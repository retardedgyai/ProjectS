package io.github.gyai.projects.participation;

import java.util.Objects;
import java.util.UUID;

public record ExperienceShareProposal(
        EncounterId encounterId,
        UUID playerId,
        long sourceExperience,
        double proposedExperience,
        String policyRevision
) {
    public ExperienceShareProposal {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(playerId, "playerId");
        if (sourceExperience < 0 || !Double.isFinite(proposedExperience)
                || proposedExperience < 0.0) {
            throw new IllegalArgumentException("Experience values must be non-negative and finite");
        }
        if (policyRevision == null || policyRevision.isBlank()) {
            throw new IllegalArgumentException("Policy revision is required");
        }
    }
}
