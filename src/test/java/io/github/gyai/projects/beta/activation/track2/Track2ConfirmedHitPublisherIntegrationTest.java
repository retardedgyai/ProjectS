package io.github.gyai.projects.beta.activation.track2;

import io.github.gyai.projects.beta.activation.BetaActivationAudience;
import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.beta.activation.BetaActivationTargetScope;
import io.github.gyai.projects.beta.activation.BetaMutationPolicy;
import io.github.gyai.projects.beta.activation.BetaRuntimeModule;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleContext;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleId;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleResult;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.beta.activation.track4.BetaChannelRegistrar;
import io.github.gyai.projects.beta.activation.track4.BetaStateTransport;
import io.github.gyai.projects.beta.activation.track4.ClientBetaProtocolRuntime;
import io.github.gyai.projects.beta.activation.track4.ElementSnapshotProtocolAdapter;
import io.github.gyai.projects.beta.activation.track4.ElementSnapshotProtocolPublisher;
import io.github.gyai.projects.beta.activation.track4.RunningCapabilityRegistry;
import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.combat.damage.DamageApplicationResult;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageRequest;
import io.github.gyai.projects.combat.damage.DamageResult;
import io.github.gyai.projects.combat.damage.DamageType;
import io.github.gyai.projects.combat.element.ice.IceElementEngine;
import io.github.gyai.projects.network.beta.BetaCapabilityAcknowledgement;
import io.github.gyai.projects.network.beta.BetaCapabilityDescriptor;
import io.github.gyai.projects.network.beta.BetaCapabilityId;
import io.github.gyai.projects.network.beta.BetaCapabilityPolicy;
import io.github.gyai.projects.network.beta.BetaCapabilitySessionService;
import io.github.gyai.projects.network.beta.BetaMessageEnvelope;
import io.github.gyai.projects.network.beta.BetaProtocolCodec;
import io.github.gyai.projects.network.beta.BetaProtocolVersion;
import io.github.gyai.projects.network.beta.ElementDisplaySnapshot;
import io.github.gyai.projects.network.beta.ElementDisplaySnapshotCodec;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** One executable regression path from confirmed hit through state packet delivery. */
public final class Track2ConfirmedHitPublisherIntegrationTest {
    private static final UUID PLAYER = UUID.fromString(
            "00000000-0000-0000-0000-000000000201");
    private static final UUID TARGET_A = UUID.fromString(
            "10000000-0000-0000-0000-000000000201");
    private static final UUID TARGET_B = UUID.fromString(
            "10000000-0000-0000-0000-000000000202");
    private static final UUID TARGET_C = UUID.fromString(
            "10000000-0000-0000-0000-000000000203");
    private static final UUID TARGET_D = UUID.fromString(
            "10000000-0000-0000-0000-000000000204");
    private static final UUID TARGET_E = UUID.fromString(
            "10000000-0000-0000-0000-000000000205");
    private static final UUID TARGET_F = UUID.fromString(
            "10000000-0000-0000-0000-000000000206");
    private static final Instant START = Instant.parse("2026-08-06T00:00:00Z");
    private static final AttackMetadata STARTER = new AttackMetadata(
            Set.of(AttackTag.NORMAL_ATTACK, AttackTag.MELEE, AttackTag.PHYSICAL), null);

    public static void main(String[] args) {
        Fixture fixture = new Fixture(Duration.ofSeconds(5));
        fixture.run();
        System.out.println("Track2ConfirmedHitPublisherIntegrationTest passed");
    }

    private static final class Fixture {
        private final MutableClock clock = new MutableClock(START);
        private final UUID[] targets = {
                TARGET_A, TARGET_B, TARGET_C, TARGET_D, TARGET_E, TARGET_F};
        private final FakeBoundary boundary = new FakeBoundary(Set.of(targets));
        private final TrainingDummyElementRuntime elements =
                new TrainingDummyElementRuntime(boundary, clock);
        private final StateModule elementsModule = new StateModule();
        private final ClientBetaProtocolRuntime protocol;
        private final AtomicReference<UUID> currentTarget = new AtomicReference<>(TARGET_A);
        private final Player player = playerProxy();
        private final AtomicReference<UUID> targetId = new AtomicReference<>(TARGET_A);
        private final LivingEntity target = targetProxy(targetId);
        private final FakeTransport transport = new FakeTransport(currentTarget);
        private final ElementSnapshotProtocolAdapter adapter;
        private final ElementSnapshotProtocolPublisher publisher;
        private final Track2ConfirmedHitObserver observer;

        private Fixture(Duration ttl) {
            BetaCapabilityPolicy capabilityPolicy = new BetaCapabilityPolicy(
                    8, ttl, List.of(BetaCapabilityDescriptor.v1(BetaCapabilityId.ELEMENTS)));
            EnumMap<BetaCapabilityId, BetaRuntimeModule> producers =
                    new EnumMap<>(BetaCapabilityId.class);
            producers.put(BetaCapabilityId.ELEMENTS, elementsModule);
            protocol = new ClientBetaProtocolRuntime(
                    new FakeChannels(),
                    new BetaCapabilitySessionService(
                            capabilityPolicy, clock),
                    new io.github.gyai.projects.network.beta.BetaCommandRouter(
                            new io.github.gyai.projects.network.beta.BetaRateLimiter(8, clock),
                            (context, command) ->
                                    io.github.gyai.projects.network.beta.BetaCommandAuthorization
                                            .Decision.allow(), 8),
                    new RunningCapabilityRegistry(producers));
            adapter = new ElementSnapshotProtocolAdapter(
                    elements.snapshots(),
                    () -> BetaRuntimeModuleState.RUNNING,
                    elementsModule::state,
                    protocol::capabilitySnapshot,
                    ignored -> true);
            publisher = new ElementSnapshotProtocolPublisher(
                    adapter, transport, protocol::capabilitySnapshot, clock);
            observer = new Track2ConfirmedHitObserver(
                    () -> BetaRuntimeModuleState.RUNNING,
                    elements,
                    targetValue -> targetValue == target,
                    clock,
                    playerId -> protocol.capabilitySnapshot(playerId)
                            .supports(BetaCapabilityId.ELEMENTS, 1));
        }

        private void run() {
            protocol.start();
            elements.configure(new BetaActivationPolicy(
                    BetaActivationAudience.ALLOWLIST,
                    BetaActivationTargetScope.TRAINING_DUMMY_ONLY,
                    BetaMutationPolicy.READ_ONLY,
                    Set.of(PLAYER), Set.of("world"), true, true));
            elements.start();
            elements.setProfile(PLAYER, StagingElementProfile.FIRE);
            publisher.start();

            var advertisement = advertise();
            confirmed("ack-before", TARGET_A);
            assert elements.snapshots().target(TARGET_A).isEmpty();
            publisher.publishOnce();
            assert transport.packets.isEmpty();
            assert publisher.diagnostics().statePacketSentCount() == 0;

            acknowledge(advertisement);
            confirmed("ack-after", TARGET_A);
            var first = elements.snapshots().target(TARGET_A).orElseThrow();
            assert first.targetRuntimeId() == boundary.runtimeId(TARGET_A);
            assert first.stateRevision() > 0;
            assert first.fireStacks() == 1;
            publisher.publishOnce();
            assert publisher.diagnostics().statePacketAttemptCount() == 1;
            assert publisher.diagnostics().statePacketSentCount() == 1;
            assert publisher.diagnostics().statePacketFailureCount() == 0;
            assert publisher.diagnostics().snapshotMissingCount() == 0;
            assert publisher.diagnostics().lastFireStacks().orElseThrow() == 1;
            assertPacket(0, first);

            protocol.disconnect(PLAYER);
            confirmed("disconnect", TARGET_B);
            publisher.publishOnce();
            assert elements.snapshots().target(TARGET_B).isEmpty();
            assert elements.snapshots().target(TARGET_A).isPresent();
            assert transport.packets.size() == 1;

            acknowledge(advertise());
            clock.advance(Duration.ofSeconds(6));
            confirmed("expired", TARGET_C);
            publisher.publishOnce();
            assert elements.snapshots().target(TARGET_C).isEmpty();
            assert transport.packets.size() == 1;

            acknowledge(advertise());
            confirmed("renewed", TARGET_D);
            publisher.publishOnce();
            assert elements.snapshots().target(TARGET_D).isPresent();
            assert publisher.diagnostics().statePacketSentCount() == 2;

            confirmed("duplicate", TARGET_E);
            var duplicateFirst = elements.snapshots().target(TARGET_E).orElseThrow();
            confirmed("duplicate", TARGET_E);
            var duplicateSecond = elements.snapshots().target(TARGET_E).orElseThrow();
            assert duplicateSecond.fireStacks() == 1;
            assert duplicateSecond.stateRevision() == duplicateFirst.stateRevision();

            Track2ConfirmedHitObserver failingResolver = new Track2ConfirmedHitObserver(
                    () -> BetaRuntimeModuleState.RUNNING,
                    elements,
                    targetValue -> targetValue == target,
                    clock,
                    ignored -> { throw new IllegalStateException("resolver failure"); });
            int secondaryBefore = boundary.secondary.size();
            failingResolver.confirmed("resolver-failure", requestFor(TARGET_F), result());
            assert elements.snapshots().target(TARGET_F).isEmpty();
            assert boundary.secondary.size() == secondaryBefore;
        }

        private io.github.gyai.projects.network.beta.BetaCapabilityAdvertisement advertise() {
            return protocol.advertise(PLAYER, true).orElseThrow();
        }

        private void acknowledge(
                io.github.gyai.projects.network.beta.BetaCapabilityAdvertisement advertisement
        ) {
            assert protocol.acknowledge(PLAYER, new BetaCapabilityAcknowledgement(
                    BetaProtocolVersion.CURRENT,
                    advertisement.sessionId(), advertisement.advertisementRevision(),
                    advertisement.capabilities()))
                    == BetaCapabilitySessionService.AcknowledgeStatus.ACCEPTED;
        }

        private void confirmed(String hitId, UUID target) {
            targetId.set(target);
            currentTarget.set(target);
            observer.confirmed(hitId, requestFor(target), result());
        }

        private void assertPacket(int index, ElementRuntimeSnapshotPort.TargetSnapshot expected) {
            BetaMessageEnvelope envelope = new BetaProtocolCodec()
                    .decodeMessage(transport.packets.get(index)).value();
            assert envelope.capabilityId() == BetaCapabilityId.ELEMENTS;
            assert envelope.capabilityPayloadVersion() == 1;
            ElementDisplaySnapshot decoded = new ElementDisplaySnapshotCodec()
                    .decode(envelope.payload());
            assert decoded.targetNetworkId() == expected.targetRuntimeId();
            assert decoded.stateRevision() == expected.stateRevision();
            assert decoded.fireStacks() == 1;
        }

        private DamageRequest requestFor(UUID targetValue) {
            targetId.set(targetValue);
            return DamageRequest.builder(player, target)
                .skillId("normal_attack")
                .damageKind(DamageKind.NORMAL_ATTACK)
                .damageType(DamageType.PHYSICAL)
                .fixedDamage(10.0)
                .coefficient(0.0)
                .attackMetadata(STARTER)
                .build();
        }
    }

    private static DamageApplicationResult result() {
        return new DamageApplicationResult(new DamageResult(
                10.0, 10.0, 1.0, 10.0, false, 1.0,
                0.0, 0.0, 1.0, 1.0, 1.0,
                10.0, 0.0, 10.0, 0.0, 10.0), true,
                0.0, 10.0, 0.0);
    }

    private static Player playerProxy() {
        World world = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> method.getName().equals("getName")
                        ? "world" : defaultValue(method.getReturnType()));
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> PLAYER;
                    case "getWorld" -> world;
                    case "hasPermission" -> true;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static LivingEntity targetProxy(AtomicReference<UUID> targetId) {
        return (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(), new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> method.getName().equals("getUniqueId")
                        ? targetId.get() : defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        return 0.0d;
    }

    private static final class FakeBoundary implements TrainingDummyElementBoundary {
        private final Set<UUID> live;
        private final List<SecondaryDamage> secondary = new ArrayList<>();

        private FakeBoundary(Set<UUID> live) { this.live = new LinkedHashSet<>(live); }

        private int runtimeId(UUID target) { return 700 + Math.max(0, liveIndex(target)); }

        private int liveIndex(UUID target) { return new ArrayList<>(live).indexOf(target); }

        @Override public boolean isLiveTrainingDummy(UUID targetId) { return live.contains(targetId); }
        @Override public int targetRuntimeId(UUID targetId) { return runtimeId(targetId); }
        @Override public List<UUID> nearbyTrainingDummies(UUID centerId, double radius, int limit) {
            return List.of();
        }
        @Override public void applySecondaryDamage(SecondaryDamage damage) { secondary.add(damage); }
        @Override public void publishVisual(VisualEvent event) { }
        @Override public Cancellable scheduleCleanup(Runnable task, long periodMillis) {
            return new Cancellable() {
                private boolean cancelled;
                @Override public void cancel() { cancelled = true; }
                @Override public boolean cancelled() { return cancelled; }
            };
        }
    }

    private static final class FakeTransport implements BetaStateTransport {
        private final AtomicReference<UUID> target;
        private final List<byte[]> packets = new ArrayList<>();

        private FakeTransport(AtomicReference<UUID> target) { this.target = target; }
        @Override public List<UUID> viewers() { return List.of(PLAYER); }
        @Override public UUID visibleTarget(UUID viewerId) { return target.get(); }
        @Override public void send(UUID viewerId, String channel, byte[] packet) {
            packets.add(packet.clone());
        }
        @Override public SendResult sendResult(UUID viewerId, String channel, byte[] packet) {
            send(viewerId, channel, packet);
            return SendResult.SENT;
        }
        @Override public Cancellable schedule(Runnable task, long periodMillis) {
            return new Cancellable() {
                private boolean cancelled;
                @Override public void cancel() { cancelled = true; }
                @Override public boolean cancelled() { return cancelled; }
            };
        }
    }

    private static final class FakeChannels implements BetaChannelRegistrar {
        @Override public void register(String channel, Direction direction) { }
        @Override public void unregister(String channel, Direction direction) { }
    }

    private static final class StateModule implements BetaRuntimeModule {
        @Override public BetaRuntimeModuleId id() { return BetaRuntimeModuleId.COMBAT_ELEMENTS; }
        @Override public Set<BetaRuntimeModuleId> dependencies() { return Set.of(); }
        @Override public BetaRuntimeModuleResult prepare(BetaRuntimeModuleContext context) {
            return BetaRuntimeModuleResult.ready();
        }
        @Override public BetaRuntimeModuleResult start() { return BetaRuntimeModuleResult.running(); }
        @Override public BetaRuntimeModuleResult stop() { return BetaRuntimeModuleResult.stopped(); }
        @Override public BetaRuntimeModuleState state() { return BetaRuntimeModuleState.RUNNING; }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
