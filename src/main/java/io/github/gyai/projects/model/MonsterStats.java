package io.github.gyai.projects.model;

public final class MonsterStats {
    private final double maxHealth;
    private final double attackDamage;
    private final double movementSpeed;
    private final double knockbackResistance;
    private final double followRange;
    private final double scale;

    public MonsterStats(
            double maxHealth,
            double attackDamage,
            double movementSpeed,
            double knockbackResistance,
            double followRange,
            double scale
    ) {
        this.maxHealth = requirePositive("maxHealth", maxHealth);
        this.attackDamage = requireNonNegative("attackDamage", attackDamage);
        this.movementSpeed = requirePositive("movementSpeed", movementSpeed);
        this.knockbackResistance = requireNonNegative(
                "knockbackResistance", knockbackResistance);
        this.followRange = requirePositive("followRange", followRange);
        this.scale = requirePositive("scale", scale);
    }

    public double maxHealth() {
        return maxHealth;
    }

    public double attackDamage() {
        return attackDamage;
    }

    public double movementSpeed() {
        return movementSpeed;
    }

    public double knockbackResistance() {
        return knockbackResistance;
    }

    public double followRange() {
        return followRange;
    }

    public double scale() {
        return scale;
    }

    private static double requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
        return value;
    }

    private static double requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }
}
