package io.github.gyai.projects.participation;

import io.github.gyai.projects.transaction.DomainId;

import java.util.Objects;
import java.util.UUID;

public record ParticipationKey(
        EncounterId encounterId,
        UUID playerId,
        String participationSourceId,
        long contributionRevision
) {
    public ParticipationKey {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(playerId, "playerId");
        participationSourceId = DomainId.requireNamespaced(
                participationSourceId, "participation source ID");
        if (contributionRevision < 0) {
            throw new IllegalArgumentException("Negative contribution revision");
        }
    }
}
