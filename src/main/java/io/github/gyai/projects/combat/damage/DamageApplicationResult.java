package io.github.gyai.projects.combat.damage;

public record DamageApplicationResult(
        DamageResult calculation,
        boolean attempted,
        double shieldDamage,
        double healthDamage,
        double lifeStealHealing
) {
}
