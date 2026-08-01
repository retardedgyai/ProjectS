package io.github.gyai.projects.combat.damage;

public enum DamageKind {
    NORMAL_ATTACK(true, 1.0),
    DIRECT_SKILL(true, 1.0),
    DAMAGE_OVER_TIME(false, 0.0),
    REFLECTED(false, 0.0),
    PERCENT_HEALTH(false, 0.0);

    private final boolean criticalAllowed;
    private final double defaultLifeStealEfficiency;

    DamageKind(boolean criticalAllowed, double defaultLifeStealEfficiency) {
        this.criticalAllowed = criticalAllowed;
        this.defaultLifeStealEfficiency = defaultLifeStealEfficiency;
    }

    public boolean criticalAllowed() {
        return criticalAllowed;
    }

    public double defaultLifeStealEfficiency() {
        return defaultLifeStealEfficiency;
    }

    public double lifeStealEfficiency(boolean areaDamage, DamageType damageType) {
        if (damageType == DamageType.TRUE || defaultLifeStealEfficiency == 0.0) {
            return 0.0;
        }
        return areaDamage ? 0.33 : defaultLifeStealEfficiency;
    }
}
