package io.github.gyai.projects.monster.editor;

public record MobStatsDefinition(
        double maxHealth,
        double physicalAttack,
        double magicalAttack,
        double physicalDefense,
        double magicalDefense,
        double movementSpeed,
        double attackSpeed,
        double criticalChance,
        double criticalDamage,
        double damageReduction
) {
    public static MobStatsDefinition defaults() {
        return new MobStatsDefinition(
                20, 4, 0, 0, 0, 1, 1,
                .05, 1.75, 0);
    }
}
