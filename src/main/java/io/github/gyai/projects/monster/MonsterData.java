package io.github.gyai.projects.monster;

import io.github.gyai.projects.model.MonsterStats;
import org.bukkit.entity.EntityType;

import java.util.Objects;

public final class MonsterData {
    private final String id;
    private final String displayName;
    private final EntityType entityType;
    private final MonsterStats stats;

    public MonsterData(
            String id,
            String displayName,
            EntityType entityType,
            MonsterStats stats
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
}
