package io.github.gyai.projects.beta.activation.track1.player;

import io.github.gyai.projects.player.progress.PlayerProgressSnapshot;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface StagingPlayerProgressPort {
    PlayerProgressObservation onJoin(PlayerProgressSnapshot legacySnapshot,
                                     String worldName, boolean compatibleClient);

    CompletionStage<PlayerProgressSaveObservation> onQuit(
            PlayerProgressSnapshot legacySnapshot, String worldName,
            boolean compatibleClient);

    Optional<PlayerProgressObservation> observation(UUID playerId);

    int activeSessions();

    CompletionStage<Void> drain();
}
