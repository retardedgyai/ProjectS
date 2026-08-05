package io.github.gyai.projects.beta.activation.track1.player;

import io.github.gyai.projects.player.progress.PlayerProgressSnapshot;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface StagingPlayerProgressStore extends AutoCloseable {
    Load load(UUID playerId);

    CompletionStage<PlayerProgressSaveObservation> save(PlayerProgressSnapshot snapshot);

    @Override
    void close();

    record Load(Status status, Optional<PlayerProgressSnapshot> snapshot, String detail) {
        public Load {
            if (status == null) throw new IllegalArgumentException("status is required");
            snapshot = snapshot == null ? Optional.empty() : snapshot;
            detail = detail == null ? "" : detail;
        }
        public enum Status { LOADED, MISSING, MALFORMED, CLOSED }
    }
}
