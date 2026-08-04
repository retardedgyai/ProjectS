package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.combat.stat.StatCalculator;

public final class DamageCalculator {
    private DamageCalculator() {
    }

    public static DamageResult calculate(Input input) {
        double attackPower = StatCalculator.nonNegative(input.attackPower());
        double baseDamage = StatCalculator.baseDamage(
                input.fixedDamage(), attackPower, input.coefficient());
        double outgoingMultiplier = StatCalculator.nonNegative(
                StatCalculator.saturatedAdd(1.0, input.damageIncreasePercent()));
        double takenMultiplier = StatCalculator.nonNegative(
                StatCalculator.saturatedAdd(1.0, input.damageTakenIncreasePercent()));
        double damageIncreaseMultiplier = StatCalculator.saturatedMultiply(
                outgoingMultiplier, takenMultiplier);
        double criticalMultiplier = input.critical()
                ? Math.max(1.0, StatCalculator.finiteOrZero(input.criticalMultiplier()))
                : 1.0;

        double modeMultiplier = StatCalculator.nonNegative(input.modeMultiplier());
        double offenseResolvedDamage = StatCalculator.saturatedMultiply(
                baseDamage, outgoingMultiplier);
        offenseResolvedDamage = StatCalculator.saturatedMultiply(
                offenseResolvedDamage, criticalMultiplier);
        offenseResolvedDamage = StatCalculator.saturatedMultiply(
                offenseResolvedDamage, modeMultiplier);
        return finish(
                input.damageType(), input.mode(), input.damageKind(),
                attackPower, baseDamage, damageIncreaseMultiplier,
                new DamageOffenseSnapshot(
                        offenseResolvedDamage, input.critical(), criticalMultiplier),
                takenMultiplier, input.defense(),
                input.defenseReductionPercent(), input.penetrationPercent(),
                input.flatPenetration(), input.defenseConstant(),
                input.damageReductions(), input.shield(), input.health(),
                input.lifeStealPercent(), input.lifeStealEfficiency(),
                input.healingReductionPercent(), modeMultiplier);
    }

    public static DamageResult calculateOffenseResolved(OffenseInput input) {
        double takenMultiplier = StatCalculator.nonNegative(
                StatCalculator.saturatedAdd(1.0, input.damageTakenIncreasePercent()));
        return finish(
                input.damageType(), input.mode(), input.damageKind(),
                0.0, input.offenseSnapshot().damage(), takenMultiplier,
                input.offenseSnapshot(), takenMultiplier, input.defense(),
                input.defenseReductionPercent(), input.penetrationPercent(),
                input.flatPenetration(), input.defenseConstant(),
                input.damageReductions(), input.shield(), input.health(),
                input.lifeStealPercent(), input.lifeStealEfficiency(),
                input.healingReductionPercent(), 1.0);
    }

    private static DamageResult finish(
            DamageType damageType,
            DamageMode mode,
            DamageKind damageKind,
            double attackPower,
            double baseDamage,
            double damageIncreaseMultiplier,
            DamageOffenseSnapshot offenseSnapshot,
            double takenMultiplier,
            double defense,
            double defenseReductionPercent,
            double penetrationPercent,
            double flatPenetration,
            double defenseConstant,
            double[] damageReductions,
            double shield,
            double health,
            double lifeStealPercent,
            double lifeStealEfficiency,
            double healingReductionPercent,
            double modeMultiplier
    ) {
        double defenseBeforePenetration = damageType == DamageType.TRUE
                ? 0.0 : StatCalculator.nonNegative(defense);
        double effectiveDefense = damageType == DamageType.TRUE
                ? 0.0 : StatCalculator.effectiveDefense(
                defenseBeforePenetration,
                defenseReductionPercent,
                penetrationPercent,
                flatPenetration);
        double defenseMultiplier = damageType == DamageType.TRUE
                ? 1.0 : StatCalculator.defenseMultiplier(
                effectiveDefense, defenseConstant);

        double uncappedReduction = 1.0;
        if (damageReductions != null) {
            for (double reduction : damageReductions) {
                uncappedReduction = StatCalculator.saturatedMultiply(
                        uncappedReduction,
                        1.0 - StatCalculator.clamp01(reduction));
            }
        }
        double reductionCap = mode == null
                ? DamageMode.PVE.reductionCap() : mode.reductionCap();
        double reductionMultiplier = Math.max(1.0 - reductionCap, uncappedReduction);
        double damageBeforeShield = StatCalculator.saturatedMultiply(
                offenseSnapshot.damage(), takenMultiplier);
        damageBeforeShield = StatCalculator.saturatedMultiply(
                damageBeforeShield, defenseMultiplier);
        damageBeforeShield = StatCalculator.saturatedMultiply(
                damageBeforeShield, reductionMultiplier);
        double rounded = roundDamage(damageBeforeShield);
        double shieldDamage = StatCalculator.shieldDamage(rounded, shield);
        double healthDamage = StatCalculator.actualHealthDamage(
                rounded, shield, health);
        double effectiveLifeStealEfficiency = effectiveLifeStealEfficiency(
                damageType, damageKind, lifeStealEfficiency);
        double lifeSteal = StatCalculator.lifeSteal(
                healthDamage, lifeStealPercent,
                effectiveLifeStealEfficiency, healingReductionPercent);
        return new DamageResult(
                attackPower, baseDamage, damageIncreaseMultiplier,
                offenseSnapshot.damage(), offenseSnapshot.critical(),
                offenseSnapshot.criticalMultiplier(),
                defenseBeforePenetration, effectiveDefense, defenseMultiplier,
                reductionMultiplier, modeMultiplier, damageBeforeShield,
                shieldDamage, healthDamage, lifeSteal, rounded);
    }

    private static double roundDamage(double value) {
        double safe = StatCalculator.nonNegative(value);
        return Math.round(safe * 1_000.0) / 1_000.0;
    }

    static double effectiveLifeStealEfficiency(
            DamageType damageType,
            DamageKind damageKind,
            double requestedEfficiency
    ) {
        if (damageType == DamageType.TRUE
                || damageKind == DamageKind.DAMAGE_OVER_TIME
                || damageKind == DamageKind.REFLECTED
                || damageKind == DamageKind.PERCENT_HEALTH) {
            return 0.0;
        }
        return requestedEfficiency;
    }

    public record Input(
            DamageType damageType,
            DamageMode mode,
            DamageKind damageKind,
            double attackPower,
            double fixedDamage,
            double coefficient,
            double damageIncreasePercent,
            double damageTakenIncreasePercent,
            boolean critical,
            double criticalMultiplier,
            double defense,
            double defenseReductionPercent,
            double penetrationPercent,
            double flatPenetration,
            double defenseConstant,
            double[] damageReductions,
            double modeMultiplier,
            double shield,
            double health,
            double lifeStealPercent,
            double lifeStealEfficiency,
            double healingReductionPercent
    ) {
        public Input {
            damageType = damageType == null ? DamageType.PHYSICAL : damageType;
            mode = mode == null ? DamageMode.PVE : mode;
            damageKind = damageKind == null ? DamageKind.DIRECT_SKILL : damageKind;
            damageReductions = damageReductions == null
                    ? new double[0] : damageReductions.clone();
        }

        @Override
        public double[] damageReductions() {
            return damageReductions.clone();
        }
    }

    public record OffenseInput(
            DamageType damageType,
            DamageMode mode,
            DamageKind damageKind,
            DamageOffenseSnapshot offenseSnapshot,
            double damageTakenIncreasePercent,
            double defense,
            double defenseReductionPercent,
            double penetrationPercent,
            double flatPenetration,
            double defenseConstant,
            double[] damageReductions,
            double shield,
            double health,
            double lifeStealPercent,
            double lifeStealEfficiency,
            double healingReductionPercent
    ) {
        public OffenseInput {
            damageType = damageType == null ? DamageType.PHYSICAL : damageType;
            mode = mode == null ? DamageMode.PVE : mode;
            damageKind = damageKind == null ? DamageKind.DIRECT_SKILL : damageKind;
            if (offenseSnapshot == null) {
                throw new IllegalArgumentException("offenseSnapshot must not be null");
            }
            damageReductions = damageReductions == null
                    ? new double[0] : damageReductions.clone();
        }

        @Override
        public double[] damageReductions() {
            return damageReductions.clone();
        }
    }
}
