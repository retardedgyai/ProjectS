package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.combat.stat.StatCalculator;

import java.util.EnumSet;

public final class DamageSnapshotTest {
    private DamageSnapshotTest() {
    }

    public static void main(String[] args) {
        DamageDefenseSnapshot defense = new DamageDefenseSnapshot(
                300, 600,
                0, 0,
                1.25,
                .20,
                10,
                1_000,
                .75);
        assertClose(300, defense.defenseFor(DamageType.PHYSICAL));
        assertClose(600, defense.defenseFor(DamageType.MAGICAL));
        assertClose(0, defense.defenseFor(DamageType.TRUE));
        assertClose(.75, defense.statusDurationMultiplier());

        AttackMetadata metadata = new AttackMetadata(
                EnumSet.of(
                        AttackTag.NORMAL_ATTACK,
                        AttackTag.MELEE,
                        AttackTag.PHYSICAL),
                ElementProfile.EMPTY);
        double[] reductions = {.50};
        DamageCalculationSnapshot snapshot = new DamageCalculationSnapshot(
                DamageType.PHYSICAL,
                DamageMode.PVE,
                DamageKind.NORMAL_ATTACK,
                metadata,
                100,
                0,
                1,
                0,
                false,
                1,
                new DamageOffenseSnapshot(100, false, 1),
                defense,
                0,
                0,
                StatCalculator.DEFAULT_DEFENSE_CONSTANT,
                reductions,
                1,
                0,
                1,
                0);
        reductions[0] = 0;
        double[] exposed = snapshot.additionalDamageReductions();
        exposed[0] = 0;
        DamageResult result = snapshot.calculate();
        assertClose(100, result.offenseResolvedDamage());
        assertClose(25, result.damageBeforeShield());
        assertClose(10, result.shieldDamage());
        assertClose(15, result.healthDamage());
        assertClose(25, result.finalRoundedDamage());
        assert snapshot.attackMetadata().equals(metadata);
        assertClose(100, snapshot.preCriticalOffenseDamage());

        DamageCalculationSnapshot critical = new DamageCalculationSnapshot(
                DamageType.PHYSICAL,
                DamageMode.PVE,
                DamageKind.NORMAL_ATTACK,
                metadata,
                100, 0, 1, 0,
                true, 1.75,
                new DamageOffenseSnapshot(175, true, 1.75),
                new DamageDefenseSnapshot(
                        0, 0, 0, 0,
                        1, 0, 0, 1_000, 1),
                0, 0,
                StatCalculator.DEFAULT_DEFENSE_CONSTANT,
                new double[0],
                1, 0, 1, 0);
        assertClose(100, critical.preCriticalOffenseDamage());
        assertClose(175, critical.calculate().finalRoundedDamage());

        expectIllegal(() -> new DamageDefenseSnapshot(
                Double.NaN, 0, 0, 0,
                1, 0, 0, 1, 1));
        expectIllegal(() -> new DamageDefenseSnapshot(
                0, 0, -1, 0,
                1, 0, 0, 1, 1));
        expectIllegal(() -> new DamageDefenseSnapshot(
                0, 0, 0, 0,
                Double.POSITIVE_INFINITY, 0, 0, 1, 1));
        expectIllegal(() -> new DamageCalculationSnapshot(
                DamageType.PHYSICAL,
                DamageMode.PVE,
                DamageKind.NORMAL_ATTACK,
                null,
                Double.NaN, 0, 0, 0,
                false, 1,
                new DamageOffenseSnapshot(0, false, 1),
                defense,
                0, 0, 300,
                new double[0],
                1, 0, 0, 0));
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void assertClose(double expected, double actual) {
        if (!Double.isFinite(actual)
                || Math.abs(expected - actual) > .000_001) {
            throw new AssertionError(
                    "Expected " + expected + " but got " + actual);
        }
    }
}
