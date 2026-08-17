package io.github.gyai.projects.data;

import io.github.gyai.projects.player.StatType;
import java.util.Map;
import java.util.UUID;

public record PlayerProgressionSnapshot(int schemaVersion, UUID playerId, int combatLevel,
                                        int fightingSpirit, Map<StatType, Double> stats) {
    public static final int CURRENT_SCHEMA = 1;
    public PlayerProgressionSnapshot {
        if (schemaVersion != CURRENT_SCHEMA) throw new IllegalArgumentException("Unsupported player schema");
        if (playerId == null || combatLevel < 1 || combatLevel > 999 || fightingSpirit < 0 || fightingSpirit > 100) throw new IllegalArgumentException("Invalid player progression");
        stats = Map.copyOf(stats == null ? Map.of() : stats);
        stats.forEach((type, value) -> { if (type == null || value == null || !Double.isFinite(value)) throw new IllegalArgumentException("Invalid player stat"); });
    }
}
