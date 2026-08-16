package io.github.gyai.projects.beta.activation;

import io.github.gyai.projects.beta.activation.track2.ElementRuntimeSnapshotPort;
import io.github.gyai.projects.beta.activation.track4.BetaStateTransport;
import io.github.gyai.projects.beta.activation.track4.ElementSnapshotProtocolAdapter;
import io.github.gyai.projects.beta.activation.track4.ElementSnapshotProtocolPublisher;
import io.github.gyai.projects.combat.element.ice.IceElementEngine;
import io.github.gyai.projects.network.beta.BetaCapabilityId;
import io.github.gyai.projects.network.beta.BetaCapabilitySnapshot;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Executable coverage for element delivery diagnostics and purity. */
public final class ElementSnapshotProtocolDiagnosticsTest {
    private static final long NOW = 1_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
    private static final UUID VIEWER = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    public static void main(String[] args) {
        noViewerIsObservable();
        noVisibleTargetIsObservable();
        capabilityBlockIsObservable();
        snapshotMissingAndExpiredAreObservable();
        sendSuccessFailureAndDeduplicationAreObservable();
        diagnosticsArePureReads();
        defaultOffDoesNotPublish();
    }

    private static void noViewerIsObservable() {
        Fixture fixture = fixture(List.of(), null, acknowledged(), BetaStateTransport.SendResult.SENT);
        fixture.publisher.start();
        fixture.publisher.publishOnce();
        assert fixture.publisher.diagnostics().publishRunCount() == 1;
        assert fixture.publisher.diagnostics().viewerExaminedCount() == 0;
        fixture.publisher.close();
    }

    private static void noVisibleTargetIsObservable() {
        Fixture fixture = fixture(List.of(VIEWER), null, acknowledged(), BetaStateTransport.SendResult.SENT);
        fixture.publisher.start();
        fixture.publisher.publishOnce();
        assert fixture.publisher.diagnostics().noVisibleTargetCount() == 1;
        assert fixture.publisher.diagnostics().statePacketSentCount() == 0;
        fixture.publisher.close();
    }

    private static void capabilityBlockIsObservable() {
        Fixture fixture = fixture(List.of(VIEWER), TARGET, BetaCapabilitySnapshot.oldClient(VIEWER),
                BetaStateTransport.SendResult.SENT);
        fixture.publisher.start();
        fixture.publisher.publishOnce();
        assert fixture.publisher.diagnostics().capabilityBlockedCount() == 1;
        fixture.publisher.close();
    }

    private static void snapshotMissingAndExpiredAreObservable() {
        Fixture missing = fixture(List.of(VIEWER), TARGET, acknowledged(),
                BetaStateTransport.SendResult.SENT);
        missing.snapshots.value = null;
        missing.publisher.start();
        missing.publisher.publishOnce();
        assert missing.publisher.diagnostics().snapshotMissingCount() == 1;
        missing.publisher.close();

        Fixture expired = fixture(List.of(VIEWER), TARGET, acknowledged(),
                BetaStateTransport.SendResult.SENT);
        expired.snapshots.value = snapshot(1, NOW);
        expired.publisher.start();
        expired.publisher.publishOnce();
        assert expired.publisher.diagnostics().snapshotExpiredCount() == 1;
        expired.publisher.close();
    }

    private static void sendSuccessFailureAndDeduplicationAreObservable() {
        Fixture success = fixture(List.of(VIEWER), TARGET, acknowledged(),
                BetaStateTransport.SendResult.SENT);
        success.publisher.start();
        success.publisher.publishOnce();
        success.publisher.publishOnce();
        assert success.publisher.diagnostics().statePacketAttemptCount() == 1;
        assert success.publisher.diagnostics().statePacketSentCount() == 1;
        assert success.publisher.diagnostics().revisionDeduplicatedCount() == 1;
        assert success.publisher.diagnostics().lastTargetRuntimeId().orElseThrow() == 77;
        assert success.publisher.diagnostics().lastStateRevision().orElseThrow() == 1;
        assert success.publisher.diagnostics().lastFireStacks().orElseThrow() == 3;
        success.publisher.close();

        Fixture failure = fixture(List.of(VIEWER), TARGET, acknowledged(),
                BetaStateTransport.SendResult.FAILED);
        failure.publisher.start();
        failure.publisher.publishOnce();
        assert failure.publisher.diagnostics().statePacketAttemptCount() == 1;
        assert failure.publisher.diagnostics().statePacketFailureCount() == 1;
        assert failure.publisher.diagnostics().statePacketSentCount() == 0;
        failure.publisher.close();
    }

    private static void diagnosticsArePureReads() {
        Fixture fixture = fixture(List.of(VIEWER), TARGET, acknowledged(),
                BetaStateTransport.SendResult.SENT);
        fixture.publisher.start();
        fixture.publisher.publishOnce();
        ElementSnapshotProtocolPublisher.Diagnostics before = fixture.publisher.diagnostics();
        List<String> lines = fixture.publisher.diagnosticLines();
        for (int i = 0; i < 100; i++) {
            assert fixture.publisher.diagnostics().equals(before);
            assert fixture.publisher.diagnosticLines().equals(lines);
        }
        assert fixture.publisher.diagnostics().equals(before);
        fixture.publisher.close();
    }

    private static void defaultOffDoesNotPublish() {
        Fixture fixture = fixture(List.of(VIEWER), TARGET, acknowledged(),
                BetaStateTransport.SendResult.SENT);
        fixture.publisher.publishOnce();
        ElementSnapshotProtocolPublisher.Diagnostics value = fixture.publisher.diagnostics();
        assert value.publishRunCount() == 0;
        assert value.statePacketAttemptCount() == 0;
        assert value.statePacketSentCount() == 0;
        assert value.statePacketFailureCount() == 0;
        assert value.capabilityBlockedCount() == 0;
        assert value.snapshotMissingCount() == 0;
        assert value.snapshotExpiredCount() == 0;
        assert value.revisionDeduplicatedCount() == 0;
    }

    private static Fixture fixture(List<UUID> viewers, UUID target,
                                   BetaCapabilitySnapshot capability,
                                   BetaStateTransport.SendResult result) {
        MutableSnapshots snapshots = new MutableSnapshots(target, snapshot(1, NOW + 10_000));
        RecordingTransport transport = new RecordingTransport(viewers, target, result);
        ElementSnapshotProtocolAdapter adapter = new ElementSnapshotProtocolAdapter(
                snapshots, () -> BetaRuntimeModuleState.RUNNING,
                () -> BetaRuntimeModuleState.RUNNING, ignored -> capability,
                ignored -> true);
        return new Fixture(snapshots, new ElementSnapshotProtocolPublisher(
                adapter, transport, ignored -> capability, CLOCK));
    }

    private static BetaCapabilitySnapshot acknowledged() {
        return new BetaCapabilitySnapshot(VIEWER, UUID.randomUUID(), 1,
                Map.of(BetaCapabilityId.ELEMENTS, 1), Instant.ofEpochMilli(NOW + 60_000), false);
    }

    private static ElementRuntimeSnapshotPort.TargetSnapshot snapshot(long revision, long expiresAt) {
        return new ElementRuntimeSnapshotPort.TargetSnapshot(TARGET, 77, revision,
                3, 25, 100, .25, true, 500, 0, expiresAt, 0,
                IceElementEngine.Stage.NONE, false, 0, NOW, 1);
    }

    private record Fixture(MutableSnapshots snapshots,
                           ElementSnapshotProtocolPublisher publisher) { }

    private static final class MutableSnapshots implements ElementRuntimeSnapshotPort {
        private final UUID target;
        private TargetSnapshot value;

        private MutableSnapshots(UUID target, TargetSnapshot value) {
            this.target = target;
            this.value = value;
        }

        @Override public Optional<TargetSnapshot> target(UUID targetId) {
            return target != null && target.equals(targetId) && value != null
                    ? Optional.of(value) : Optional.empty();
        }

        @Override public Map<UUID, TargetSnapshot> targets() {
            return target == null || value == null ? Map.of() : Map.of(target, value);
        }

        @Override public io.github.gyai.projects.beta.activation.track2.StagingElementProfile
        playerProfile(UUID playerId) { return io.github.gyai.projects.beta.activation.track2.StagingElementProfile.FIRE; }
    }

    private static final class RecordingTransport implements BetaStateTransport {
        private final List<UUID> viewers;
        private final UUID target;
        private final SendResult result;

        private RecordingTransport(List<UUID> viewers, UUID target, SendResult result) {
            this.viewers = viewers;
            this.target = target;
            this.result = result;
        }

        @Override public List<UUID> viewers() { return viewers; }
        @Override public UUID visibleTarget(UUID viewerId) { return target; }
        @Override public void send(UUID viewerId, String channel, byte[] packet) { }
        @Override public SendResult sendResult(UUID viewerId, String channel, byte[] packet) { return result; }
        @Override public Cancellable schedule(Runnable task, long periodMillis) {
            return new Cancellable() {
                private boolean cancelled;
                @Override public void cancel() { cancelled = true; }
                @Override public boolean cancelled() { return cancelled; }
            };
        }
    }
}
