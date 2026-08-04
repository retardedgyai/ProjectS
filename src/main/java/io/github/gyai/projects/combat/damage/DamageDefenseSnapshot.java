package io.github.gyai.projects.combat.damage;

/** Immutable target-side values captured before pure damage calculation. */
public record DamageDefenseSnapshot(
        double physicalDefense,
        double magicalDefense,
        double physicalDefenseReduction,
        double magicalDefenseReduction,
        double incomingDamageMultiplier,
        double damageReduction,
        double shieldAmount,
        double healthAmount,
        double statusDurationMultiplier
) {
    public DamageDefenseSnapshot {
        requireNonNegativeFinite("physicalDefense", physicalDefense);
        requireNonNegativeFinite("magicalDefense", magicalDefense);
        requireRate("physicalDefenseReduction", physicalDefenseReduction);
        requireRate("magicalDefenseReduction", magicalDefenseReduction);
        requireNonNegativeFinite(
                "incomingDamageMultiplier", incomingDamageMultiplier);
        requireRate("damageReduction", damageReduction);
        requireNonNegativeFinite("shieldAmount", shieldAmount);
        requireNonNegativeFinite("healthAmount", healthAmount);
        requireNonNegativeFinite(
                "statusDurationMultiplier", statusDurationMultiplier);
    }

    public double defenseFor(DamageType type) {
        return switch (requireType(type)) {
            case PHYSICAL -> physicalDefense;
            case MAGICAL -> magicalDefense;
            case TRUE -> 0.0;
        };
    }

    public double defenseReductionFor(DamageType type) {
        return switch (requireType(type)) {
            case PHYSICAL -> physicalDefenseReduction;
            case MAGICAL -> magicalDefenseReduction;
            case TRUE -> 0.0;
        };
    }

    private static DamageType requireType(DamageType type) {
        if (type == null) {
            throw new IllegalArgumentException("damage type must not be null");
        }
        return type;
    }

    private static void requireRate(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and between zero and one");
        }
    }

    private static void requireNonNegativeFinite(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and non-negative");
        }
    }
}
