package io.github.gyai.projects.monster.editor;

import io.github.gyai.projects.transaction.DomainId;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

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
        MobAppearanceDefinition appearance,
        List<String> abilityIds
) {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ABILITY_IDS = 64;

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
        if (abilityIds == null) {
            throw new IllegalArgumentException("abilityIds must not be null");
        }
        if (abilityIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("abilityIds must not contain null");
        }
        abilityIds = List.copyOf(abilityIds);
        if (abilityIds.size() > MAX_ABILITY_IDS) {
            throw new IllegalArgumentException("Mob abilities are limited to "
                    + MAX_ABILITY_IDS);
        }
        HashSet<String> unique = new HashSet<>();
        for (String abilityId : abilityIds) {
            DomainId.requireNamespaced(abilityId, "ability id");
            if (!unique.add(abilityId)) {
                throw new IllegalArgumentException("Duplicate mob ability id: " + abilityId);
            }
        }
    }

    /** Compatibility constructor for Mob Editor packet v1 and legacy call sites. */
    public MobDefinition(
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
        this(schemaVersion, revision, id, displayName, entityType, category,
                enabled, level, nameplateMode, tags, stats, basicAttack, ai,
                appearance, List.of());
    }

    public static MobDefinition create(String id) {
        return new MobDefinition(
                SCHEMA_VERSION, 0, id, "新しいモブ", "ZOMBIE",
                Category.NORMAL, true, 1, NameplateMode.ALWAYS,
                List.of(), MobStatsDefinition.defaults(),
                MobBasicAttackDefinition.defaults(), MobAiDefinition.defaults(),
                MobAppearanceDefinition.defaults(), List.of());
    }

    public MobDefinition withRevision(long value) {
        return new MobDefinition(
                schemaVersion, value, id, displayName, entityType,
                category, enabled, level, nameplateMode, tags,
                stats, basicAttack, ai, appearance, abilityIds);
    }

    /**
     * Structural copy only. Registry existence is intentionally validated at
     * explicit assignment and cast resolution boundaries, never on cold load.
     */
    public MobDefinition withAbilityIds(List<String> values) {
        return new MobDefinition(
                schemaVersion, revision, id, displayName, entityType,
                category, enabled, level, nameplateMode, tags,
                stats, basicAttack, ai, appearance, values);
    }
}
