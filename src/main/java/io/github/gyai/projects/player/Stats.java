package io.github.gyai.projects.player;

public class Stats {
    private static final double MIN_ATTACK_POWER_BONUS = -100.0;
    private static final double MAX_ATTACK_POWER_BONUS = 1_000.0;
    private static final double MIN_ATTACK_SPEED_BONUS = -0.9;
    private static final double MAX_ATTACK_SPEED_BONUS = 5.0;

    private double attackPowerBonus;
    private double attackSpeedBonus;

    public double getAttackPowerBonus() {
        return attackPowerBonus;
    }

    public void addAttackPowerBonus(double amount) {
        attackPowerBonus = Math.clamp(
                attackPowerBonus + amount, MIN_ATTACK_POWER_BONUS, MAX_ATTACK_POWER_BONUS);
    }

    public double getAttackSpeedBonus() {
        return attackSpeedBonus;
    }

    public void addAttackSpeedBonus(double amount) {
        attackSpeedBonus = Math.clamp(
                attackSpeedBonus + amount, MIN_ATTACK_SPEED_BONUS, MAX_ATTACK_SPEED_BONUS);
    }

    public void resetDevBonuses() {
        attackPowerBonus = 0.0;
        attackSpeedBonus = 0.0;
    }
}
