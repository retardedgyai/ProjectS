package io.github.gyai.projects.combat.element.ice;

import io.github.gyai.projects.combat.element.ElementAttackSchool;
import io.github.gyai.projects.combat.element.ElementTargetCategory;

import java.util.UUID;

public final class IceElementEngineTest {
    private static final UUID PLAYER_A = UUID.fromString(
            "00000000-0000-0000-0000-00000000000a");
    private static final UUID PLAYER_B = UUID.fromString(
            "00000000-0000-0000-0000-00000000000b");
    private static final UUID PLAYER_C = UUID.fromString(
            "00000000-0000-0000-0000-00000000000c");
    private static final IceElementEngine.TargetProfile NORMAL = profile(
            ElementTargetCategory.NORMAL);

    private IceElementEngineTest() {
    }

    public static void main(String[] args) {
        sharedGaugeUsesInjectedStageBoundaries();
        freezingHitCannotShatterAndNextValidHitShattersOnce();
        shatterUsesPreCriticalImpactCoreAndProportionalResidual();
        immunityAccumulatesWithoutAutomaticFreeze();
        frozenDamageBonusExcludesPeriodicAndAutomaticDamage();
        categoryImmunityDurationsAreFixed();
        boundedStateAndImmutableContributions();
        rejectsInvalidNumbers();
    }

    private static void sharedGaugeUsesInjectedStageBoundaries() {
        IceElementEngine engine = engine(8, 8);
        var state = engine.apply(hit("stages", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 24, 40, false, 10, 0)).state();
        assert state.stage() == IceElementEngine.Stage.NONE;
        state = engine.apply(hit("stages", NORMAL, PLAYER_B,
                ElementAttackSchool.MAGICAL, 1, 60, false, 10, 1)).state();
        assert state.stage() == IceElementEngine.Stage.COLD_I;
        state = engine.apply(hit("stages", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 50, 40, false, 10, 2)).state();
        assert state.stage() == IceElementEngine.Stage.COLD_II;
        var freeze = engine.apply(hit("stages", NORMAL, PLAYER_B,
                ElementAttackSchool.MAGICAL, 25, 60, false, 10, 3));
        assert freeze.frozeNow();
        assert freeze.state().stage() == IceElementEngine.Stage.FROZEN;
        close(100, freeze.state().coldValue());
        assert freeze.state().contributions().size() == 2;
    }

    private static void freezingHitCannotShatterAndNextValidHitShattersOnce() {
        IceElementEngine engine = engine(8, 8);
        var freeze = engine.apply(hit("once", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 100, 80, true, 100, 0));
        assert freeze.frozeNow();
        assert freeze.shatter().isEmpty();

        var shatter = engine.apply(hit("once", NORMAL, PLAYER_B,
                ElementAttackSchool.PHYSICAL, 0, 0, true, 100, 1));
        assert shatter.shatter().isPresent();
        assert !shatter.state().frozen();
        close(1.08, shatter.directDamageMultiplier());

        var second = engine.apply(hit("once", NORMAL, PLAYER_B,
                ElementAttackSchool.PHYSICAL, 0, 0, true, 100, 2));
        assert second.shatter().isEmpty();
    }

    private static void shatterUsesPreCriticalImpactCoreAndProportionalResidual() {
        IceElementEngine engine = engine(8, 8);
        engine.apply(hit("damage", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 25, 40, false, 1, 0));
        engine.apply(hit("damage", NORMAL, PLAYER_B,
                ElementAttackSchool.MAGICAL, 75, 80, false, 1, 1));
        var result = engine.apply(hit("damage", NORMAL, PLAYER_C,
                ElementAttackSchool.PHYSICAL, 0, 0, true, 100, 2));
        var event = result.shatter().orElseThrow();

        close(135, event.impactDamage());
        close(37.8, event.coreDamage());
        close(144.45, event.physicalAdditionalDamage());
        close(28.35, event.magicalAdditionalDamage());
        close(172.8, event.totalAdditionalDamage());
        assert !event.criticalAllowed();
        assert event.singleTarget();
        close(.25, event.coreContributionShares().get(PLAYER_A).totalFraction());
        close(.75, event.coreContributionShares().get(PLAYER_B).totalFraction());

        close(40, result.state().coldValue());
        close(10, result.state().contributions().get(PLAYER_A).totalColdValue());
        close(30, result.state().contributions().get(PLAYER_B).totalColdValue());
        assert result.state().iceCore().contributionShares().isEmpty();
    }

    private static void immunityAccumulatesWithoutAutomaticFreeze() {
        IceElementEngine engine = engine(8, 8);
        engine.apply(hit("immune", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 100, 50, false, 1, 0));
        var shattered = engine.apply(hit("immune", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 0, 0, true, 10, 100));
        assert shattered.state().refreezeImmuneUntilMillis() == 3_100;
        var accumulated = engine.apply(hit("immune", NORMAL, PLAYER_B,
                ElementAttackSchool.MAGICAL, 60, 70, false, 1, 1_000));
        close(100, accumulated.state().coldValue());
        assert !accumulated.state().frozen();

        var afterExpiry = engine.state("immune", 4_000).orElseThrow();
        assert !afterExpiry.frozen();
        assert afterExpiry.stage() == IceElementEngine.Stage.COLD_II;
        var nextValidHit = engine.apply(hit("immune", NORMAL, PLAYER_B,
                ElementAttackSchool.MAGICAL, 0, 70, false, 1, 4_001));
        assert nextValidHit.frozeNow();
        assert nextValidHit.state().frozen();
    }

    private static void frozenDamageBonusExcludesPeriodicAndAutomaticDamage() {
        IceElementEngine engine = engine(8, 8);
        close(1.08, engine.damageMultiplier(
                true, IceElementEngine.DamageOrigin.NORMAL_ATTACK_DIRECT));
        close(1.08, engine.damageMultiplier(
                true, IceElementEngine.DamageOrigin.SKILL_DIRECT));
        close(1.08, engine.damageMultiplier(
                true, IceElementEngine.DamageOrigin.SHATTER_ADDITIONAL));
        close(1, engine.damageMultiplier(
                true, IceElementEngine.DamageOrigin.DAMAGE_OVER_TIME));
        close(1, engine.damageMultiplier(
                true, IceElementEngine.DamageOrigin.PERIODIC));
        close(1, engine.damageMultiplier(
                true, IceElementEngine.DamageOrigin.AUTOMATIC_SECONDARY));
        close(1, engine.damageMultiplier(
                true, IceElementEngine.DamageOrigin.REFLECTED));

        var ignored = engine.apply(new IceElementEngine.Hit(
                "ignored", NORMAL, PLAYER_A, ElementAttackSchool.MAGICAL,
                IceElementEngine.DamageOrigin.DAMAGE_OVER_TIME,
                100, 100, true, 100, 0));
        close(0, ignored.state().coldValue());
        assert !ignored.state().frozen();
        assert ignored.shatter().isEmpty();
    }

    private static void categoryImmunityDurationsAreFixed() {
        var policy = IceElementEngine.Policy.waveOne(8, 8);
        assert policy.refreezeImmunityMillis(ElementTargetCategory.NORMAL) == 3_000;
        assert policy.refreezeImmunityMillis(ElementTargetCategory.ELITE) == 4_000;
        assert policy.refreezeImmunityMillis(ElementTargetCategory.MINIBOSS) == 5_000;
        assert policy.refreezeImmunityMillis(ElementTargetCategory.BOSS) == 8_000;
    }

    private static void boundedStateAndImmutableContributions() {
        IceElementEngine engine = engine(1, 2);
        engine.apply(hit("one", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 1, 1, false, 1, 0));
        engine.apply(hit("one", NORMAL, PLAYER_B,
                ElementAttackSchool.MAGICAL, 1, 1, false, 1, 1));
        var contributorRejected = engine.apply(hit("one", NORMAL, PLAYER_C,
                ElementAttackSchool.PHYSICAL, 1, 1, false, 1, 2));
        assert !contributorRejected.accepted();
        assert contributorRejected.capacityReason()
                == IceElementEngine.CapacityReason.CONTRIBUTORS;
        var targetRejected = engine.apply(hit("two", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 1, 1, false, 1, 0));
        assert !targetRejected.accepted();
        assert targetRejected.capacityReason()
                == IceElementEngine.CapacityReason.TARGETS;
        expectUnsupported(() -> engine.snapshot().clear());
        expectUnsupported(() -> contributorRejected.state().contributions().clear());
        assert engine.removeInactiveBefore(3) == 1;
        assert engine.snapshot().isEmpty();
    }

    private static void rejectsInvalidNumbers() {
        expectIllegal(() -> new IceElementEngine.TargetProfile(
                ElementTargetCategory.NORMAL, Double.NaN, .25, .75));
        expectIllegal(() -> new IceElementEngine.TargetProfile(
                ElementTargetCategory.NORMAL, 100, .8, .75));
        expectIllegal(() -> hit("x", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, -1, 1, false, 1, 0));
        expectIllegal(() -> hit("x", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 1, Double.POSITIVE_INFINITY,
                false, 1, 0));
    }

    private static IceElementEngine engine(int targets, int contributors) {
        return new IceElementEngine(
                IceElementEngine.Policy.waveOne(targets, contributors));
    }

    private static IceElementEngine.TargetProfile profile(
            ElementTargetCategory category
    ) {
        // Stage boundaries are fixture inputs, not production defaults.
        return new IceElementEngine.TargetProfile(category, 100, .25, .75);
    }

    private static IceElementEngine.Hit hit(
            String target,
            IceElementEngine.TargetProfile profile,
            UUID player,
            ElementAttackSchool school,
            double cold,
            double ice,
            boolean shatter,
            double preCriticalDamage,
            long time
    ) {
        return new IceElementEngine.Hit(
                target, profile, player, school,
                IceElementEngine.DamageOrigin.SKILL_DIRECT,
                cold, ice, shatter, preCriticalDamage, time);
    }

    private static void close(double expected, double actual) {
        assert Math.abs(expected - actual) <= 1.0e-8
                : "expected=" + expected + " actual=" + actual;
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }
}
