package io.github.gyai.projects.monster.editor;

import java.util.List;

public record MobDefinition(
        int schemaVersion,
        long revision,
        String id,
        String displayName,
        String entityType,
        Category category,
        boolean enabled,
        int level,
        NameplateMode nameplateMode,
        List<String> tags,
        MobStatsDefinition stats,
        MobBasicAttackDefinition basicAttack,
        MobAiDefinition ai,
        MobAppearanceDefinition appearance
) {
    public static final int SCHEMA_VERSION = 1;

    public enum Category {
        NORMAL,
        ELITE,
        BOSS
    }

    public enum NameplateMode {
        ALWAYS,
        COMBAT_ONLY,
        HIDDEN
    }

    public MobDefinition {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public static MobDefinition create(String id) {
        return new MobDefinition(
                SCHEMA_VERSION, 0, id, "新しいモブ", "ZOMBIE",
                Category.NORMAL, true, 1, NameplateMode.ALWAYS,
                List.of(), MobStatsDefinition.defaults(),
                MobBasicAttackDefinition.defaults(), MobAiDefinition.defaults(),
                MobAppearanceDefinition.defaults());
    }

    public MobDefinition withRevision(long value) {
        return new MobDefinition(
                schemaVersion, value, id, displayName, entityType,
                category, enabled, level, nameplateMode, tags,
                stats, basicAttack, ai, appearance);
    }
}
