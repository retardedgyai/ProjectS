package io.github.gyai.projects.persistence.player;

import io.github.gyai.projects.player.progress.PlayerProgressRecordV1;

import java.nio.file.Path;
import java.util.Optional;

public record PlayerProgressLoadResult(
        PlayerProgressLoadStatus status,
        PlayerProgressRecordV1 record,
        Path quarantineCopy,
        String detail
) {
    public PlayerProgressLoadResult {
        if (status == null) throw new IllegalArgumentException("status is required");
        detail = detail == null ? "" : detail;
    }

    public Optional<PlayerProgressRecordV1> loadedRecord() {
        return Optional.ofNullable(record);
    }

    public Optional<Path> quarantinePath() {
        return Optional.ofNullable(quarantineCopy);
    }

    public boolean successful() {
        return status == PlayerProgressLoadStatus.LOADED
                || status == PlayerProgressLoadStatus.MISSING;
    }
}
