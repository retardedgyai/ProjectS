package io.github.gyai.projects.player;

import java.util.UUID;

public class PlayerData {
    public static final int MAX_FIGHTING_SPIRIT = 100;

    private final UUID uniqueId;
    private final Stats stats = new Stats();
    private int fightingSpirit;
    private final double cooldownReduction;

    public PlayerData(UUID uniqueId) {
        this.uniqueId = uniqueId;
        this.cooldownReduction = 0.30;
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

    public double getCooldownReduction() {
        return cooldownReduction;
    }

}
