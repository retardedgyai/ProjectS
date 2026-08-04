package io.github.gyai.projects.monster.editor;

public record MobAiDefinition(
        Preset preset,
        TargetPriority targetPriority,
        double aggroRange,
        double chaseRange,
        double leashRange,
        double attackRange,
        double targetRefreshSeconds,
        boolean returnHome,
        boolean resetHealthOnReturn,
        boolean avoidFalls,
        boolean avoidWater
) {
    public enum Preset {
        PASSIVE,
        NEUTRAL,
        AGGRESSIVE,
        RANGED,
        GUARD,
        BOSS
    }

    public enum TargetPriority {
        NEAREST,
        LOWEST_HEALTH
    }

    public static MobAiDefinition defaults() {
        return new MobAiDefinition(
                Preset.AGGRESSIVE, TargetPriority.NEAREST,
                12, 24, 32, 2.2, 1,
                true, true, true, false);
    }
}
