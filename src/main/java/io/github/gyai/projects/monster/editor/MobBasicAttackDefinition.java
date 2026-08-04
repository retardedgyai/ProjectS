package io.github.gyai.projects.monster.editor;

import io.github.gyai.projects.combat.damage.DamageType;

public record MobBasicAttackDefinition(
        DamageType damageType,
        double fixedDamage,
        double coefficient,
        double intervalSeconds,
        double range,
        double knockback,
        boolean criticalAllowed
) {
    public static MobBasicAttackDefinition defaults() {
        return new MobBasicAttackDefinition(
                DamageType.PHYSICAL, 0, 1, 1.2, 2.2, .2, true);
    }
}
