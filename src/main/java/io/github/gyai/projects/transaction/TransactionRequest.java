package io.github.gyai.projects.transaction;

import java.util.Objects;
import java.util.UUID;

public record TransactionRequest(
        UUID requestId,
        UUID playerId,
        String recipeId,
        long expectedRevision
) {
    public TransactionRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        recipeId = DomainId.requireNamespaced(recipeId, "recipe ID");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("Negative expected revision");
        }
    }
}
