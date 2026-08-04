package io.github.gyai.projects.persistence.player;

import java.nio.file.Path;
import java.util.UUID;

public record PlayerProgressSaveResult(
        PlayerProgressSaveStatus status,
        UUID playerId,
        long revision,
        UUID requestId,
        Path committedPath,
        String detail
) {
    public PlayerProgressSaveResult {
        if (status == null || playerId == null || requestId == null) {
            throw new IllegalArgumentException("status, playerId, and requestId are required");
        }
        detail = detail == null ? "" : detail;
    }

    public boolean successful() {
        return status == PlayerProgressSaveStatus.COMMITTED
                || status == PlayerProgressSaveStatus.IDEMPOTENT;
    }
}
