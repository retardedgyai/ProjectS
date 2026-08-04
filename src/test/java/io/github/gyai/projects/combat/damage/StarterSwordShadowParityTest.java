package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.combat.stat.StatCalculator;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class StarterSwordShadowParityTest {
    private StarterSwordShadowParityTest() {
    }

    public static void main(String[] args) {
        AttackMetadata swordMetadata = new AttackMetadata(
                Set.of(
                        AttackTag.NORMAL_ATTACK,
                        AttackTag.MELEE,
                        AttackTag.PHYSICAL),
                ElementProfile.EMPTY);
        DamageCalculationSnapshot snapshot = snapshot(swordMetadata, 100);
        DamageResult legacy = snapshot.calculate();

        AtomicInteger disabledCalculations = new AtomicInteger();
        DamageShadowEvaluator disabled = new DamageShadowEvaluator(false);
        assert disabled.evaluateStarterSword(legacy, () -> {
            disabledCalculations.incrementAndGet();
            return snapshot;
        }).isEmpty();
        assert disabledCalculations.get() == 0;
        assert disabled.evaluationCount() == 0;

        AtomicInteger shadowCalculations = new AtomicInteger();
        AtomicInteger applications = new AtomicInteger();
        DamageShadowEvaluator enabled = new DamageShadowEvaluator(true);
        DamageShadowComparison parity = enabled.evaluateStarterSword(
                legacy,
                () -> {
                    shadowCalculations.incrementAndGet();
                    return snapshot;
                }).orElseThrow();
        assert parity.matches();
        assert shadowCalculations.get() == 1;
        assert enabled.evaluationCount() == 1;
        // The evaluator only observes results and has no application callback.
        assert applications.get() == 0;

        assert parity.snapshot().damageType() == DamageType.PHYSICAL;
        assert parity.snapshot().damageKind() == DamageKind.NORMAL_ATTACK;
        assert parity.snapshot().mode() == DamageMode.PVE;
        assertClose(.25, parity.snapshot().penetrationPercent());
        assertClose(10, parity.snapshot().flatPenetration());
        assert parity.snapshot().attackMetadata().tags().containsAll(Set.of(
                AttackTag.NORMAL_ATTACK,
                AttackTag.MELEE,
                AttackTag.PHYSICAL));
        assert parity.snapshot().attackMetadata().elements()
                .equals(ElementProfile.EMPTY);

        DamageShadowComparison mismatch =
                DamageShadowComparator.compareStarterSword(
                        legacy,
                        snapshot(swordMetadata, 101));
        assert !mismatch.matches();
        assert mismatch.numericDifferences().containsKey(
                "preCriticalOffenseDamage");
        assert mismatch.numericDifferences().containsKey(
                "finalRoundedDamage");

        AttackMetadata wrongMetadata = new AttackMetadata(
                Set.of(AttackTag.SKILL), ElementProfile.EMPTY);
        DamageShadowComparison contextMismatch =
                DamageShadowComparator.compareStarterSword(
                        snapshot(wrongMetadata, 100).calculate(),
                        snapshot(wrongMetadata, 100));
        assert contextMismatch.contextDifferences().contains("attackTags");
    }

    private static DamageCalculationSnapshot snapshot(
            AttackMetadata metadata,
            double fixedDamage
    ) {
        double criticalMultiplier = DamageMode.PVE.baseCriticalMultiplier();
        double offenseDamage = fixedDamage * criticalMultiplier;
        return new DamageCalculationSnapshot(
                DamageType.PHYSICAL,
                DamageMode.PVE,
                DamageKind.NORMAL_ATTACK,
                metadata,
                0,
                fixedDamage,
                0,
                .10,
                true,
                criticalMultiplier,
                new DamageOffenseSnapshot(
                        offenseDamage * 1.10,
                        true,
                        criticalMultiplier),
                new DamageDefenseSnapshot(
                        300,
                        600,
                        .10,
                        0,
                        1.20,
                        .15,
                        20,
                        1_000,
                        1),
                .25,
                10,
                StatCalculator.DEFAULT_DEFENSE_CONSTANT,
                new double[]{.05},
                1,
                .10,
                1,
                0);
    }

    private static void assertClose(double expected, double actual) {
        if (!Double.isFinite(actual)
                || Math.abs(expected - actual) > .000_001) {
            throw new AssertionError(
                    "Expected " + expected + " but got " + actual);
        }
    }
}
