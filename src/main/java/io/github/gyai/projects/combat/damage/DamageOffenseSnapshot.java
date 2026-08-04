package io.github.gyai.projects.combat.damage;

public record DamageOffenseSnapshot(
        double damage,
        boolean critical,
        double criticalMultiplier
) {
    public DamageOffenseSnapshot {
        if (!Double.isFinite(damage) || damage < 0.0) {
            throw new IllegalArgumentException("damage must be finite and non-negative");
        }
        if (!Double.isFinite(criticalMultiplier) || criticalMultiplier < 1.0) {
            throw new IllegalArgumentException(
                    "criticalMultiplier must be finite and at least 1");
        }
    }
}
