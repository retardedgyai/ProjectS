package io.github.gyai.projects.combat.classsystem;

import io.github.gyai.projects.network.SkillInputType;

public enum SkillSlot {
    SKILL_Q, SKILL_W, SKILL_E, SKILL_R;

    public static SkillSlot fromInput(SkillInputType input) {
        return switch (input) {
            case SKILL_1 -> SKILL_Q;
            case SKILL_2 -> SKILL_W;
            case SKILL_3 -> SKILL_E;
            case SKILL_4, ULTIMATE -> SKILL_R;
            case DODGE, BOW_FIRE_START, BOW_FIRE_STOP, OPEN_DEV_MENU -> null;
        };
    }
}
