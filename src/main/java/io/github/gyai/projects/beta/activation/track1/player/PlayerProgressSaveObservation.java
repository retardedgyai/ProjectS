package io.github.gyai.projects.beta.activation.track1.player;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public record PlayerProgressSaveObservation(
        UUID playerId,
        Status status,
        long revision,
        Optional<Path> path,
        String detail
) {
    public PlayerProgressSaveObservation {
        if (playerId == null || status == null || revision < 0) {
            throw new IllegalArgumentException("invalid save observation");
        }
        path = path == null ? Optional.empty() : path;
        detail = detail == null ? "" : detail.replace('\n', ' ').replace('\r', ' ');
        if (detail.length() > 256) detail = detail.substring(0, 256);
    }

    public enum Status { READ_ONLY, COMMITTED, STALE, DENIED, FAILED, NO_SESSION, CLOSED }
}
