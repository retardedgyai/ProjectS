package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.BetaActivationAudience;
import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.network.beta.BetaCapabilityAdvertisement;
import io.github.gyai.projects.network.beta.BetaCapabilityPolicy;
import io.github.gyai.projects.network.beta.BetaCapabilitySessionService;
import io.github.gyai.projects.network.beta.BetaChannels;
import io.github.gyai.projects.network.beta.BetaProtocolCodec;
import io.github.gyai.projects.network.beta.BetaProtocolDecodeResult;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Bounded per-connection advertisement lifecycle. No Bukkit object is retained. */
public final class BetaCapabilityAdvertisementPublisher
        implements BetaCapabilityLifecycleListener, AutoCloseable {
    public static final long MAINTENANCE_PERIOD_MILLIS = 5_000L;
    private final ClientBetaProtocolRuntime protocol;
    private final BetaCapabilityAdvertisementTransport transport;
    private final BetaCapabilityLifecycleRegistrar lifecycle;
    private final BetaCapabilityPolicy capabilityPolicy;
    private final Clock clock;
    private final Supplier<BetaRuntimeModuleState> protocolModuleState;
    private final BetaStagingPlayerLifecyclePort playerLifecycle;
    private final BetaProtocolCodec codec = new BetaProtocolCodec();
    private final LinkedHashMap<UUID, PendingAdvertisement> pending =
            new LinkedHashMap<>(16, .75f, true);
    private BetaActivationPolicy activationPolicy = BetaActivationPolicy.defaults();
    private BetaCapabilityAdvertisementTransport.Cancellable initialCheck;
    private BetaCapabilityAdvertisementTransport.Cancellable maintenanceTask;
    private boolean clientBetaUiEnabled;
    private boolean running;
    private boolean closed;
    private long sentCount;
    private long resendCount;
    private long ackAcceptedCount;
    private long ackRejectedCount;
    private long sessionRenewalCount;
    private long expiredSessionCount;
    private long maintenanceRunCount;
    private long maintenanceFailureCount;
    private String lastHandshakeResult = "none";

    public BetaCapabilityAdvertisementPublisher(
            ClientBetaProtocolRuntime protocol,
            BetaCapabilityAdvertisementTransport transport,
            BetaCapabilityLifecycleRegistrar lifecycle,
            BetaCapabilityPolicy capabilityPolicy,
            Clock clock,
            Supplier<BetaRuntimeModuleState> protocolModuleState
    ) {
        this(protocol, transport, lifecycle, capabilityPolicy, clock,
                protocolModuleState, defaultPlayerLifecycle(protocol));
    }

    public BetaCapabilityAdvertisementPublisher(
            ClientBetaProtocolRuntime protocol,
            BetaCapabilityAdvertisementTransport transport,
            BetaCapabilityLifecycleRegistrar lifecycle,
            BetaCapabilityPolicy capabilityPolicy,
            Clock clock,
            Supplier<BetaRuntimeModuleState> protocolModuleState,
            BetaStagingPlayerLifecyclePort playerLifecycle
    ) {
        this.protocol = java.util.Objects.requireNonNull(protocol, "protocol");
        this.transport = java.util.Objects.requireNonNull(transport, "transport");
        this.lifecycle = java.util.Objects.requireNonNull(lifecycle, "lifecycle");
        this.capabilityPolicy = java.util.Objects.requireNonNull(
                capabilityPolicy, "capabilityPolicy");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.protocolModuleState = java.util.Objects.requireNonNull(
                protocolModuleState, "protocolModuleState");
        this.playerLifecycle = java.util.Objects.requireNonNull(
                playerLifecycle, "playerLifecycle");
    }

    public synchronized void start(
            BetaActivationPolicy policy,
            boolean featureEnabled
    ) {
        if (running) return;
        if (closed) throw new IllegalStateException("advertisement publisher is closed");
        activationPolicy = java.util.Objects.requireNonNull(policy, "policy");
        clientBetaUiEnabled = featureEnabled;
        try {
            lifecycle.register(this);
            running = true;
            initialCheck = java.util.Objects.requireNonNull(
                    transport.scheduleMainThread(this::advertiseExistingPlayers));
            maintenanceTask = java.util.Objects.requireNonNull(
                    transport.scheduleRepeating(
                            this::runMaintenance, MAINTENANCE_PERIOD_MILLIS));
        } catch (RuntimeException failure) {
            running = false;
            cancel(initialCheck);
            initialCheck = null;
            cancel(maintenanceTask);
            maintenanceTask = null;
            try {
                lifecycle.unregister();
            } catch (RuntimeException ignored) {
                // Start failure is reported by the module; rollback remains best effort.
            }
            throw failure;
        }
    }

    @Override
    public void onJoin(UUID playerId) {
        if (playerId != null) playerLifecycle.connectionStarted(playerId);
    }

    @Override
    public void onChannelRegistered(UUID playerId, String channel) {
        if (BetaChannels.CAPABILITIES.equals(channel)) {
            advertiseOrResend(playerId);
        }
    }

    @Override
    public void onQuit(UUID playerId) {
        if (playerId != null) playerLifecycle.connectionEnded(playerId);
    }

    @Override
    public void onKick(UUID playerId) {
        if (playerId != null) playerLifecycle.connectionEnded(playerId);
    }

    public void advertiseExistingPlayers() {
        List<UUID> players;
        synchronized (this) {
            initialCheck = null;
            if (!running) return;
        }
        try {
            players = transport.onlinePlayers();
        } catch (RuntimeException failure) {
            recordResult("existing-player-check-failed");
            return;
        }
        if (players == null) return;
        int examined = 0;
        for (UUID playerId : players) {
            if (playerId == null
                    || examined++ >= capabilityPolicy.maximumSessions()) break;
            advertiseOrResend(playerId);
        }
    }

    public void advertiseOrResend(UUID playerId) {
        if (playerId == null || !admitted(playerId)) return;
        byte[] packet;
        boolean resend;
        synchronized (this) {
            if (!running) return;
            PendingAdvertisement existing = pending.get(playerId);
            if (existing != null) {
                if (existing.renewalDue()) return;
                if (clock.instant().isAfter(existing.expiresAt())) return;
                if (existing.acknowledged()) return;
                packet = existing.packet().clone();
                resend = true;
            } else {
                packet = createAdvertisement(playerId, false);
                if (packet == null) return;
                resend = false;
            }
        }
        sendAdvertisement(playerId, packet, resend, false);
    }

    public void runMaintenance() {
        ArrayList<RenewalCandidate> candidates = new ArrayList<>();
        try {
            synchronized (this) {
                if (!running) return;
                maintenanceRunCount = increment(maintenanceRunCount);
                Instant now = clock.instant();
                for (var entry : pending.entrySet()) {
                    if (candidates.size() >= capabilityPolicy.maximumSessions()) break;
                    PendingAdvertisement value = entry.getValue();
                    if (value.renewalDue()) {
                        candidates.add(new RenewalCandidate(
                                entry.getKey(), value, false));
                    } else if (now.isAfter(value.expiresAt())) {
                        candidates.add(new RenewalCandidate(
                                entry.getKey(), value.renewalDueCopy(), true));
                    }
                }
                for (RenewalCandidate candidate : candidates) {
                    if (!candidate.invalidateOldSession()) continue;
                    pending.put(candidate.playerId(), candidate.pending());
                    expiredSessionCount = increment(expiredSessionCount);
                }
            }
        } catch (RuntimeException failure) {
            maintenanceFailed("maintenance-scan-failed");
            return;
        }
        for (RenewalCandidate candidate : candidates) {
            try {
                renew(candidate);
            } catch (RuntimeException failure) {
                maintenanceFailed("maintenance-renewal-failed");
            }
        }
    }

    public BetaCapabilitySessionService.AcknowledgeStatus onAcknowledgement(
            UUID playerId,
            byte[] packet
    ) {
        BetaProtocolDecodeResult<io.github.gyai.projects.network.beta.BetaCapabilityAcknowledgement>
                decoded = codec.decodeAcknowledgement(packet);
        if (decoded.status() != BetaProtocolDecodeResult.Status.SUCCESS) {
            synchronized (this) {
                ackRejectedCount = increment(ackRejectedCount);
                lastHandshakeResult = bounded("ack-decode-" + decoded.status());
            }
            return BetaCapabilitySessionService.AcknowledgeStatus.UNKNOWN_SESSION;
        }
        BetaCapabilitySessionService.AcknowledgeStatus status =
                protocol.acknowledge(playerId, decoded.value());
        synchronized (this) {
            if (status == BetaCapabilitySessionService.AcknowledgeStatus.ACCEPTED) {
                ackAcceptedCount = increment(ackAcceptedCount);
                PendingAdvertisement current = pending.get(playerId);
                if (current != null
                        && current.sessionId().equals(decoded.value().sessionId())
                        && current.revision()
                        == decoded.value().advertisementRevision()) {
                    pending.put(playerId, current.acknowledgedCopy());
                }
            } else {
                ackRejectedCount = increment(ackRejectedCount);
            }
            lastHandshakeResult = bounded("ack-" + status);
        }
        return status;
    }

    public synchronized void clearPlayerState(UUID playerId) {
        if (playerId != null) pending.remove(playerId);
    }

    public synchronized void clearAllState() {
        pending.clear();
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    public synchronized Diagnostics diagnostics() {
        return new Diagnostics(sentCount, resendCount, ackAcceptedCount,
                ackRejectedCount, protocol.retainedSessionCount(),
                sessionRenewalCount, expiredSessionCount,
                maintenanceRunCount, maintenanceFailureCount,
                lastHandshakeResult, pending.size());
    }

    public List<String> diagnosticLines() {
        Diagnostics value = diagnostics();
        return List.of(
                "advertisementSent=" + value.advertisementSentCount()
                        + " advertisementResend=" + value.advertisementResendCount(),
                "ackAccepted=" + value.ackAcceptedCount()
                        + " ackRejected=" + value.ackRejectedCount(),
                "activeCapabilitySessions=" + value.activeCapabilitySessionCount(),
                "sessionRenewal=" + value.sessionRenewalCount()
                        + " expiredSession=" + value.expiredSessionCount(),
                "maintenanceRun=" + value.maintenanceRunCount()
                        + " maintenanceFailure=" + value.maintenanceFailureCount(),
                "lastHandshakeResult=" + value.lastHandshakeResult());
    }

    public synchronized boolean running() {
        return running;
    }

    public synchronized boolean maintenanceRunning() {
        return maintenanceTask != null && !maintenanceTask.cancelled();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        running = false;
        if (initialCheck != null) {
            cancel(initialCheck);
            initialCheck = null;
        }
        cancel(maintenanceTask);
        maintenanceTask = null;
        RuntimeException first = null;
        try {
            lifecycle.unregister();
        } catch (RuntimeException failure) {
            first = failure;
        }
        try {
            playerLifecycle.clearAll();
        } catch (RuntimeException failure) {
            if (first == null) first = failure;
        } finally {
            pending.clear();
            clientBetaUiEnabled = false;
            activationPolicy = BetaActivationPolicy.defaults();
        }
        if (first != null) throw first;
    }

    private boolean admitted(UUID playerId) {
        BetaActivationPolicy policy;
        boolean enabled;
        synchronized (this) {
            if (!running) return false;
            policy = activationPolicy;
            enabled = clientBetaUiEnabled;
        }
        if (!enabled || protocolModuleState.get() != BetaRuntimeModuleState.RUNNING
                || !protocol.running() || protocol.closed()) return false;
        if (policy.audience() == BetaActivationAudience.OFF) return false;
        if (policy.audience() == BetaActivationAudience.ALLOWLIST
                && !policy.allowlistedPlayerUuids().contains(playerId)) return false;
        try {
            return policy.allowsWorld(transport.worldName(playerId))
                    && transport.listeningChannels(playerId)
                    .contains(BetaChannels.CAPABILITIES);
        } catch (RuntimeException failure) {
            recordResult("admission-check-failed");
            return false;
        }
    }

    private void renew(RenewalCandidate candidate) {
        UUID playerId = candidate.playerId();
        if (candidate.invalidateOldSession()) {
            try {
                protocol.disconnect(playerId);
            } catch (RuntimeException failure) {
                maintenanceFailed("renewal-disconnect-failed");
                return;
            }
            synchronized (this) {
                if (!running) return;
                pending.putIfAbsent(playerId, candidate.pending());
            }
        }
        try {
            if (!transport.online(playerId) || !admitted(playerId)) return;
        } catch (RuntimeException failure) {
            maintenanceFailed("renewal-admission-failed");
            return;
        }
        byte[] packet;
        synchronized (this) {
            PendingAdvertisement current = pending.get(playerId);
            if (!running || current == null || !current.renewalDue()) return;
            packet = createAdvertisement(playerId, true);
            if (packet == null) return;
        }
        sendAdvertisement(playerId, packet, false, true);
    }

    private byte[] createAdvertisement(UUID playerId, boolean renewal) {
        BetaCapabilityAdvertisement advertisement = protocol
                .advertise(playerId, clientBetaUiEnabled)
                .orElse(null);
        if (advertisement == null) return null;
        byte[] packet = codec.encode(advertisement);
        remember(playerId, new PendingAdvertisement(
                advertisement.sessionId(),
                advertisement.advertisementRevision(),
                packet.clone(),
                clock.instant().plus(capabilityPolicy.sessionTtl()),
                false,
                false));
        if (renewal) sessionRenewalCount = increment(sessionRenewalCount);
        return packet;
    }

    private void sendAdvertisement(
            UUID playerId,
            byte[] packet,
            boolean resend,
            boolean renewal
    ) {
        try {
            transport.send(playerId, BetaChannels.CAPABILITIES, packet);
            synchronized (this) {
                if (resend) resendCount = increment(resendCount);
                else sentCount = increment(sentCount);
                lastHandshakeResult = renewal ? "session-renewed"
                        : resend ? "advertisement-resent" : "advertisement-sent";
            }
        } catch (RuntimeException failure) {
            if (renewal) maintenanceFailed("renewal-send-failed");
            else recordResult("advertisement-send-failed");
        }
    }

    private synchronized void maintenanceFailed(String result) {
        maintenanceFailureCount = increment(maintenanceFailureCount);
        lastHandshakeResult = bounded(result);
    }

    private void remember(UUID playerId, PendingAdvertisement value) {
        pending.put(playerId, value);
        while (pending.size() > capabilityPolicy.maximumSessions()) {
            UUID eldest = pending.keySet().iterator().next();
            pending.remove(eldest);
            protocol.disconnect(eldest);
        }
    }

    private synchronized void recordResult(String value) {
        lastHandshakeResult = bounded(value);
    }

    private static void cancel(BetaCapabilityAdvertisementTransport.Cancellable value) {
        if (value == null) return;
        try {
            value.cancel();
        } catch (RuntimeException ignored) {
            // Remaining lifecycle cleanup must continue.
        }
    }

    private static BetaStagingPlayerLifecyclePort defaultPlayerLifecycle(
            ClientBetaProtocolRuntime protocol
    ) {
        return new BetaStagingPlayerLifecyclePort() {
            @Override
            public void connectionStarted(UUID playerId) {
                protocol.reconnect(playerId);
            }

            @Override
            public void connectionEnded(UUID playerId) {
                protocol.disconnect(playerId);
            }

            @Override
            public void clearAll() {
                protocol.clearAllConnectionState();
            }
        };
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? value : value + 1;
    }

    private static String bounded(String value) {
        if (value == null) return "";
        String sanitized = value.replace('\n', ' ').replace('\r', ' ');
        return sanitized.length() <= 160 ? sanitized : sanitized.substring(0, 160);
    }

    private record PendingAdvertisement(
            UUID sessionId,
            long revision,
            byte[] packet,
            Instant expiresAt,
            boolean acknowledged,
            boolean renewalDue
    ) {
        private PendingAdvertisement acknowledgedCopy() {
            return new PendingAdvertisement(
                    sessionId, revision, packet.clone(), expiresAt, true, renewalDue);
        }

        private PendingAdvertisement renewalDueCopy() {
            return new PendingAdvertisement(
                    sessionId, revision, packet.clone(), expiresAt,
                    acknowledged, true);
        }
    }

    private record RenewalCandidate(
            UUID playerId,
            PendingAdvertisement pending,
            boolean invalidateOldSession
    ) { }

    public record Diagnostics(
            long advertisementSentCount,
            long advertisementResendCount,
            long ackAcceptedCount,
            long ackRejectedCount,
            int activeCapabilitySessionCount,
            long sessionRenewalCount,
            long expiredSessionCount,
            long maintenanceRunCount,
            long maintenanceFailureCount,
            String lastHandshakeResult,
            int pendingAdvertisementCount
    ) {
    }
}
