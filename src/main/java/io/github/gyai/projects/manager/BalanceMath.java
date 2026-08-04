package io.github.gyai.projects.manager;

public final class BalanceMath {
    private BalanceMath() {
    }

    public static double attackPower(
            double globalBaseAttackPower,
            double perItemAttackPowerBonus,
            double enhancementMultiplier
    ) {
        return Math.max(0.0,
                globalBaseAttackPower + perItemAttackPowerBonus)
                * enhancementMultiplier;
    }

    public static double attackSpeed(
            double globalBaseAttackSpeedBonus,
            double enhancementAttackSpeedBonus,
            double perItemAttackSpeedBonus
    ) {
        return globalBaseAttackSpeedBonus
                + enhancementAttackSpeedBonus
                + perItemAttackSpeedBonus;
    }

    public static double typedWeaponAttackPower(
            double typedBaseAttackPower,
            double tunedPrimaryAttackPower
    ) {
        if (!Double.isFinite(typedBaseAttackPower)
                || !Double.isFinite(tunedPrimaryAttackPower)
                || typedBaseAttackPower <= 0.0) {
            return 0.0;
        }
        return Math.max(0.0, tunedPrimaryAttackPower);
    }

    public static double skillDamage(
            double baseDamage,
            double attackPower,
            double attackPowerScaling
    ) {
        return baseDamage + attackPower * attackPowerScaling;
    }

    public static boolean finiteInRange(
            double value,
            double minimum,
            double maximum
    ) {
        return Double.isFinite(value)
                && value >= minimum
                && value <= maximum;
    }

    public static boolean revisionMatches(long expected, long current) {
        return expected == current;
    }
}
