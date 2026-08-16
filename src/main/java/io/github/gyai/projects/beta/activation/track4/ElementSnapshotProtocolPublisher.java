package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.network.beta.BetaCapabilityId;
import io.github.gyai.projects.network.beta.BetaCapabilitySnapshot;
import io.github.gyai.projects.network.beta.BetaChannels;
import io.github.gyai.projects.network.beta.BetaMessageEnvelope;
import io.github.gyai.projects.network.beta.BetaMessageKind;
import io.github.gyai.projects.network.beta.BetaProtocolCodec;
import io.github.gyai.projects.network.beta.BetaProtocolVersion;
import io.github.gyai.projects.network.beta.ElementDisplaySnapshotCodec;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

/** Lifecycle-owned publisher for newer, negotiated projects:elements snapshots. */
public final class ElementSnapshotProtocolPublisher implements AutoCloseable {
    private static final long PERIOD_MILLIS = 100L;
    private final ElementSnapshotProtocolAdapter adapter;
    private final BetaStateTransport transport;
    private final ElementSnapshotProtocolAdapter.CapabilityPort capabilities;
    private final Clock clock;
    private final BetaProtocolCodec protocolCodec = new BetaProtocolCodec();
    private final ElementDisplaySnapshotCodec payloadCodec = new ElementDisplaySnapshotCodec();
    private BetaStateTransport.Cancellable task;
    private boolean running;
    private long publishRunCount;
    private long viewerExaminedCount;
    private long visibleTargetCount;
    private long noVisibleTargetCount;
    private long capabilityBlockedCount;
    private long snapshotMissingCount;
    private long snapshotExpiredCount;
    private long revisionDeduplicatedCount;
    private long statePacketAttemptCount;
    private long statePacketSentCount;
    private long statePacketFailureCount;
    private String lastElementResult = "none";
    private OptionalInt lastTargetRuntimeId = OptionalInt.empty();
    private OptionalLong lastStateRevision = OptionalLong.empty();
    private OptionalInt lastFireStacks = OptionalInt.empty();

    public ElementSnapshotProtocolPublisher(
            ElementSnapshotProtocolAdapter adapter,
            BetaStateTransport transport,
            ElementSnapshotProtocolAdapter.CapabilityPort capabilities,
            Clock clock
    ) {
        this.adapter = java.util.Objects.requireNonNull(adapter);
        this.transport = java.util.Objects.requireNonNull(transport);
        this.capabilities = java.util.Objects.requireNonNull(capabilities);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public synchronized void start() {
        if (running) return;
        task = java.util.Objects.requireNonNull(
                transport.schedule(this::publishOnce, PERIOD_MILLIS));
        running = true;
    }

    public void publishOnce() {
        List<UUID> viewers;
        synchronized (this) {
            if (!running) return;
            publishRunCount = increment(publishRunCount);
        }
        try {
            viewers = transport.viewers();
        } catch (RuntimeException failure) {
            recordResult("viewer-enumeration-failed", null);
            return;
        }
        if (viewers == null) return;
        adapter.retainViewers(viewers);
        int examined = 0;
        for (UUID viewer : viewers) {
            if (viewer == null || examined++ >= ElementSnapshotProtocolAdapter.MAXIMUM_VIEWERS) break;
            synchronized (this) { viewerExaminedCount = increment(viewerExaminedCount); }
            UUID target;
            try {
                target = transport.visibleTarget(viewer);
            } catch (RuntimeException failure) {
                recordResult("visibility-failed", null);
                continue;
            }
            if (target == null) {
                synchronized (this) { noVisibleTargetCount = increment(noVisibleTargetCount); }
                recordResult("no-visible-target", null);
                adapter.clearViewer(viewer);
                continue;
            }
            synchronized (this) { visibleTargetCount = increment(visibleTargetCount); }
            ElementSnapshotProtocolAdapter.Decision decision = null;
            boolean packetAttempted = false;
            try {
                decision = adapter.decide(viewer, target, clock.millis());
                recordDecision(decision);
                switch (decision.status()) {
                    case CAPABILITY_UNAVAILABLE -> incrementCapabilityBlocked();
                    case SNAPSHOT_MISSING -> incrementSnapshotMissing();
                    case SNAPSHOT_EXPIRED -> incrementSnapshotExpired();
                    case REVISION_NOT_ADVANCED -> incrementRevisionDeduplicated();
                    default -> { }
                }
                if (decision.status() != ElementSnapshotProtocolAdapter.DecisionStatus.READY) {
                    continue;
                }
                BetaCapabilitySnapshot session = capabilities.snapshot(viewer);
                if (session == null || !session.supports(BetaCapabilityId.ELEMENTS, 1)
                        || session.sessionId() == null) {
                    incrementCapabilityBlocked();
                    recordResult("capability-blocked", decision);
                    continue;
                }
                byte[] packet = protocolCodec.encode(new BetaMessageEnvelope(
                        BetaProtocolVersion.CURRENT, BetaMessageKind.STATE,
                        BetaCapabilityId.ELEMENTS, 1, session.sessionId(),
                        payloadCodec.encode(decision.snapshot().orElseThrow())));
                packetAttempted = true;
                synchronized (this) { statePacketAttemptCount = increment(statePacketAttemptCount); }
                recordResult("packet-generated", decision);
                BetaStateTransport.SendResult result = transport.sendResult(
                        viewer, BetaChannels.STATE, packet);
                if (result == BetaStateTransport.SendResult.SENT) {
                    synchronized (this) { statePacketSentCount = increment(statePacketSentCount); }
                    recordResult("sent", decision);
                } else {
                    synchronized (this) { statePacketFailureCount = increment(statePacketFailureCount); }
                    recordResult("send-failed", decision);
                }
            } catch (RuntimeException failure) {
                if (packetAttempted) synchronized (this) {
                    statePacketFailureCount = increment(statePacketFailureCount);
                }
                recordResult(packetAttempted ? "send-failed" : "publisher-failed", decision);
            }
        }
    }

    public synchronized boolean running() { return running; }

    public void clearViewer(UUID playerId) {
        if (playerId != null) adapter.clearViewer(playerId);
    }

    public void clearViewerState() { adapter.clear(); }

    public synchronized Diagnostics diagnostics() {
        return new Diagnostics(publishRunCount, viewerExaminedCount, visibleTargetCount,
                noVisibleTargetCount, capabilityBlockedCount, snapshotMissingCount,
                snapshotExpiredCount, revisionDeduplicatedCount, statePacketAttemptCount,
                statePacketSentCount, statePacketFailureCount, lastElementResult,
                lastTargetRuntimeId, lastStateRevision, lastFireStacks);
    }

    public List<String> diagnosticLines() {
        Diagnostics value = diagnostics();
        return List.of(
                "elementState sent=" + value.statePacketSentCount()
                        + " failed=" + value.statePacketFailureCount()
                        + " dedupe=" + value.revisionDeduplicatedCount(),
                "elementState visible=" + value.visibleTargetCount()
                        + " noTarget=" + value.noVisibleTargetCount()
                        + " missing=" + value.snapshotMissingCount()
                        + " expired=" + value.snapshotExpiredCount()
                        + " blocked=" + value.capabilityBlockedCount(),
                "lastElement result=" + value.lastElementResult()
                        + " targetRuntimeId=" + format(value.lastTargetRuntimeId())
                        + " revision=" + format(value.lastStateRevision())
                        + " fireStacks=" + format(value.lastFireStacks()));
    }

    private synchronized void recordDecision(ElementSnapshotProtocolAdapter.Decision decision) {
        lastElementResult = decision.status().name().toLowerCase(Locale.ROOT);
        lastTargetRuntimeId = decision.targetRuntimeId();
        lastStateRevision = decision.stateRevision();
        lastFireStacks = decision.fireStacks();
    }

    private synchronized void recordResult(String result,
                                            ElementSnapshotProtocolAdapter.Decision decision) {
        lastElementResult = result;
        if (decision != null) {
            lastTargetRuntimeId = decision.targetRuntimeId();
            lastStateRevision = decision.stateRevision();
            lastFireStacks = decision.fireStacks();
        } else {
            lastTargetRuntimeId = OptionalInt.empty();
            lastStateRevision = OptionalLong.empty();
            lastFireStacks = OptionalInt.empty();
        }
    }

    private synchronized void incrementCapabilityBlocked() {
        capabilityBlockedCount = increment(capabilityBlockedCount);
    }

    private synchronized void incrementSnapshotMissing() {
        snapshotMissingCount = increment(snapshotMissingCount);
    }

    private synchronized void incrementSnapshotExpired() {
        snapshotExpiredCount = increment(snapshotExpiredCount);
    }

    private synchronized void incrementRevisionDeduplicated() {
        revisionDeduplicatedCount = increment(revisionDeduplicatedCount);
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? value : value + 1L;
    }

    private static String format(OptionalInt value) {
        return value.isPresent() ? Integer.toString(value.getAsInt()) : "none";
    }

    private static String format(OptionalLong value) {
        return value.isPresent() ? Long.toString(value.getAsLong()) : "none";
    }

    public record Diagnostics(
            long publishRunCount,
            long viewerExaminedCount,
            long visibleTargetCount,
            long noVisibleTargetCount,
            long capabilityBlockedCount,
            long snapshotMissingCount,
            long snapshotExpiredCount,
            long revisionDeduplicatedCount,
            long statePacketAttemptCount,
            long statePacketSentCount,
            long statePacketFailureCount,
            String lastElementResult,
            OptionalInt lastTargetRuntimeId,
            OptionalLong lastStateRevision,
            OptionalInt lastFireStacks
    ) {
        public Diagnostics {
            if (lastElementResult == null || lastTargetRuntimeId == null
                    || lastStateRevision == null || lastFireStacks == null) {
                throw new IllegalArgumentException("diagnostic fields are required");
            }
        }
    }

    @Override public synchronized void close() {
        if (!running && task == null) { adapter.clear(); return; }
        running = false;
        if (task != null) try { task.cancel(); } catch (RuntimeException ignored) { }
        task = null;
        adapter.clear();
    }
}
