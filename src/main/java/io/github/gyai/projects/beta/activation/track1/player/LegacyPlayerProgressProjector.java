package io.github.gyai.projects.beta.activation.track1.player;

import io.github.gyai.projects.player.PlayerData;
import io.github.gyai.projects.player.progress.PlayerProgressBuilder;
import io.github.gyai.projects.player.progress.PlayerProgressSnapshot;

import java.time.Instant;
import java.util.Map;

/** Minimal immutable shadow projection. It never mutates PlayerData. */
public final class LegacyPlayerProgressProjector {
    public PlayerProgressSnapshot project(PlayerData playerData) {
        if (playerData == null) throw new IllegalArgumentException("PlayerData is required");
        return new PlayerProgressBuilder(playerData.getUniqueId())
                .level(playerData.getCombatLevel())
                .persistentResources(Map.of(
                        "projects:fighting-spirit", (long) playerData.getFightingSpirit()))
                .revision(0)
                .lastSavedAt(Instant.EPOCH)
                .build();
    }
}
