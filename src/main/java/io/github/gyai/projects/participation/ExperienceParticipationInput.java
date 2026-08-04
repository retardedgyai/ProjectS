package io.github.gyai.projects.participation;

import java.util.Objects;
import java.util.UUID;

public record ExperienceParticipationInput(
        EncounterId encounterId,
        UUID playerId,
        boolean sameWorld,
        double distance,
        double creditedContribution
) {
    public ExperienceParticipationInput {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(playerId, "playerId");
        if (!Double.isFinite(distance) || distance < 0.0
                || !Double.isFinite(creditedContribution) || creditedContribution < 0.0) {
            throw new IllegalArgumentException("Experience input values must be finite and non-negative");
        }
    }
}
