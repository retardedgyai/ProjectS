package io.github.gyai.projects.persistence.player;

import io.github.gyai.projects.player.progress.PlayerProgressRecordV1;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Bukkit-free storage boundary. Implementations must reject writes after close. */
public interface PlayerProgressRepository extends AutoCloseable {
    PlayerProgressLoadResult load(UUID playerId);

    CompletableFuture<PlayerProgressSaveResult> save(
            PlayerProgressRecordV1 record,
            UUID requestId);

    boolean acceptingWrites();

    @Override
    void close();
}
