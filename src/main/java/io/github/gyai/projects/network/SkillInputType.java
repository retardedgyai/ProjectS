package io.github.gyai.projects.network;

import java.util.Arrays;
import java.util.Optional;

public enum SkillInputType {
    SKILL_1,
    SKILL_2,
    SKILL_3,
    SKILL_4,
    ULTIMATE,
    DODGE,
    BOW_FIRE_START,
    BOW_FIRE_STOP,
    OPEN_DEV_MENU;

    public String getWireName() {
        return name();
    }

    public static Optional<SkillInputType> fromWireName(String wireName) {
        return Arrays.stream(values())
                .filter(type -> type.name().equals(wireName))
                .findFirst();
    }
}
