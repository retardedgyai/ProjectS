package io.github.gyai.projects.beta.activation.track1.player;

import io.github.gyai.projects.player.progress.PlayerProgressSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record PlayerProgressObservation(
        UUID playerId,
        PlayerProgressObservationStatus status,
        PlayerProgressSnapshot legacySnapshot,
        Optional<PlayerProgressSnapshot> stagingSnapshot,
        boolean matches,
        List<String> differences,
        long observedRevision
) {
    public static final int MAX_DIFFERENCES = 16;

    public PlayerProgressObservation {
        if (playerId == null || status == null || legacySnapshot == null) {
            throw new IllegalArgumentException("observation identity is required");
        }
        stagingSnapshot = stagingSnapshot == null ? Optional.empty() : stagingSnapshot;
        List<String> values = differences == null ? List.of() : differences;
        differences = values.stream().limit(MAX_DIFFERENCES).map(value -> {
            String text = value == null ? "" : value;
            return text.length() <= 128 ? text : text.substring(0, 128);
        }).toList();
        if (observedRevision < 0) throw new IllegalArgumentException("negative revision");
    }
}
