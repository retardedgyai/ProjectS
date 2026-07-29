package io.github.gyai.projects.monster;

import io.github.gyai.projects.combat.skill.CcResistanceProfile;
import io.github.gyai.projects.model.MonsterStats;
import org.bukkit.entity.EntityType;

import java.util.Objects;

public final class MonsterData {
    private final String id;
    private final String displayName;
    private final EntityType entityType;
    private final MonsterStats stats;
    private final int level;
    private final MonsterRank rank;
    private final CcResistanceProfile resistanceProfile;

    public MonsterData(
            String id,
            String displayName,
            EntityType entityType,
            MonsterStats stats,
            int level,
            MonsterRank rank,
            CcResistanceProfile resistanceProfile
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Monster id must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Monster display name must not be blank");
        }
        this.id = id;
        this.displayName = displayName;
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.stats = Objects.requireNonNull(stats, "stats");
        if (level < 1 || level > 999) {
            throw new IllegalArgumentException("Monster level must be between 1 and 999");
        }
        this.level = level;
        this.rank = Objects.requireNonNull(rank, "rank");
        this.resistanceProfile = Objects.requireNonNull(
                resistanceProfile, "resistanceProfile");
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public EntityType entityType() {
        return entityType;
    }

    public MonsterStats stats() {
        return stats;
    }

    public int level() {
        return level;
    }

    public MonsterRank rank() {
        return rank;
    }

    public CcResistanceProfile resistanceProfile() {
        return resistanceProfile;
    }
}
