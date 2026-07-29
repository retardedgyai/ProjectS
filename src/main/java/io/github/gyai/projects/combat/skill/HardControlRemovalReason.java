package io.github.gyai.projects.combat.skill;

public enum HardControlRemovalReason {
    NONE,
    EXPIRED,
    REPLACED,
    CLEARED,
    ENTITY_INVALID,
    WORLD_CHANGED,
    MONSTER_REMOVED,
    BOSS_RESET,
    DEV_TOOL,
    PLUGIN_STOP
}
