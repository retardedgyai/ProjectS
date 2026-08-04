package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.combat.stat.StatCalculator;

public final class DamageEventApplicationPolicy {
    private DamageEventApplicationPolicy() {
    }

    public static boolean replacesModifier(String modifierName) {
        if (modifierName == null) return false;
        return switch (modifierName) {
            case "HARD_HAT", "BLOCKING", "ARMOR", "RESISTANCE", "MAGIC" -> true;
            default -> false;
        };
    }

    public static boolean allowsPveTarget(boolean playerTarget) {
        return !playerTarget;
    }

    public static double absorptionModifier(
            double damageBeforeAbsorption,
            double absorptionAmount
    ) {
        return -Math.min(
                StatCalculator.nonNegative(damageBeforeAbsorption),
                StatCalculator.nonNegative(absorptionAmount));
    }

    public static double damageAfterAbsorption(
            double damageBeforeAbsorption,
            double absorptionAmount
    ) {
        return StatCalculator.nonNegative(damageBeforeAbsorption)
                + absorptionModifier(damageBeforeAbsorption, absorptionAmount);
    }
}
