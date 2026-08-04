package io.github.gyai.projects.player;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class Stats {
    private final EnumMap<StatType, Double> values =
            new EnumMap<>(StatType.class);

    public double get(StatType type) {
        return values.getOrDefault(Objects.requireNonNull(type, "type"), 0.0);
    }

    public void set(StatType type, double value) {
        Objects.requireNonNull(type, "type");
        requireFinite(value);
        if (value == 0.0) values.remove(type);
        else values.put(type, value);
    }

    public double add(StatType type, double amount) {
        requireFinite(amount);
        double updated = get(type) + amount;
        requireFinite(updated);
        set(type, updated);
        return updated;
    }

    public void reset() {
        values.clear();
    }

    public Map<StatType, Double> snapshot() {
        return Map.copyOf(values);
    }

    public static double clampRate(double value, double minimum, double maximum) {
        requireFinite(value);
        requireFinite(minimum);
        requireFinite(maximum);
        if (minimum > maximum) {
            throw new IllegalArgumentException("minimum must not exceed maximum");
        }
        return Math.clamp(value, minimum, maximum);
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Stat values must be finite");
        }
    }

    /** @deprecated Use {@link StatType#PHYSICAL_ATTACK_FLAT}. */
    @Deprecated
    public double getAttackPowerBonus() {
        return get(StatType.PHYSICAL_ATTACK_FLAT);
    }

    /** @deprecated Use {@link #add(StatType, double)}. */
    @Deprecated
    public void addAttackPowerBonus(double amount) {
        add(StatType.PHYSICAL_ATTACK_FLAT, amount);
    }

    /** @deprecated Use {@link StatType#ATTACK_SPEED_PERCENT}. */
    @Deprecated
    public double getAttackSpeedBonus() {
        return get(StatType.ATTACK_SPEED_PERCENT);
    }

    /** @deprecated Use {@link #add(StatType, double)}. */
    @Deprecated
    public void addAttackSpeedBonus(double amount) {
        add(StatType.ATTACK_SPEED_PERCENT, amount);
    }

    /** @deprecated Use {@link #reset()}. */
    @Deprecated
    public void resetDevBonuses() {
        reset();
    }
}
