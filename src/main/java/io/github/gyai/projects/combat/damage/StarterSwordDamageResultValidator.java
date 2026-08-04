package io.github.gyai.projects.combat.damage;

/** Validation completed before the authoritative application boundary starts. */
public final class StarterSwordDamageResultValidator {
    private StarterSwordDamageResultValidator() {
    }

    public static boolean isSafe(DamageResult result) {
        return result != null
                && finiteNonNegative(result.resolvedAttackPower())
                && finiteNonNegative(result.baseDamage())
                && finiteNonNegative(result.damageIncreaseMultiplier())
                && finiteNonNegative(result.offenseResolvedDamage())
                && finiteNonNegative(result.criticalMultiplier())
                && finiteNonNegative(result.defenseBeforePenetration())
                && finiteNonNegative(result.effectiveDefense())
                && finiteNonNegative(result.defenseMultiplier())
                && finiteNonNegative(result.reductionMultiplier())
                && finiteNonNegative(result.modeMultiplier())
                && finiteNonNegative(result.damageBeforeShield())
                && finiteNonNegative(result.shieldDamage())
                && finiteNonNegative(result.healthDamage())
                && finiteNonNegative(result.lifeStealHealing())
                && finiteNonNegative(result.finalRoundedDamage());
    }

    private static boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }
}
