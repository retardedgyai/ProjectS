package io.github.gyai.projects.combat.element.fire;

import io.github.gyai.projects.combat.element.ElementAttackSchool;
import io.github.gyai.projects.combat.element.ElementTargetCategory;

import java.util.UUID;

public final class FireElementEngineTest {
    private static final UUID PLAYER_A = UUID.fromString(
            "00000000-0000-0000-0000-00000000000a");
    private static final UUID PLAYER_B = UUID.fromString(
            "00000000-0000-0000-0000-00000000000b");
    private static final UUID PLAYER_C = UUID.fromString(
            "00000000-0000-0000-0000-00000000000c");
    private static final FireElementEngine.TargetProfile NORMAL =
            new FireElementEngine.TargetProfile(ElementTargetCategory.NORMAL, 25.0);
    private static final FireElementEngine.TargetProfile BOSS =
            new FireElementEngine.TargetProfile(ElementTargetCategory.BOSS, 100.0);

    private FireElementEngineTest() {
    }

    public static void main(String[] args) {
        fractionalCarryAndCategoryThresholds();
        detonationConsumesSevenAndRetainsContributionProportions();
        oneHitEmitsAtMostOneDetonationAndNeverSpreads();
        decayHonorsFiveSecondHoldThenFractionAndStacks();
        boundedStateAndImmutableSnapshots();
        rejectsInvalidNumbers();
    }

    private static void fractionalCarryAndCategoryThresholds() {
        FireElementEngine engine = engine(8, 8);
        var first = engine.apply(hit("normal", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 20, 40, 0));
        assert first.state().stacks() == 0;
        close(20, first.state().fractionalBurnValue());
        var second = engine.apply(hit("normal", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 10, 40, 1));
        assert second.state().stacks() == 1;
        close(5, second.state().fractionalBurnValue());

        var boss = engine.apply(hit("boss", BOSS, PLAYER_A,
                ElementAttackSchool.MAGICAL, 99.5, 80, 0));
        assert boss.state().stacks() == 0;
        close(99.5, boss.state().fractionalBurnValue());
        boss = engine.apply(hit("boss", BOSS, PLAYER_A,
                ElementAttackSchool.MAGICAL, .5, 80, 1));
        assert boss.state().stacks() == 1;
        close(0, boss.state().fractionalBurnValue());
    }

    private static void detonationConsumesSevenAndRetainsContributionProportions() {
        FireElementEngine engine = engine(8, 8);
        engine.apply(hit("target", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 225, 100, 0));
        var result = engine.apply(hit("target", NORMAL, PLAYER_B,
                ElementAttackSchool.MAGICAL, 25, 50, 1));

        assert result.detonation().isPresent();
        assert result.state().stacks() == 3;
        close(0, result.state().fractionalBurnValue());
        var event = result.detonation().orElseThrow();
        close(95, event.effectiveFireValue());
        close(237.5, event.centerDamage());
        close(142.5, event.nearbyDamage());
        close(213.75, event.physicalBaseDamage());
        close(23.75, event.magicalBaseDamage());
        close(.9, event.contributionShares().get(PLAYER_A).totalFraction());
        close(.1, event.contributionShares().get(PLAYER_B).totalFraction());

        double retainedA = result.state().contributions().get(PLAYER_A).totalBurnValue();
        double retainedB = result.state().contributions().get(PLAYER_B).totalBurnValue();
        close(67.5, retainedA);
        close(7.5, retainedB);
        close(9, retainedA / retainedB);
    }

    private static void oneHitEmitsAtMostOneDetonationAndNeverSpreads() {
        FireElementEngine engine = engine(8, 8);
        var result = engine.apply(hit("heavy", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 10_000, 10, 0));
        assert result.detonation().isPresent();
        assert result.state().stacks() == 3;
        var event = result.detonation().orElseThrow();
        assert !event.spreadsBurn();
        close(4, event.radius());
        close(1, event.centerMultiplier());
        close(.6, event.nearbyMultiplier());
        close(25, event.centerDamage());

        var followup = engine.apply(hit("heavy", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 0, 10, 1));
        assert followup.detonation().isEmpty();
    }

    private static void decayHonorsFiveSecondHoldThenFractionAndStacks() {
        FireElementEngine engine = engine(8, 8);
        engine.apply(hit("decay", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 62.5, 40, 0));
        var held = engine.advanceDecay("decay", 5_000).orElseThrow();
        assert held.stacks() == 2;
        close(12.5, held.fractionalBurnValue());
        var firstFraction = engine.advanceDecay("decay", 6_000).orElseThrow();
        close(6.25, firstFraction.fractionalBurnValue());
        var fractionGone = engine.advanceDecay("decay", 7_000).orElseThrow();
        close(0, fractionGone.fractionalBurnValue());
        assert fractionGone.stacks() == 2;
        assert engine.advanceDecay("decay", 8_999).orElseThrow().stacks() == 2;
        assert engine.advanceDecay("decay", 9_000).orElseThrow().stacks() == 1;
        assert engine.advanceDecay("decay", 11_000).isEmpty();
    }

    private static void boundedStateAndImmutableSnapshots() {
        FireElementEngine engine = engine(1, 2);
        engine.apply(hit("one", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 1, 1, 0));
        engine.apply(hit("one", NORMAL, PLAYER_B,
                ElementAttackSchool.MAGICAL, 1, 1, 1));
        var contributorRejected = engine.apply(hit("one", NORMAL, PLAYER_C,
                ElementAttackSchool.PHYSICAL, 1, 1, 2));
        assert !contributorRejected.accepted();
        assert contributorRejected.capacityReason()
                == FireElementEngine.CapacityReason.CONTRIBUTORS;
        var targetRejected = engine.apply(hit("two", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 1, 1, 0));
        assert !targetRejected.accepted();
        assert targetRejected.capacityReason()
                == FireElementEngine.CapacityReason.TARGETS;

        expectUnsupported(() -> engine.snapshot().clear());
        expectUnsupported(() -> contributorRejected.state().contributions().clear());
        assert engine.removeInactiveBefore(3) == 1;
        assert engine.snapshot().isEmpty();
    }

    private static void rejectsInvalidNumbers() {
        expectIllegal(() -> new FireElementEngine.TargetProfile(
                ElementTargetCategory.NORMAL, Double.NaN));
        expectIllegal(() -> hit("x", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, -1, 1, 0));
        expectIllegal(() -> hit("x", NORMAL, PLAYER_A,
                ElementAttackSchool.PHYSICAL, 1, Double.POSITIVE_INFINITY, 0));
    }

    private static FireElementEngine engine(int targets, int contributors) {
        return new FireElementEngine(
                FireElementEngine.Policy.waveOne(targets, contributors));
    }

    private static FireElementEngine.Hit hit(
            String target,
            FireElementEngine.TargetProfile profile,
            UUID player,
            ElementAttackSchool school,
            double burn,
            double fire,
            long time
    ) {
        return new FireElementEngine.Hit(
                target, profile, player, school, burn, fire, time);
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
