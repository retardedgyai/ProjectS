package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.combat.stat.StatCalculator;

import java.util.Objects;

/** Complete immutable input captured for a pure shadow calculation. */
public record DamageCalculationSnapshot(
        DamageType damageType,
        DamageMode mode,
        DamageKind damageKind,
        AttackMetadata attackMetadata,
        double attackPower,
        double fixedDamage,
        double coefficient,
        double damageIncreasePercent,
        boolean critical,
        double criticalMultiplier,
        DamageOffenseSnapshot offenseSnapshot,
        DamageDefenseSnapshot defenseSnapshot,
        double penetrationPercent,
        double flatPenetration,
        double defenseConstant,
        double[] additionalDamageReductions,
        double modeMultiplier,
        double lifeStealPercent,
        double lifeStealEfficiency,
        double healingReductionPercent
) {
    public DamageCalculationSnapshot {
        damageType = Objects.requireNonNull(damageType, "damageType");
        mode = Objects.requireNonNull(mode, "mode");
        damageKind = Objects.requireNonNull(damageKind, "damageKind");
        attackMetadata = attackMetadata == null
                ? AttackMetadata.EMPTY : attackMetadata;
        offenseSnapshot = Objects.requireNonNull(
                offenseSnapshot, "offenseSnapshot");
        defenseSnapshot = Objects.requireNonNull(
                defenseSnapshot, "defenseSnapshot");
        requireFinite(attackPower, "attackPower");
        requireFinite(fixedDamage, "fixedDamage");
        requireFinite(coefficient, "coefficient");
        requireFinite(damageIncreasePercent, "damageIncreasePercent");
        requireFinite(criticalMultiplier, "criticalMultiplier");
        requireFinite(penetrationPercent, "penetrationPercent");
        requireFinite(flatPenetration, "flatPenetration");
        requireFinite(defenseConstant, "defenseConstant");
        requireFinite(modeMultiplier, "modeMultiplier");
        requireFinite(lifeStealPercent, "lifeStealPercent");
        requireFinite(lifeStealEfficiency, "lifeStealEfficiency");
        requireFinite(healingReductionPercent, "healingReductionPercent");
        additionalDamageReductions = additionalDamageReductions == null
                ? new double[0] : additionalDamageReductions.clone();
        for (double reduction : additionalDamageReductions) {
            requireFinite(reduction, "additionalDamageReduction");
        }
    }

    @Override
    public double[] additionalDamageReductions() {
        return additionalDamageReductions.clone();
    }

    public DamageResult calculate() {
        return DamageCalculator.calculate(new DamageCalculator.Input(
                damageType,
                mode,
                damageKind,
                attackPower,
                fixedDamage,
                coefficient,
                damageIncreasePercent,
                defenseSnapshot.incomingDamageMultiplier() - 1.0,
                critical,
                criticalMultiplier,
                defenseSnapshot.defenseFor(damageType),
                defenseSnapshot.defenseReductionFor(damageType),
                penetrationPercent,
                flatPenetration,
                defenseConstant,
                combinedDamageReductions(),
                modeMultiplier,
                defenseSnapshot.shieldAmount(),
                defenseSnapshot.healthAmount(),
                lifeStealPercent,
                lifeStealEfficiency,
                healingReductionPercent));
    }

    public double preCriticalOffenseDamage() {
        double multiplier = critical
                ? Math.max(1.0, StatCalculator.finiteOrZero(criticalMultiplier))
                : 1.0;
        return offenseSnapshot.damage() / multiplier;
    }

    private double[] combinedDamageReductions() {
        double[] combined = new double[additionalDamageReductions.length + 1];
        System.arraycopy(
                additionalDamageReductions, 0,
                combined, 0,
                additionalDamageReductions.length);
        combined[combined.length - 1] = defenseSnapshot.damageReduction();
        return combined;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
