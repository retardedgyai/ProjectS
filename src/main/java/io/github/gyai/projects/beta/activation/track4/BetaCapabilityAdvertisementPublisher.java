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
    private final ClientBetaProtocolRuntime protocol;
    private final BetaCapabilityAdvertisementTransport transport;
    private final BetaCapabilityLifecycleRegistrar lifecycle;
    private final BetaCapabilityPolicy capabilityPolicy;
    private final Clock clock;
    private final Supplier<BetaRuntimeModuleState> protocolModuleState;
    private final BetaProtocolCodec codec = new BetaProtocolCodec();
    private final LinkedHashMap<UUID, PendingAdvertisement> pending =
            new LinkedHashMap<>(16, .75f, true);
    private BetaActivationPolicy activationPolicy = BetaActivationPolicy.defaults();
    private BetaCapabilityAdvertisementTransport.Cancellable initialCheck;
    private boolean clientBetaUiEnabled;
    private boolean running;
    private long sentCount;
    private long resendCount;
    private long ackAcceptedCount;
    private long ackRejectedCount;
    private String lastHandshakeResult = "none";

    public BetaCapabilityAdvertisementPublisher(
            ClientBetaProtocolRuntime protocol,
            BetaCapabilityAdvertisementTransport transport,
            BetaCapabilityLifecycleRegistrar lifecycle,
            BetaCapabilityPolicy capabilityPolicy,
            Clock clock,
            Supplier<BetaRuntimeModuleState> protocolModuleState
    ) {
        this.protocol = java.util.Objects.requireNonNull(protocol, "protocol");
        this.transport = java.util.Objects.requireNonNull(transport, "transport");
        this.lifecycle = java.util.Objects.requireNonNull(lifecycle, "lifecycle");
        this.capabilityPolicy = java.util.Objects.requireNonNull(
                capabilityPolicy, "capabilityPolicy");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.protocolModuleState = java.util.Objects.requireNonNull(
                protocolModuleState, "protocolModuleState");
    }

    public synchronized void start(
            BetaActivationPolicy policy,
            boolean featureEnabled
    ) {
        if (running) return;
        activationPolicy = java.util.Objects.requireNonNull(policy, "policy");
        clientBetaUiEnabled = featureEnabled;
        try {
            lifecycle.register(this);
            running = true;
            initialCheck = java.util.Objects.requireNonNull(
                    transport.scheduleMainThread(this::advertiseExistingPlayers));
        } catch (RuntimeException failure) {
            running = false;
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
        if (playerId != null) protocol.reconnect(playerId);
    }

    @Override
    public void onChannelRegistered(UUID playerId, String channel) {
        if (BetaChannels.CAPABILITIES.equals(channel)) {
            advertiseOrResend(playerId);
        }
    }

    @Override
    public void onQuit(UUID playerId) {
        if (playerId != null) protocol.disconnect(playerId);
    }

    @Override
    public void onKick(UUID playerId) {
        if (playerId != null) protocol.disconnect(playerId);
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
            expirePending();
            PendingAdvertisement existing = pending.get(playerId);
            if (existing != null) {
                if (existing.acknowledged()) return;
                packet = existing.packet().clone();
                resend = true;
            } else {
                BetaCapabilityAdvertisement advertisement = protocol
                        .advertise(playerId, clientBetaUiEnabled)
                        .orElse(null);
                if (advertisement == null) return;
                packet = codec.encode(advertisement);
                remember(playerId, new PendingAdvertisement(
                        advertisement.sessionId(),
                        advertisement.advertisementRevision(),
                        packet.clone(),
                        clock.instant().plus(capabilityPolicy.sessionTtl()),
                        false));
                resend = false;
            }
        }
        try {
            transport.send(playerId, BetaChannels.CAPABILITIES, packet);
            synchronized (this) {
                if (resend) resendCount = increment(resendCount);
                else sentCount = increment(sentCount);
                lastHandshakeResult = resend ? "advertisement-resent" : "advertisement-sent";
            }
        } catch (RuntimeException failure) {
            recordResult("advertisement-send-failed");
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
        expirePending();
        return pending.size();
    }

    public synchronized Diagnostics diagnostics() {
        expirePending();
        return new Diagnostics(sentCount, resendCount, ackAcceptedCount,
                ackRejectedCount, protocol.activeSessionCount(),
                lastHandshakeResult, pending.size());
    }

    public List<String> diagnosticLines() {
        Diagnostics value = diagnostics();
        return List.of(
                "advertisementSent=" + value.advertisementSentCount(),
                "advertisementResend=" + value.advertisementResendCount(),
                "ackAccepted=" + value.ackAcceptedCount(),
                "ackRejected=" + value.ackRejectedCount(),
                "activeCapabilitySessions=" + value.activeCapabilitySessionCount(),
                "lastHandshakeResult=" + value.lastHandshakeResult());
    }

    public synchronized boolean running() {
        return running;
    }

    @Override
    public synchronized void close() {
        running = false;
        if (initialCheck != null) {
            try {
                initialCheck.cancel();
            } catch (RuntimeException ignored) {
                // Continue listener and state cleanup.
            }
            initialCheck = null;
        }
        try {
            lifecycle.unregister();
        } finally {
            pending.clear();
            clientBetaUiEnabled = false;
            activationPolicy = BetaActivationPolicy.defaults();
        }
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

    private synchronized void expirePending() {
        Instant now = clock.instant();
        ArrayList<UUID> expired = new ArrayList<>();
        pending.forEach((playerId, value) -> {
            if (now.isAfter(value.expiresAt())) expired.add(playerId);
        });
        for (UUID playerId : expired) {
            pending.remove(playerId);
            protocol.disconnect(playerId);
        }
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
            boolean acknowledged
    ) {
        private PendingAdvertisement acknowledgedCopy() {
            return new PendingAdvertisement(
                    sessionId, revision, packet.clone(), expiresAt, true);
        }
    }

    public record Diagnostics(
            long advertisementSentCount,
            long advertisementResendCount,
            long ackAcceptedCount,
            long ackRejectedCount,
            int activeCapabilitySessionCount,
            String lastHandshakeResult,
            int pendingAdvertisementCount
    ) {
    }
}
