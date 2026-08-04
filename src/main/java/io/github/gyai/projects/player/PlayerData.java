package io.github.gyai.projects.player;

import java.util.UUID;

public class PlayerData {
    public static final int MAX_FIGHTING_SPIRIT = 100;

    private final UUID uniqueId;
    private final Stats stats = new Stats();
    private int fightingSpirit;
    private int combatLevel = 1;

    public PlayerData(UUID uniqueId) {
        this.uniqueId = uniqueId;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public Stats getStats() {
        return stats;
    }

    public int getFightingSpirit() {
        return fightingSpirit;
    }

    public void addFightingSpirit(int amount) {
        fightingSpirit = Math.clamp(fightingSpirit + amount, 0, MAX_FIGHTING_SPIRIT);
    }

    public void setFightingSpirit(int amount) {
        fightingSpirit = Math.clamp(amount, 0, MAX_FIGHTING_SPIRIT);
    }

    public boolean consumeFightingSpirit(int amount) {
        if (amount < 0 || fightingSpirit < amount) {
            return false;
        }
        fightingSpirit -= amount;
        return true;
    }

    public double getCooldownRecoveryPercent() {
        return stats.get(StatType.COOLDOWN_RECOVERY_PERCENT);
    }

    /**
     * @deprecated This name remains only for binary and source compatibility.
     * The returned value is the cooldown recovery speed stat, not a cooldown
     * reduction rate. New code must use {@link #getCooldownRecoveryPercent()}.
     */
    @Deprecated
    public double getCooldownReduction() {
        return getCooldownRecoveryPercent();
    }

    public int getCombatLevel() {
        return combatLevel;
    }

    public void setCombatLevel(int combatLevel) {
        this.combatLevel = Math.clamp(combatLevel, 1, 999);
    }
}
