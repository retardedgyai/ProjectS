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
import io.github.gyai.projects.beta.activation.BetaOperatorContributorRegistry;
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
import io.github.gyai.projects.combat.damage.DamageServiceStarterSwordRuntime;
import io.github.gyai.projects.combat.damage.BukkitDamageSnapshotResolver;
import io.github.gyai.projects.combat.damage.DamageShadowRuntimeContext;
import io.github.gyai.projects.combat.damage.DamageShadowTargetType;
import io.github.gyai.projects.combat.damage.StarterSwordDamageRouter;
import io.github.gyai.projects.combat.damage.StarterSwordDamageRoutePolicy;
import io.github.gyai.projects.combat.damage.StarterSwordRouteController;
import io.github.gyai.projects.combat.damage.StarterSwordRouteTracker;
import io.github.gyai.projects.combat.damage.StarterSwordShadowRuntime;
import io.github.gyai.projects.combat.damage.DamageService;
import io.github.gyai.projects.combat.damage.DamageMode;
import io.github.gyai.projects.combat.damage.DamageOffenseSnapshot;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.manager.EnhancementManager;
import io.github.gyai.projects.manager.ItemManager;
import io.github.gyai.projects.manager.PlayerManager;
import io.github.gyai.projects.player.StatType;
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
import org.bukkit.inventory.PlayerInventory;

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
import java.util.function.Consumer;
import java.lang.reflect.Field;
import sun.misc.Unsafe;

/** One executable regression path from confirmed hit through state packet delivery. */
public final class Track2ConfirmedHitPublisherIntegrationTest {
    private static final UUID PLAYER = UUID.fromString(
            "00000000-0000-0000-0000-000000000201");
    private static final UUID PLAYER_A = UUID.fromString(
            "00000000-0000-0000-0000-000000000202");
    private static final UUID PLAYER_B = UUID.fromString(
            "00000000-0000-0000-0000-000000000203");
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
    private static final UUID TARGET_G = UUID.fromString(
            "10000000-0000-0000-0000-000000000207");
    private static final Instant START = Instant.parse("2026-08-06T00:00:00Z");
    private static final AttackMetadata STARTER = new AttackMetadata(
            Set.of(AttackTag.NORMAL_ATTACK, AttackTag.MELEE, AttackTag.PHYSICAL), null);

    public static void main(String[] args) {
        Fixture fixture = new Fixture(Duration.ofSeconds(5));
        fixture.run();
        fixture.fullProductionIcePath();
        fixture.criticalCompositionAndFreezeExpiry();
        fixture.extremeMultiplierAndHealthPurity();
        fixture.profileIndependentFreezeAndExclusions();
        System.out.println("Track2ConfirmedHitPublisherIntegrationTest passed");
    }

    private static final class Fixture {
        private final MutableClock clock = new MutableClock(START);
        private final UUID[] targets = {
                TARGET_A, TARGET_B, TARGET_C, TARGET_D, TARGET_E, TARGET_F, TARGET_G};
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
                    Set.of(PLAYER, PLAYER_A, PLAYER_B), Set.of("world"), true, true));
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

        /** Real DamageService calculate/apply plus pre-hit, confirmed, secondary and packet flow. */
        private void fullProductionIcePath() {
            UUID directTarget = TARGET_F;
            targetId.set(directTarget);
            currentTarget.set(directTarget);
            AtomicReference<Double> health = new AtomicReference<>(10_000.0);
            LivingEntity serviceTarget = damageTarget(directTarget, health);
            DamageService service = realDamageService();
            AtomicReference<DamageApplicationResult> secondaryResult = new AtomicReference<>();
            boundary.secondaryApplier = secondary -> secondaryResult.set(
                    applySecondary(service, serviceTarget, secondary));
            Track2PreHitDamageModifier modifier = new Track2PreHitDamageModifier(
                    () -> BetaRuntimeModuleState.RUNNING, elements,
                    value -> value == serviceTarget, clock, ignored -> true);
            Track2ConfirmedHitObserver directObserver = new Track2ConfirmedHitObserver(
                    () -> BetaRuntimeModuleState.RUNNING, elements,
                    value -> value == serviceTarget, clock, ignored -> true);
            elements.setProfile(PLAYER, StagingElementProfile.ICE);
            DamageRequest defaultOff = directRequest(serviceTarget, DamageKind.NORMAL_ATTACK);
            DamageRequest unchanged = new Track2PreHitDamageModifier(
                    () -> BetaRuntimeModuleState.DISABLED, elements,
                    value -> value == serviceTarget, clock, ignored -> true)
                    .modify("default-off", defaultOff);
            assert unchanged == defaultOff && elements.diagnostics().isEmpty();

            for (int hit = 0; hit < 4; hit++) {
                DamageRequest request = directRequest(serviceTarget, DamageKind.NORMAL_ATTACK);
                DamageApplicationResult result = service.apply(request);
                directObserver.confirmed("real-freeze-" + hit, request, result);
            }
            var frozenState = elements.snapshots().target(directTarget).orElseThrow();
            assert frozenState.frozen() : frozenState + " diagnostics=" + elements.diagnostics();

            DamageRequest normal = modifier.modify("real-normal", directRequest(
                    serviceTarget, DamageKind.NORMAL_ATTACK));
            assert normal.iceDirectDamageMultiplier() == 1.08;
            StarterSwordRouteController controller = new StarterSwordRouteController(
                    true, new StarterSwordRouteTracker(), clock);
            StarterSwordDamageRouter router = new StarterSwordDamageRouter(
                    new DamageServiceStarterSwordRuntime(service), new AuthoritativeShadow(),
                    controller, new StarterSwordDamageRoutePolicy());
            DamageApplicationResult normalResult = router.apply(normal);
            assert normalResult.attempted();
            assert normalResult.calculation().finalRoundedDamage() == 108.0;
            assert normalResult.calculation().equals(service.calculate(normal));
            assert controller.snapshot().newRouteAppliedCount() == 1;
            assert controller.snapshot().legacyFallbackCount() == 0;
            directObserver.confirmed("real-normal", normal, normalResult);

            DamageRequest spin = modifier.modify("real-spin", directRequest(
                    serviceTarget, DamageKind.DIRECT_SKILL));
            assert spin.iceDirectDamageMultiplier() == 1.08;
            DamageApplicationResult spinResult = service.apply(spin);
            assert spinResult.calculation().finalRoundedDamage() == 108.0;
            directObserver.confirmed("real-spin", spin, spinResult);
            assert boundary.secondary.size() == 1;
            TrainingDummyElementBoundary.SecondaryDamage shatter = boundary.secondary.getFirst();
            assert shatter.amount() == 135.0;
            DamageApplicationResult appliedSecondary = secondaryResult.get();
            assert appliedSecondary != null && appliedSecondary.attempted();
            assert appliedSecondary.calculation().offenseResolvedDamage() == 135.0;
            assert !appliedSecondary.calculation().critical();
            assert appliedSecondary.calculation().lifeStealHealing() == 0.0;
            var state = elements.snapshots().target(directTarget).orElseThrow();
            assert state.cold() == 40.0 && !state.frozen();
            assert state.refreezeImmuneUntilMillis() == clock.millis() + 3_000L;
            publisher.publishOnce();
            assert publisher.diagnostics().statePacketSentCount() >= 3;
        }

        private void extremeMultiplierAndHealthPurity() {
            DamageRequest extreme = directRequest(target, DamageKind.NORMAL_ATTACK).toBuilder()
                    .pveMultiplier(Double.MAX_VALUE).iceDirectDamageMultiplier(1.08).build();
            assert extreme.calculationMultiplier()
                    == io.github.gyai.projects.combat.stat.StatCalculator.MAX_SAFE_VALUE;
            DamageRequest ordinary = extreme.toBuilder()
                    .pveMultiplier(1.25).iceDirectDamageMultiplier(1.08).build();
            assert Math.abs(ordinary.calculationMultiplier() - 1.35) < 1.0e-9;
            DamageRequest copied = ordinary.toBuilder().build();
            assert copied.attacker() == ordinary.attacker() && copied.target() == ordinary.target()
                    && copied.skillId().equals(ordinary.skillId()) && copied.castId().equals(ordinary.castId())
                    && copied.damageKind() == ordinary.damageKind()
                    && copied.attackMetadata().equals(ordinary.attackMetadata())
                    && copied.iceDirectDamageMultiplier() == 1.08
                    && copied.calculationMultiplier() == ordinary.calculationMultiplier();

            for (int hit = 0; hit < 6; hit++) {
                DamageRequest request = directRequest(target, DamageKind.NORMAL_ATTACK);
                observer.confirmed("health-ice-" + hit, request, result());
            }
            BetaOperatorContributorRegistry registry = new BetaOperatorContributorRegistry(
                    List.of(), () -> {
                        ArrayList<String> lines = new ArrayList<>(elements.latestIceDiagnostics(2));
                        lines.addAll(publisher.diagnosticLines());
                        return lines;
                    });
            List<String> beforeLines = registry.healthDetails();
            var beforeDiagnostics = publisher.diagnostics();
            var beforeTargets = elements.snapshots().targets();
            var beforeRuntimeDiagnostics = elements.diagnostics();
            assert beforeLines.stream().anyMatch(line -> line.startsWith("ice "));
            assert beforeLines.stream().anyMatch(line -> line.startsWith("elementState sent="));
            for (int read = 0; read < 100; read++) {
                assert registry.healthDetails().equals(beforeLines);
                assert publisher.diagnostics().equals(beforeDiagnostics);
                assert elements.snapshots().targets().equals(beforeTargets);
                assert elements.diagnostics().equals(beforeRuntimeDiagnostics);
            }
            for (String line : beforeLines) {
                assert !line.contains(PLAYER.toString()) && !line.contains(TARGET_A.toString())
                        && !line.contains("world") && line.length() <= 256;
            }
        }

        private void criticalCompositionAndFreezeExpiry() {
            PlayerManager players = new PlayerManager();
            DamageService service = realDamageService(players);
            players.getPlayerData(player).getStats().set(StatType.DAMAGE_INCREASE_PERCENT, .25);
            players.getPlayerData(player).getStats().set(StatType.CRITICAL_CHANCE_PERCENT, 1.0);
            players.getPlayerData(player).getStats().set(StatType.CRITICAL_DAMAGE_BONUS, .25);
            AtomicReference<Double> health = new AtomicReference<>(10_000.0);
            LivingEntity expiryTarget = damageTarget(TARGET_E, health);
            Track2ConfirmedHitObserver expiryObserver = new Track2ConfirmedHitObserver(
                    () -> BetaRuntimeModuleState.RUNNING, elements,
                    value -> value == expiryTarget, clock, ignored -> true);
            Track2PreHitDamageModifier expiryModifier = new Track2PreHitDamageModifier(
                    () -> BetaRuntimeModuleState.RUNNING, elements,
                    value -> value == expiryTarget, clock, ignored -> true);
            for (int hit = 0; hit < 4; hit++) {
                DamageRequest request = directRequest(expiryTarget, DamageKind.NORMAL_ATTACK)
                        .toBuilder().criticalAllowed(true)
                        .castId(UUID.nameUUIDFromBytes(("expiry-freeze-" + hit).getBytes()))
                        .build();
                expiryObserver.confirmed("expiry-freeze-" + hit, request, service.apply(request));
            }
            assert elements.snapshots().target(TARGET_E).orElseThrow().frozen();
            clock.advance(Duration.ofMillis(2_999));
            DamageRequest beforeExpiry = expiryModifier.modify("expiry-before", directRequest(
                    expiryTarget, DamageKind.NORMAL_ATTACK).toBuilder().criticalAllowed(true)
                    .castId(UUID.nameUUIDFromBytes("expiry-before".getBytes())).build());
            assert beforeExpiry.iceDirectDamageMultiplier() == 1.08;
            DamageApplicationResult composed = service.apply(beforeExpiry);
            assert composed.calculation().critical();
            assert composed.calculation().criticalMultiplier() == 2.0;
            assert composed.calculation().finalRoundedDamage() == 270.0;
            assert composed.calculation().finalRoundedDamage() != 291.6;
            players.getPlayerData(player).getStats().set(StatType.SKILL_DAMAGE_INCREASE_PERCENT, .10);
            DamageRequest spin = expiryModifier.modify("spin-snapshot", directRequest(
                    expiryTarget, DamageKind.DIRECT_SKILL).toBuilder().pveMultiplier(1.20)
                    .criticalAllowed(true).castId(UUID.nameUUIDFromBytes("spin-snapshot".getBytes()))
                    .build());
            DamageResult spinLegacy = service.calculate(spin);
            var spinSnapshot = new BukkitDamageSnapshotResolver(players,
                    allocate(FakeItems.class), allocate(FakeEnhancements.class))
                    .resolve(spin, spinLegacy.critical());
            assert spin.iceDirectDamageMultiplier() == 1.08;
            assert spinLegacy.critical() && spinLegacy.finalRoundedDamage() == 349.92;
            assert spinSnapshot.calculate().equals(spinLegacy);
            clock.advance(Duration.ofMillis(1));
            DamageRequest atExpiry = expiryModifier.modify("expiry-at", directRequest(
                    expiryTarget, DamageKind.NORMAL_ATTACK));
            assert atExpiry.iceDirectDamageMultiplier() == 1.0;
        }

        private void profileIndependentFreezeAndExclusions() {
            UUID targetUuid = TARGET_G;
            AtomicReference<Double> health = new AtomicReference<>(10_000.0);
            LivingEntity sharedTarget = damageTarget(targetUuid, health);
            Player playerA = playerProxy(PLAYER_A);
            Player playerB = playerProxy(PLAYER_B);
            Track2ConfirmedHitObserver sharedObserver = new Track2ConfirmedHitObserver(
                    () -> BetaRuntimeModuleState.RUNNING, elements,
                    value -> value == sharedTarget, clock, ignored -> true);
            Track2PreHitDamageModifier modifier = new Track2PreHitDamageModifier(
                    () -> BetaRuntimeModuleState.RUNNING, elements,
                    value -> value == sharedTarget, clock, ignored -> true);
            DamageService service = realDamageService();

            elements.setProfile(PLAYER_A, StagingElementProfile.ICE);
            for (int hit = 0; hit < 4; hit++) {
                DamageRequest request = directRequest(playerA, sharedTarget,
                        DamageKind.NORMAL_ATTACK);
                sharedObserver.confirmed("profile-a-freeze-" + hit, request, service.apply(request));
            }
            ElementRuntimeSnapshotPort.TargetSnapshot frozen = elements.snapshots()
                    .target(targetUuid).orElseThrow();
            assert frozen.cold() == 100.0 && frozen.frozen();

            elements.setProfile(PLAYER_B, StagingElementProfile.NONE);
            DamageRequest noneRequest = directRequest(playerB, sharedTarget,
                    DamageKind.NORMAL_ATTACK);
            DamageRequest noneModified = modifier.modify("profile-b-none", noneRequest);
            assert noneModified.iceDirectDamageMultiplier() == 1.08;
            DamageRequest noneModifiedTwice = modifier.modify("profile-b-none-twice", noneModified);
            assert noneModifiedTwice.iceDirectDamageMultiplier() == 1.08;
            assert service.apply(noneModified).calculation().finalRoundedDamage() == 108.0;
            sharedObserver.confirmed("profile-b-none", noneModified, service.apply(noneModified));
            ElementRuntimeSnapshotPort.TargetSnapshot afterNone = elements.snapshots()
                    .target(targetUuid).orElseThrow();
            assert afterNone.cold() == 100.0 && afterNone.frozen();

            elements.setProfile(PLAYER_B, StagingElementProfile.FIRE);
            DamageRequest fireRequest = directRequest(playerB, sharedTarget,
                    DamageKind.NORMAL_ATTACK);
            DamageRequest fireModified = modifier.modify("profile-b-fire", fireRequest);
            assert fireModified.iceDirectDamageMultiplier() == 1.08;
            sharedObserver.confirmed("profile-b-fire", fireModified, service.apply(fireModified));
            ElementRuntimeSnapshotPort.TargetSnapshot afterFire = elements.snapshots()
                    .target(targetUuid).orElseThrow();
            assert afterFire.cold() == 100.0 && afterFire.frozen()
                    && afterFire.fireStacks() == 1;

            DamageRequest pvp = directRequest(playerB, sharedTarget, DamageKind.NORMAL_ATTACK)
                    .toBuilder().mode(DamageMode.PVP).build();
            assert modifier.modify("profile-b-pvp", pvp) == pvp;
            assert pvp.iceDirectDamageMultiplier() == 1.0;

            DamageRequest dot = directRequest(playerB, sharedTarget, DamageKind.DAMAGE_OVER_TIME);
            assert modifier.modify("profile-b-dot", dot) == dot;
            DamageRequest secondary = directRequest(playerB, sharedTarget, DamageKind.DIRECT_SKILL)
                    .toBuilder().offenseSnapshot(new DamageOffenseSnapshot(10.0, false, 1.0))
                    .build();
            assert modifier.modify("profile-b-secondary", secondary) == secondary;

            elements.setProfile(PLAYER_B, StagingElementProfile.ICE);
            int secondaryBefore = boundary.secondary.size();
            DamageRequest spin = modifier.modify("profile-b-spin",
                    directRequest(playerB, sharedTarget, DamageKind.DIRECT_SKILL));
            assert spin.iceDirectDamageMultiplier() == 1.08;
            sharedObserver.confirmed("profile-b-spin", spin, service.apply(spin));
            assert boundary.secondary.size() == secondaryBefore + 1;
            TrainingDummyElementBoundary.SecondaryDamage shatter = boundary.secondary.getLast();
            assert shatter.amount() == 135.0 && !shatter.criticalAllowed();
            ElementRuntimeSnapshotPort.TargetSnapshot afterShatter = elements.snapshots()
                    .target(targetUuid).orElseThrow();
            assert afterShatter.cold() == 40.0 && !afterShatter.frozen();
            assert afterShatter.refreezeImmuneUntilMillis() == clock.millis() + 3_000L;
        }

        private DamageRequest directRequest(LivingEntity value, DamageKind kind) {
            return directRequest(player, value, kind);
        }

        private DamageRequest directRequest(Player attacker, LivingEntity value, DamageKind kind) {
            targetId.set(value.getUniqueId());
            String skillId = kind == DamageKind.NORMAL_ATTACK ? "normal_attack"
                    : kind == DamageKind.DIRECT_SKILL ? "spin_slash" : "dot";
            return DamageRequest.builder(attacker, value)
                    .skillId(skillId)
                    .damageKind(kind).damageType(DamageType.PHYSICAL).mode(DamageMode.PVE)
                    .fixedDamage(100.0).coefficient(0.0).criticalAllowed(false)
                    .attackMetadata(kind == DamageKind.NORMAL_ATTACK ? STARTER : new AttackMetadata(
                            Set.of(AttackTag.SKILL, AttackTag.MELEE, AttackTag.PHYSICAL), null))
                    .build();
        }

        private DamageApplicationResult applySecondary(DamageService service, LivingEntity target,
                                           TrainingDummyElementBoundary.SecondaryDamage damage) {
            return service.apply(DamageRequest.builder(player, target).skillId(null)
                    .damageType(DamageType.PHYSICAL).damageKind(DamageKind.DIRECT_SKILL)
                    .mode(DamageMode.PVE).fixedDamage(damage.amount()).coefficient(0.0)
                    .criticalAllowed(false).lifeStealEfficiency(0.0)
                    .offenseSnapshot(new DamageOffenseSnapshot(damage.amount(), false, 1.0))
                    .attackMetadata(damage.metadata()).build());
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
        return playerProxy(PLAYER);
    }

    private static Player playerProxy(UUID playerId) {
        World world = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> method.getName().equals("getName")
                        ? "world" : defaultValue(method.getReturnType()));
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "getWorld" -> world;
                    case "hasPermission" -> true;
                    case "getInventory" -> Proxy.newProxyInstance(
                            PlayerInventory.class.getClassLoader(), new Class<?>[]{PlayerInventory.class},
                            (inventory, inventoryMethod, inventoryArgs) -> defaultValue(
                                    inventoryMethod.getReturnType()));
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static LivingEntity targetProxy(AtomicReference<UUID> targetId) {
        return (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(), new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> method.getName().equals("getUniqueId")
                        ? targetId.get() : defaultValue(method.getReturnType()));
    }

    private static LivingEntity damageTarget(UUID id, AtomicReference<Double> health) {
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "isValid" -> true;
                    case "getHealth" -> health.get();
                    case "getAbsorptionAmount" -> 0.0;
                    case "getEquipment" -> null;
                    case "damage" -> { health.set(health.get() - (Double) args[0]); yield null; }
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static DamageService realDamageService() {
        return realDamageService(new PlayerManager());
    }

    private static DamageService realDamageService(PlayerManager players) {
        return new DamageService(players, allocate(FakeItems.class),
                allocate(FakeEnhancements.class), allocate(FakeDummies.class));
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (T) ((Unsafe) field.get(null)).allocateInstance(type);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unsafe fixture unavailable", exception);
        }
    }

    private static final class FakeItems extends ItemManager {
        private FakeItems() { super(null); }
    }

    private static final class AuthoritativeShadow implements StarterSwordShadowRuntime {
        @Override public DamageApplicationResult apply(DamageRequest request) {
            throw new AssertionError("authoritative router must not use shadow apply");
        }
        @Override public DamageShadowRuntimeContext resolveContext(DamageRequest request) {
            return new DamageShadowRuntimeContext(START, PLAYER, request.target().getUniqueId(),
                    DamageShadowTargetType.TRAINING_DUMMY, "starter_sword", 0);
        }
        @Override public boolean enabled() { return false; }
        @Override public void compareLegacySafely(DamageShadowRuntimeContext context,
                                                   DamageRequest request,
                                                   DamageResult legacyResult) { }
        @Override public java.util.Optional<io.github.gyai.projects.combat.damage.DamageShadowComparison>
        comparePrecalculatedSafely(DamageShadowRuntimeContext context, DamageRequest request,
                                   DamageResult legacyResult, DamageResult shadowResult,
                                   io.github.gyai.projects.combat.damage.DamageCalculationSnapshot snapshot) {
            return java.util.Optional.empty();
        }
    }

    private static final class FakeEnhancements extends EnhancementManager {
        private FakeEnhancements() { super(null, null, null); }
        @Override public double getPhysicalAttackPower(Player player,
                                                       org.bukkit.inventory.ItemStack item) { return 0.0; }
        @Override public double getMagicalAttackPower(Player player,
                                                      org.bukkit.inventory.ItemStack item) { return 0.0; }
    }

    private static final class FakeDummies extends TrainingDummyManager {
        private FakeDummies() { super(null); }
        @Override public boolean isTrainingDummy(org.bukkit.entity.Entity entity) { return false; }
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
        private Consumer<SecondaryDamage> secondaryApplier = ignored -> { };

        private FakeBoundary(Set<UUID> live) { this.live = new LinkedHashSet<>(live); }

        private int runtimeId(UUID target) { return 700 + Math.max(0, liveIndex(target)); }

        private int liveIndex(UUID target) { return new ArrayList<>(live).indexOf(target); }

        @Override public boolean isLiveTrainingDummy(UUID targetId) { return live.contains(targetId); }
        @Override public int targetRuntimeId(UUID targetId) { return runtimeId(targetId); }
        @Override public List<UUID> nearbyTrainingDummies(UUID centerId, double radius, int limit) {
            return List.of();
        }
        @Override public void applySecondaryDamage(SecondaryDamage damage) {
            secondary.add(damage);
            secondaryApplier.accept(damage);
        }
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
