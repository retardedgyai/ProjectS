package io.github.gyai.projects.beta.activation;

import io.github.gyai.projects.beta.activation.track2.ElementRuntimeSnapshotPort;
import io.github.gyai.projects.beta.activation.track2.StagingElementProfile;
import io.github.gyai.projects.beta.activation.track4.*;
import io.github.gyai.projects.combat.element.ice.IceElementEngine;
import io.github.gyai.projects.network.beta.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BetaActivationWave1ProtocolIntegrationTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC);

    public static void main(String[] args) {
        elementsOnlyProducerAdvertisesAndPublishesExactlyOnce();
        stoppedAndUnnegotiatedPathsSendNothing();
        protocolModuleHasNoProducerHardDependencies();
    }

    private static void elementsOnlyProducerAdvertisesAndPublishesExactlyOnce() {
        UUID player = UUID.randomUUID(), target = UUID.randomUUID();
        StateModule elements = new StateModule(BetaRuntimeModuleId.COMBAT_ELEMENTS,
                BetaRuntimeModuleState.RUNNING);
        StateModule party = new StateModule(BetaRuntimeModuleId.PARTY_QUEST_REWARD,
                BetaRuntimeModuleState.DISABLED);
        StateModule mob = new StateModule(BetaRuntimeModuleId.MOB_EDITOR_V2,
                BetaRuntimeModuleState.DISABLED);
        EnumMap<BetaCapabilityId, BetaRuntimeModule> producers =
                new EnumMap<>(BetaCapabilityId.class);
        producers.put(BetaCapabilityId.ELEMENTS, elements);
        producers.put(BetaCapabilityId.PARTY, party);
        producers.put(BetaCapabilityId.MOB_EDITOR_V2, mob);
        RunningCapabilityRegistry availability = new RunningCapabilityRegistry(producers);
        RecordingChannels channels = new RecordingChannels();
        BetaCapabilitySessionService sessions = new BetaCapabilitySessionService(
                BetaCapabilityPolicy.wave3Defaults(), CLOCK);
        ClientBetaProtocolRuntime protocol = new ClientBetaProtocolRuntime(
                channels, sessions, new BetaCommandRouter(new BetaRateLimiter(32, CLOCK),
                (context, command) -> BetaCommandAuthorization.Decision.allow(), 32), availability);
        protocol.start();
        BetaCapabilityAdvertisement advertisement = protocol.advertise(player, true).orElseThrow();
        assert advertisement.capabilities().equals(List.of(
                BetaCapabilityDescriptor.v1(BetaCapabilityId.ELEMENTS)));
        assert protocol.acknowledge(player, new BetaCapabilityAcknowledgement(
                BetaProtocolVersion.CURRENT, advertisement.sessionId(),
                advertisement.advertisementRevision(), advertisement.capabilities()))
                == BetaCapabilitySessionService.AcknowledgeStatus.ACCEPTED;

        MutableSnapshots snapshots = new MutableSnapshots(target, snapshot(target, 1, 0));
        RecordingTransport transport = new RecordingTransport(player, target);
        ElementSnapshotProtocolAdapter adapter = new ElementSnapshotProtocolAdapter(
                snapshots, () -> BetaRuntimeModuleState.RUNNING, elements::state,
                protocol::capabilitySnapshot, ignored -> true);
        ElementSnapshotProtocolPublisher publisher = new ElementSnapshotProtocolPublisher(
                adapter, transport, protocol::capabilitySnapshot, CLOCK);
        publisher.start();
        transport.runScheduled();
        assert transport.packets.size() == 1;
        var envelope = new BetaProtocolCodec().decodeMessage(
                transport.packets.getFirst()).value();
        assert envelope.capabilityId() == BetaCapabilityId.ELEMENTS;
        var decoded = new ElementDisplaySnapshotCodec().decode(envelope.payload());
        assert decoded.targetNetworkId() == 77;
        assert decoded.stateRevision() == 1;
        assert decoded.fireStacks() == 3;

        transport.runScheduled();
        assert transport.packets.size() == 1 : "stale revision must not send";
        snapshots.value = snapshot(target, 2, 1);
        transport.runScheduled();
        assert transport.packets.size() == 2;
        transport.runScheduled();
        assert transport.packets.size() == 2 : "duplicate pulse must not send";
        publisher.close();
        transport.runScheduled();
        assert transport.packets.size() == 2 : "stopped publisher must not send";
        protocol.close();
        assert channels.active.isEmpty();
    }

    private static void stoppedAndUnnegotiatedPathsSendNothing() {
        UUID player = UUID.randomUUID(), target = UUID.randomUUID();
        MutableSnapshots snapshots = new MutableSnapshots(target, snapshot(target, 1, 0));
        RecordingTransport transport = new RecordingTransport(player, target);
        ElementSnapshotProtocolAdapter adapter = new ElementSnapshotProtocolAdapter(
                snapshots, () -> BetaRuntimeModuleState.RUNNING,
                () -> BetaRuntimeModuleState.RUNNING,
                BetaCapabilitySnapshot::oldClient, ignored -> true);
        ElementSnapshotProtocolPublisher publisher = new ElementSnapshotProtocolPublisher(
                adapter, transport, BetaCapabilitySnapshot::oldClient, CLOCK);
        assert !publisher.running();
        publisher.publishOnce();
        assert transport.packets.isEmpty();
        publisher.start(); transport.runScheduled();
        assert transport.packets.isEmpty() : "unnegotiated capability must not send";
        publisher.close();
    }

    private static void protocolModuleHasNoProducerHardDependencies() {
        ClientBetaProtocolRuntime protocol = new ClientBetaProtocolRuntime(
                new RecordingChannels(),
                new BetaCapabilitySessionService(BetaCapabilityPolicy.wave3Defaults(), CLOCK),
                new BetaCommandRouter(new BetaRateLimiter(8, CLOCK),
                        (context, command) -> BetaCommandAuthorization.Decision.allow(), 8),
                (player, capability) -> capability == BetaCapabilityId.ELEMENTS);
        assert new ClientBetaProtocolRuntimeModule(protocol).dependencies().isEmpty();
    }

    private static ElementRuntimeSnapshotPort.TargetSnapshot snapshot(
            UUID target, long revision, long pulse
    ) {
        return new ElementRuntimeSnapshotPort.TargetSnapshot(target, 77, revision,
                3, 25, 100, .25, true, 500, pulse,
                CLOCK.millis() + 10_000, 0, IceElementEngine.Stage.NONE,
                false, 0, CLOCK.millis(), 1);
    }

    private static final class MutableSnapshots implements ElementRuntimeSnapshotPort {
        private final UUID target; private TargetSnapshot value;
        private MutableSnapshots(UUID target, TargetSnapshot value) { this.target = target; this.value = value; }
        @Override public Optional<TargetSnapshot> target(UUID id) {
            return target.equals(id) ? Optional.of(value) : Optional.empty();
        }
        @Override public Map<UUID, TargetSnapshot> targets() { return Map.of(target, value); }
        @Override public StagingElementProfile playerProfile(UUID playerId) { return StagingElementProfile.FIRE; }
    }

    private static final class RecordingTransport implements BetaStateTransport {
        private final UUID player, target; private Runnable scheduled; private boolean cancelled;
        private final List<byte[]> packets = new ArrayList<>();
        private RecordingTransport(UUID player, UUID target) { this.player = player; this.target = target; }
        @Override public List<UUID> viewers() { return List.of(player); }
        @Override public UUID visibleTarget(UUID viewerId) { return target; }
        @Override public void send(UUID viewerId, String channel, byte[] packet) {
            assert channel.equals(BetaChannels.STATE); packets.add(packet.clone());
        }
        @Override public Cancellable schedule(Runnable task, long periodMillis) {
            scheduled = task; return new Cancellable() {
                @Override public void cancel() { cancelled = true; }
                @Override public boolean cancelled() { return cancelled; }
            };
        }
        private void runScheduled() { if (!cancelled && scheduled != null) scheduled.run(); }
    }

    private static final class RecordingChannels implements BetaChannelRegistrar {
        private final Set<String> active = new java.util.LinkedHashSet<>();
        @Override public void register(String channel, Direction direction) { active.add(channel + direction); }
        @Override public void unregister(String channel, Direction direction) { active.remove(channel + direction); }
    }

    private static final class StateModule implements BetaRuntimeModule {
        private final BetaRuntimeModuleId id; private final BetaRuntimeModuleState state;
        private StateModule(BetaRuntimeModuleId id, BetaRuntimeModuleState state) { this.id = id; this.state = state; }
        @Override public BetaRuntimeModuleId id() { return id; }
        @Override public Set<BetaRuntimeModuleId> dependencies() { return Set.of(); }
        @Override public BetaRuntimeModuleDescriptor descriptor() {
            return new BetaRuntimeModuleDescriptor(id, Set.of(), Set.of(),
                    BetaMutationPolicy.READ_ONLY, true, Set.of());
        }
        @Override public BetaRuntimeModuleResult prepare(BetaRuntimeModuleContext context) { return BetaRuntimeModuleResult.ready(); }
        @Override public BetaRuntimeModuleResult start() { return BetaRuntimeModuleResult.running(); }
        @Override public BetaRuntimeModuleResult stop() { return BetaRuntimeModuleResult.stopped(); }
        @Override public BetaRuntimeModuleState state() { return state; }
    }
}
