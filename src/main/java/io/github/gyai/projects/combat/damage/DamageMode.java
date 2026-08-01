package io.github.gyai.projects.combat.damage;

public enum DamageMode {
    PVE(1.75, 0.80),
    PVP(1.50, 0.75);

    private final double baseCriticalMultiplier;
    private final double reductionCap;

    DamageMode(double baseCriticalMultiplier, double reductionCap) {
        this.baseCriticalMultiplier = baseCriticalMultiplier;
        this.reductionCap = reductionCap;
    }

    public double baseCriticalMultiplier() {
        return baseCriticalMultiplier;
    }

    public double reductionCap() {
        return reductionCap;
    }
}
