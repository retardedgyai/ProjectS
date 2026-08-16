package io.github.gyai.projects.reward;

import io.github.gyai.projects.transaction.DomainId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UnlockProposal(
        UUID playerId,
        String unlockId,
        long expectedPlayerRevision,
        Instant requestedAt
) {
    public UnlockProposal {
        Objects.requireNonNull(playerId, "playerId");
        unlockId = DomainId.requireNamespaced(unlockId, "unlock ID");
        if (expectedPlayerRevision < 0) throw new IllegalArgumentException("Negative revision");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
