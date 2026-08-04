package io.github.gyai.projects.network.beta;

import java.util.Arrays;
import java.util.Optional;

public enum BetaCapabilityId {
    HUD("projects:hud"),
    PARTY("projects:party"),
    ELEMENTS("projects:elements"),
    EQUIPMENT("projects:equipment"),
    CRAFTING("projects:crafting"),
    ENHANCEMENT("projects:enhancement"),
    MOB_EDITOR_V2("projects:mob-editor-v2");

    private final String id;

    BetaCapabilityId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<BetaCapabilityId> fromId(String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
    }
}
