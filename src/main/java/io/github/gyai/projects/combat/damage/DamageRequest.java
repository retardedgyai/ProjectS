package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.combat.stat.StatCalculator;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

public final class DamageRequest {
    private final Player attacker;
    private final LivingEntity target;
    private final String skillId;
    private final UUID castId;
    private final DamageType damageType;
    private final DamageKind damageKind;
    private final double fixedDamage;
    private final double coefficient;
    private final DamageMode mode;
    private final boolean criticalAllowed;
    private final double lifeStealEfficiency;
    private final boolean areaDamage;
    private final double defenseReductionPercent;
    private final double damageTakenIncreasePercent;
    private final double healingReductionPercent;
    private final double pveMultiplier;
    private final double pvpMultiplier;
    private final double iceDirectDamageMultiplier;
    private final double[] additionalDamageReductions;
    private final DamageOffenseSnapshot offenseSnapshot;
    private final AttackMetadata attackMetadata;

    private DamageRequest(Builder builder) {
        attacker = Objects.requireNonNull(builder.attacker, "attacker");
        target = Objects.requireNonNull(builder.target, "target");
        skillId = builder.skillId;
        castId = builder.castId == null ? UUID.randomUUID() : builder.castId;
        damageType = Objects.requireNonNull(builder.damageType, "damageType");
        damageKind = Objects.requireNonNull(builder.damageKind, "damageKind");
        fixedDamage = finite(builder.fixedDamage, "fixedDamage");
        coefficient = finite(builder.coefficient, "coefficient");
        mode = Objects.requireNonNull(builder.mode, "mode");
        criticalAllowed = builder.criticalAllowed && damageKind.criticalAllowed();
        areaDamage = builder.areaDamage;
        lifeStealEfficiency = damageType == DamageType.TRUE
                ? 0.0 : finite(builder.lifeStealEfficiencySet
                ? builder.lifeStealEfficiency
                : damageKind.lifeStealEfficiency(areaDamage, damageType),
                "lifeStealEfficiency");
        defenseReductionPercent = finite(
                builder.defenseReductionPercent, "defenseReductionPercent");
        damageTakenIncreasePercent = finite(
                builder.damageTakenIncreasePercent, "damageTakenIncreasePercent");
        healingReductionPercent = finite(
                builder.healingReductionPercent, "healingReductionPercent");
        pveMultiplier = finite(builder.pveMultiplier, "pveMultiplier");
        pvpMultiplier = finite(builder.pvpMultiplier, "pvpMultiplier");
        iceDirectDamageMultiplier = boundedMultiplier(
                builder.iceDirectDamageMultiplier, "iceDirectDamageMultiplier");
        additionalDamageReductions = builder.additionalDamageReductions.clone();
        offenseSnapshot = builder.offenseSnapshot;
        attackMetadata = builder.attackMetadata == null
                ? AttackMetadata.EMPTY : builder.attackMetadata;
        for (double reduction : additionalDamageReductions) {
            finite(reduction, "additionalDamageReduction");
        }
    }

    public static Builder builder(Player attacker, LivingEntity target) {
        return new Builder(attacker, target);
    }

    public Player attacker() { return attacker; }
    public LivingEntity target() { return target; }
    public String skillId() { return skillId; }
    public UUID castId() { return castId; }
    public DamageType damageType() { return damageType; }
    public DamageKind damageKind() { return damageKind; }
    public double fixedDamage() { return fixedDamage; }
    public double coefficient() { return coefficient; }
    public DamageMode mode() { return mode; }
    public boolean criticalAllowed() { return criticalAllowed; }
    public double lifeStealEfficiency() { return lifeStealEfficiency; }
    public boolean areaDamage() { return areaDamage; }
    public double defenseReductionPercent() { return defenseReductionPercent; }
    public double damageTakenIncreasePercent() { return damageTakenIncreasePercent; }
    public double healingReductionPercent() { return healingReductionPercent; }
    public double modeMultiplier() {
        return mode == DamageMode.PVP ? pvpMultiplier : pveMultiplier;
    }
    /** Explicit pre-mitigation Ice factor; separate from the established mode multiplier. */
    public double iceDirectDamageMultiplier() { return iceDirectDamageMultiplier; }
    public double calculationMultiplier() {
        return StatCalculator.saturatedMultiply(modeMultiplier(), iceDirectDamageMultiplier);
    }
    public double[] additionalDamageReductions() {
        return additionalDamageReductions.clone();
    }
    public DamageOffenseSnapshot offenseSnapshot() { return offenseSnapshot; }
    public AttackMetadata attackMetadata() { return attackMetadata; }

    private static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    private static double boundedMultiplier(double value, String name) {
        finite(value, name);
        if (value < 1.0 || value > 2.0) {
            throw new IllegalArgumentException(name + " must be between 1.0 and 2.0");
        }
        return value;
    }

    public Builder toBuilder() {
        return builder(attacker, target).skillId(skillId).castId(castId)
                .damageType(damageType).damageKind(damageKind)
                .fixedDamage(fixedDamage).coefficient(coefficient).mode(mode)
                .criticalAllowed(criticalAllowed).lifeStealEfficiency(lifeStealEfficiency)
                .areaDamage(areaDamage).defenseReductionPercent(defenseReductionPercent)
                .damageTakenIncreasePercent(damageTakenIncreasePercent)
                .healingReductionPercent(healingReductionPercent)
                .pveMultiplier(pveMultiplier).pvpMultiplier(pvpMultiplier)
                .iceDirectDamageMultiplier(iceDirectDamageMultiplier)
                .additionalDamageReductions(additionalDamageReductions)
                .offenseSnapshot(offenseSnapshot).attackMetadata(attackMetadata);
    }

    public static final class Builder {
        private final Player attacker;
        private final LivingEntity target;
        private String skillId;
        private UUID castId;
        private DamageType damageType = DamageType.PHYSICAL;
        private DamageKind damageKind = DamageKind.DIRECT_SKILL;
        private double fixedDamage;
        private double coefficient;
        private DamageMode mode = DamageMode.PVE;
        private boolean criticalAllowed = true;
        private double lifeStealEfficiency = DamageKind.DIRECT_SKILL.defaultLifeStealEfficiency();
        private boolean lifeStealEfficiencySet;
        private boolean areaDamage;
        private double defenseReductionPercent;
        private double damageTakenIncreasePercent;
        private double healingReductionPercent;
        private double pveMultiplier = 1.0;
        private double pvpMultiplier = 1.0;
        private double iceDirectDamageMultiplier = 1.0;
        private double[] additionalDamageReductions = new double[0];
        private DamageOffenseSnapshot offenseSnapshot;
        private AttackMetadata attackMetadata = AttackMetadata.EMPTY;

        private Builder(Player attacker, LivingEntity target) {
            this.attacker = attacker;
            this.target = target;
        }

        public Builder skillId(String value) { skillId = value; return this; }
        public Builder castId(UUID value) { castId = value; return this; }
        public Builder damageType(DamageType value) { damageType = value; return this; }
        public Builder damageKind(DamageKind value) {
            damageKind = value;
            if (!lifeStealEfficiencySet && value != null) {
                lifeStealEfficiency = value.defaultLifeStealEfficiency();
            }
            return this;
        }
        public Builder fixedDamage(double value) { fixedDamage = value; return this; }
        public Builder coefficient(double value) { coefficient = value; return this; }
        public Builder mode(DamageMode value) { mode = value; return this; }
        public Builder criticalAllowed(boolean value) { criticalAllowed = value; return this; }
        public Builder lifeStealEfficiency(double value) {
            lifeStealEfficiency = value;
            lifeStealEfficiencySet = true;
            return this;
        }
        public Builder areaDamage(boolean value) { areaDamage = value; return this; }
        public Builder defenseReductionPercent(double value) {
            defenseReductionPercent = value; return this;
        }
        public Builder damageTakenIncreasePercent(double value) {
            damageTakenIncreasePercent = value; return this;
        }
        public Builder healingReductionPercent(double value) {
            healingReductionPercent = value; return this;
        }
        public Builder pveMultiplier(double value) { pveMultiplier = value; return this; }
        public Builder pvpMultiplier(double value) { pvpMultiplier = value; return this; }
        public Builder iceDirectDamageMultiplier(double value) {
            iceDirectDamageMultiplier = value; return this;
        }
        public Builder additionalDamageReductions(double... values) {
            additionalDamageReductions = values == null ? new double[0] : values.clone();
            return this;
        }
        public Builder offenseSnapshot(DamageOffenseSnapshot value) {
            offenseSnapshot = value;
            return this;
        }
        public Builder attackMetadata(AttackMetadata value) {
            attackMetadata = value == null ? AttackMetadata.EMPTY : value;
            return this;
        }
        public DamageRequest build() { return new DamageRequest(this); }
    }
}
