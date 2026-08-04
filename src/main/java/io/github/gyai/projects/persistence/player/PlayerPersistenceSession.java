package io.github.gyai.projects.persistence.player;

import io.github.gyai.projects.player.progress.PlayerProgressRecordV1;

import java.util.Optional;
import java.util.UUID;

/** Result exposed to a future Paper adapter; contains no Bukkit object. */
public record PlayerPersistenceSession(
        PlayerPersistenceSessionStatus status,
        UUID playerId,
        PlayerProgressRecordV1 record,
        String detail
) {
    public PlayerPersistenceSession {
        if (status == null || playerId == null) {
            throw new IllegalArgumentException("status and playerId are required");
        }
        detail = detail == null ? "" : detail;
    }

    public Optional<PlayerProgressRecordV1> progress() {
        return Optional.ofNullable(record);
    }

    public boolean persistenceBackedFeaturesAllowed() {
        return status == PlayerPersistenceSessionStatus.ACTIVE_LOADED
                || status == PlayerPersistenceSessionStatus.ACTIVE_NEW;
    }
}
