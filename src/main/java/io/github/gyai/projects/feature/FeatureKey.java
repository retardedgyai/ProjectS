package io.github.gyai.projects.feature;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public enum FeatureKey {
    FIRE_SYSTEM("fire-system"),
    ICE_SYSTEM("ice-system"),
    LIGHTNING_SYSTEM("lightning-system"),
    EQUIPMENT_V2("equipment-v2"),
    MOD_SYSTEM("mod-system"),
    PLAYER_PERSISTENCE("player-persistence"),
    PASSIVE_TREE("passive-tree"),
    GATHERING("gathering"),
    REFINING("refining"),
    CRAFTING("crafting"),
    TIER_PROMOTION("tier-promotion"),
    ENHANCEMENT_V2("enhancement-v2"),
    REPAIR_V2("repair-v2"),
    PARTY("party"),
    QUESTS("quests"),
    REWARD_V2("reward-v2"),
    MOB_EDITOR_V2("mob-editor-v2"),
    CLIENT_BETA_UI("client-beta-ui");

    private static final Map<String, FeatureKey> BY_ID = index();

    private final String id;

    FeatureKey(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String configPath() {
        return "features." + id;
    }

    public static Optional<FeatureKey> fromId(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Map<String, FeatureKey> byId() {
        return BY_ID;
    }

    private static Map<String, FeatureKey> index() {
        LinkedHashMap<String, FeatureKey> result = new LinkedHashMap<>();
        for (FeatureKey key : values()) {
            if (result.put(key.id, key) != null) {
                throw new IllegalStateException("Duplicate feature ID: " + key.id);
            }
        }
        return Collections.unmodifiableMap(result);
    }
}

