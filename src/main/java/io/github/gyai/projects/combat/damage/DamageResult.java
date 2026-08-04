package io.github.gyai.projects.combat.damage;

public record DamageResult(
        double resolvedAttackPower,
        double baseDamage,
        double damageIncreaseMultiplier,
        double offenseResolvedDamage,
        boolean critical,
        double criticalMultiplier,
        double defenseBeforePenetration,
        double effectiveDefense,
        double defenseMultiplier,
        double reductionMultiplier,
        double modeMultiplier,
        double damageBeforeShield,
        double shieldDamage,
        double healthDamage,
        double lifeStealHealing,
        double finalRoundedDamage
) {
}
