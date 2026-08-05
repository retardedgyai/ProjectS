package io.github.gyai.projects.beta.activation.track2;

import io.github.gyai.projects.beta.activation.BetaActivationAudience;
import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.beta.activation.BetaActivationTargetScope;
import io.github.gyai.projects.beta.activation.BetaMutationPolicy;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleContext;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleId;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.combat.damage.DamageElement;
import io.github.gyai.projects.combat.element.ice.IceElementEngine;
import io.github.gyai.projects.feature.FeatureFlagSnapshot;
import io.github.gyai.projects.feature.FeatureKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CombatElementsActivationRuntimeTest {
    private static final UUID PLAYER_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID PLAYER_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID CENTER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID NEARBY = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final AttackMetadata STARTER = new AttackMetadata(
            Set.of(AttackTag.NORMAL_ATTACK, AttackTag.MELEE, AttackTag.PHYSICAL), null);
    private static final AttackMetadata SPIN = new AttackMetadata(
            Set.of(AttackTag.SKILL, AttackTag.MELEE, AttackTag.PHYSICAL), null);

    private CombatElementsActivationRuntimeTest() { }

    public static void main(String[] args) throws Exception {
        noneIsStrictlyObservationalAndParticipationIsDeduplicated();
        metadataIsComposedImmutably();
        fireFixtureDetonatesOnceAndRetainsThreeStacks();
        fireTracksMultipleContributorsAndDecays();
        iceStagesFreezeBonusAndOneShotShatter();
        invalidTargetsAndOriginsAreRejected();
        lifecycleCleansEveryBoundedState();
        moduleProviderIsFailClosedAndIdempotent();
        operatorContributorIsPermissionBounded();
        sourceBoundaryHasNoBukkitOrCentralRegistration();
        System.out.println("CombatElementsActivationRuntimeTest: OK");
    }

    private static void noneIsStrictlyObservationalAndParticipationIsDeduplicated() {
        Fixture fixture = new Fixture();
        fixture.runtime.start();
        var input = fixture.starter(PLAYER_A, CENTER, "none-1", 0L);
        var first = fixture.runtime.observe(input);
        var duplicate = fixture.runtime.observe(input);
        assert first.metadata() == STARTER;
        assert !first.observed();
        assert first.directDamageMultiplier() == 1.0;
        assert first.secondaryApplications() == 0;
        assert duplicate.secondaryApplications() == 0;
        assert fixture.boundary.secondary.isEmpty();
        assert fixture.runtime.participation().after(0, 10).size() == 1;
        assert fixture.runtime.snapshots().targets().isEmpty();
    }

    private static void metadataIsComposedImmutably() {
        Fixture fixture = new Fixture();
        fixture.runtime.start();
        fixture.runtime.setProfile(PLAYER_A, StagingElementProfile.FIRE);
        var fire = fixture.runtime.observe(fixture.starter(PLAYER_A, CENTER, "fire-meta", 0L));
        assert fire.metadata() != STARTER;
        assert fire.metadata().tags().equals(Set.of(
                AttackTag.NORMAL_ATTACK, AttackTag.MELEE, AttackTag.PHYSICAL, AttackTag.FIRE));
        assert fire.metadata().elements().value(DamageElement.FIRE) == 10.0;
        assert STARTER.tags().equals(Set.of(
                AttackTag.NORMAL_ATTACK, AttackTag.MELEE, AttackTag.PHYSICAL));
        try {
            fire.metadata().tags().add(AttackTag.ICE);
            throw new AssertionError("metadata tags were mutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static void fireFixtureDetonatesOnceAndRetainsThreeStacks() {
        Fixture fixture = new Fixture();
        fixture.boundary.live.add(NEARBY);
        fixture.boundary.nearby = List.of(CENTER, NEARBY, NEARBY);
        fixture.runtime.start();
        fixture.runtime.setProfile(PLAYER_A, StagingElementProfile.FIRE);
        for (int hit = 1; hit <= 10; hit++) {
            var result = fixture.runtime.observe(
                    fixture.starter(PLAYER_A, CENTER, "fire-" + hit, hit));
            assert result.secondaryApplications() == (hit == 10 ? 2 : 0);
        }
        assert fixture.boundary.secondary.size() == 2;
        assert fixture.boundary.secondary.stream().noneMatch(
                TrainingDummyElementBoundary.SecondaryDamage::criticalAllowed);
        assert fixture.boundary.secondary.stream().allMatch(damage ->
                damage.origin() == IceElementEngine.DamageOrigin.AUTOMATIC_SECONDARY);
        assert fixture.boundary.secondary.stream().filter(damage ->
                damage.targetId().equals(CENTER)).findFirst().orElseThrow().amount() == 25.0;
        assert fixture.boundary.secondary.stream().filter(damage ->
                damage.targetId().equals(NEARBY)).findFirst().orElseThrow().amount() == 15.0;
        assert fixture.runtime.snapshots().target(CENTER).orElseThrow().fireStacks() == 3;
        int before = fixture.boundary.secondary.size();
        fixture.runtime.observe(fixture.starter(PLAYER_A, CENTER, "fire-10", 10L));
        assert fixture.boundary.secondary.size() == before;
    }

    private static void fireTracksMultipleContributorsAndDecays() {
        Fixture fixture = new Fixture();
        fixture.runtime.start();
        fixture.runtime.setProfile(PLAYER_A, StagingElementProfile.FIRE);
        fixture.runtime.setProfile(PLAYER_B, StagingElementProfile.FIRE);
        fixture.runtime.observe(fixture.starter(PLAYER_A, CENTER, "multi-a", 0L));
        fixture.runtime.observe(fixture.starter(PLAYER_B, CENTER, "multi-b", 1L));
        assert fixture.runtime.snapshots().target(CENTER).orElseThrow().contributorCount() == 2;
        fixture.clock.millis = 7_001L;
        fixture.boundary.runCleanup();
        assert fixture.runtime.snapshots().target(CENTER).orElseThrow().fireStacks() == 1;
        fixture.clock.millis = 9_001L;
        fixture.boundary.runCleanup();
        assert fixture.runtime.snapshots().target(CENTER).orElseThrow().fireStacks() == 0;
    }

    private static void iceStagesFreezeBonusAndOneShotShatter() {
        Fixture fixture = new Fixture();
        fixture.runtime.start();
        fixture.runtime.setProfile(PLAYER_A, StagingElementProfile.ICE);
        for (int hit = 1; hit <= 4; hit++) {
            var outcome = fixture.runtime.observe(
                    fixture.starter(PLAYER_A, CENTER, "ice-" + hit, hit));
            if (hit < 4) assert !outcome.frozeNow();
            else assert outcome.frozeNow();
        }
        var frozen = fixture.runtime.snapshots().target(CENTER).orElseThrow();
        assert frozen.iceStage() == IceElementEngine.Stage.FROZEN;
        assert frozen.cold() == 100.0;
        var amplifiedStarter = fixture.runtime.observe(
                fixture.starter(PLAYER_A, CENTER, "ice-amplified", 5L));
        assert amplifiedStarter.directDamageMultiplier() == 1.08;
        assert !amplifiedStarter.shattered();
        var shatter = fixture.runtime.observe(fixture.spin(PLAYER_A, CENTER, "shatter", 6L));
        assert shatter.directDamageMultiplier() == 1.08;
        assert shatter.shattered();
        assert shatter.secondaryApplications() == 1;
        assert shatter.metadata().tags().equals(Set.of(
                AttackTag.SKILL, AttackTag.MELEE, AttackTag.PHYSICAL,
                AttackTag.ICE, AttackTag.SHATTER));
        assert fixture.boundary.secondary.getFirst().origin()
                == IceElementEngine.DamageOrigin.SHATTER_ADDITIONAL;
        var residual = fixture.runtime.snapshots().target(CENTER).orElseThrow();
        assert residual.cold() == 40.0;
        assert residual.refreezeImmuneUntilMillis() == 3_006L;
        var noSecondShatter = fixture.runtime.observe(
                fixture.spin(PLAYER_A, CENTER, "shatter-again", 7L));
        assert !noSecondShatter.shattered();
        assert fixture.boundary.secondary.size() == 1;

        fixture.clock.millis = 3_010L;
        fixture.boundary.runCleanup();
        assert !fixture.runtime.snapshots().target(CENTER).orElseThrow().frozen();
    }

    private static void invalidTargetsAndOriginsAreRejected() {
        Fixture fixture = new Fixture();
        fixture.runtime.start();
        fixture.runtime.setProfile(PLAYER_A, StagingElementProfile.ICE);
        fixture.boundary.live.remove(CENTER);
        assert !fixture.runtime.observe(fixture.starter(PLAYER_A, CENTER, "not-live", 0)).observed();
        fixture.boundary.live.add(CENTER);
        var playerTarget = new TrainingDummyElementRuntime.AttackInput(
                "pvp", PLAYER_A, CENTER, "starter_sword",
                TrainingDummyElementRuntime.AttackType.STARTER_SWORD_NORMAL,
                IceElementEngine.DamageOrigin.NORMAL_ATTACK_DIRECT, STARTER, 100,
                true, true, true, false, 1);
        assert !fixture.runtime.observe(playerTarget).observed();
        var secondary = new TrainingDummyElementRuntime.AttackInput(
                "secondary", PLAYER_A, CENTER, "spin_slash",
                TrainingDummyElementRuntime.AttackType.SPIN_SLASH,
                IceElementEngine.DamageOrigin.AUTOMATIC_SECONDARY, SPIN, 100,
                true, true, false, false, 2);
        assert !fixture.runtime.observe(secondary).observed();
        assert fixture.boundary.secondary.isEmpty();
        assert fixture.runtime.snapshots().targets().isEmpty();
    }

    private static void lifecycleCleansEveryBoundedState() {
        Fixture fixture = new Fixture();
        assert fixture.runtime.start();
        assert fixture.runtime.start();
        fixture.runtime.setProfile(PLAYER_A, StagingElementProfile.FIRE);
        fixture.runtime.observe(fixture.starter(PLAYER_A, CENTER, "cleanup", 0));
        fixture.runtime.playerLoggedOut(PLAYER_A);
        assert fixture.runtime.snapshots().playerProfile(PLAYER_A) == StagingElementProfile.NONE;
        fixture.runtime.targetRemoved(CENTER);
        assert fixture.runtime.snapshots().targets().isEmpty();
        fixture.runtime.close();
        fixture.runtime.close();
        assert fixture.boundary.cancelled;
        assert !fixture.runtime.running();
        assert fixture.runtime.participation().after(0, 10).isEmpty();
    }

    private static void moduleProviderIsFailClosedAndIdempotent() {
        Fixture disabled = new Fixture();
        var provider = new CombatElementsRuntimeModuleProvider(disabled.boundary, disabled.clock);
        assert provider.moduleId() == BetaRuntimeModuleId.COMBAT_ELEMENTS;
        CombatElementsRuntimeModule module = provider.combatElementsModule();
        var disabledContext = context(FeatureFlagSnapshot.allDisabled());
        assert module.prepare(disabledContext).state() == BetaRuntimeModuleState.DISABLED;
        assert disabled.boundary.schedules == 0;

        Fixture enabled = new Fixture();
        var enabledProvider = new CombatElementsRuntimeModuleProvider(enabled.boundary, enabled.clock);
        CombatElementsRuntimeModule enabledModule = enabledProvider.combatElementsModule();
        assert enabledModule.prepare(context(FeatureFlagSnapshot.of(Map.of(
                FeatureKey.FIRE_SYSTEM, true, FeatureKey.ICE_SYSTEM, true)))).success();
        assert enabledModule.start().success();
        assert enabledModule.start().success();
        assert enabled.boundary.schedules == 1;
        assert enabledModule.health().schedulerRunning();
        assert enabledModule.stop().success();
        assert enabledModule.stop().success();
        assert enabled.boundary.cancelled;
        assert enabledModule.state() == BetaRuntimeModuleState.STOPPED;
    }

    private static void operatorContributorIsPermissionBounded() {
        Fixture fixture = new Fixture();
        var commands = new CombatElementsOperatorCommandContributor(fixture.runtime);
        assert commands.route().equals("staging element");
        assert commands.execute(false, PLAYER_A, List.of("fire")).getFirst()
                .contains("projects.dev");
        assert fixture.runtime.snapshots().playerProfile(PLAYER_A) == StagingElementProfile.NONE;
        assert commands.execute(true, PLAYER_A, List.of("fire")).getFirst().contains("FIRE");
        assert commands.execute(true, PLAYER_A, List.of("status")).getFirst().contains("FIRE");
        assert commands.execute(true, PLAYER_A, List.of("none")).getFirst().contains("NONE");
        assert commands.execute(true, PLAYER_A, List.of("unknown")).getFirst().startsWith("Usage:");
    }

    private static void sourceBoundaryHasNoBukkitOrCentralRegistration() throws IOException {
        Path root = Path.of("src/main/java/io/github/gyai/projects/beta/activation/track2");
        StringBuilder source = new StringBuilder();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")
                    && !path.getFileName().toString().equals(
                    "BukkitTrainingDummyElementBoundary.java")).toList()) {
                source.append(Files.readString(file));
            }
        }
        String text = source.toString();
        assert !text.contains("org.bukkit");
        assert !text.contains("registerIncomingPluginChannel");
        assert !text.contains("registerOutgoingPluginChannel");
        assert !text.contains("registerEvents(");
        assert !text.contains("runTaskTimer");
        String bukkitBoundary = Files.readString(root.resolve(
                "BukkitTrainingDummyElementBoundary.java"));
        assert bukkitBoundary.contains("DamageService damageService");
        assert bukkitBoundary.contains("criticalAllowed(false)");
        assert bukkitBoundary.contains("lifeStealEfficiency(0.0)");
        assert !bukkitBoundary.contains("private final Player");
        assert !bukkitBoundary.contains("private final LivingEntity");
        assert !bukkitBoundary.contains("private final Entity");
        String plugin = Files.readString(Path.of(
                "src/main/java/io/github/gyai/projects/ProjectSPlugin.java"));
        String command = Files.readString(Path.of(
                "src/main/java/io/github/gyai/projects/command/ProjectCommand.java"));
        assert !plugin.contains("CombatElementsRuntimeModuleProvider");
        assert !command.contains("staging element");
    }

    private static BetaRuntimeModuleContext context(FeatureFlagSnapshot flags) {
        return new BetaRuntimeModuleContext(
                new BetaActivationPolicy(BetaActivationAudience.ALLOWLIST,
                        BetaActivationTargetScope.TRAINING_DUMMY_ONLY,
                        BetaMutationPolicy.READ_ONLY, Set.of(PLAYER_A), Set.of("world"),
                        true, false),
                flags,
                Set.of(CombatElementsRuntimeModule.TRAINING_DUMMY_INFRASTRUCTURE,
                        CombatElementsRuntimeModule.DAMAGE_SERVICE_INFRASTRUCTURE),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), true);
    }

    private static final class Fixture {
        final MutableClock clock = new MutableClock();
        final FakeBoundary boundary = new FakeBoundary();
        final TrainingDummyElementRuntime runtime = new TrainingDummyElementRuntime(boundary, clock);

        Fixture() {
            boundary.live.add(CENTER);
        }

        TrainingDummyElementRuntime.AttackInput starter(
                UUID player, UUID target, String hitId, long now
        ) {
            return new TrainingDummyElementRuntime.AttackInput(
                    hitId, player, target, "starter_sword",
                    TrainingDummyElementRuntime.AttackType.STARTER_SWORD_NORMAL,
                    IceElementEngine.DamageOrigin.NORMAL_ATTACK_DIRECT,
                    STARTER, 100.0, true, true, false, true, now);
        }

        TrainingDummyElementRuntime.AttackInput spin(
                UUID player, UUID target, String hitId, long now
        ) {
            return new TrainingDummyElementRuntime.AttackInput(
                    hitId, player, target, "spin_slash",
                    TrainingDummyElementRuntime.AttackType.SPIN_SLASH,
                    IceElementEngine.DamageOrigin.SKILL_DIRECT,
                    SPIN, 100.0, true, true, false, true, now);
        }
    }

    private static final class FakeBoundary implements TrainingDummyElementBoundary {
        final Set<UUID> live = new LinkedHashSet<>();
        final List<SecondaryDamage> secondary = new ArrayList<>();
        final List<VisualEvent> visuals = new ArrayList<>();
        List<UUID> nearby = List.of();
        Runnable cleanup;
        int schedules;
        boolean cancelled;

        @Override
        public boolean isLiveTrainingDummy(UUID targetId) {
            return live.contains(targetId);
        }

        @Override
        public List<UUID> nearbyTrainingDummies(UUID centerId, double radius, int limit) {
            assert radius == 4.0;
            assert limit == 64;
            return nearby;
        }

        @Override
        public void applySecondaryDamage(SecondaryDamage damage) {
            assert live.contains(damage.targetId());
            secondary.add(damage);
        }

        @Override
        public void publishVisual(VisualEvent event) {
            visuals.add(event);
        }

        @Override
        public Cancellable scheduleCleanup(Runnable task, long periodMillis) {
            assert periodMillis == 1_000L;
            schedules++;
            cleanup = task;
            return new Cancellable() {
                @Override
                public void cancel() {
                    cancelled = true;
                }

                @Override
                public boolean cancelled() {
                    return cancelled;
                }
            };
        }

        void runCleanup() {
            assert cleanup != null;
            cleanup.run();
        }
    }

    private static final class MutableClock extends Clock {
        long millis;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }
}
