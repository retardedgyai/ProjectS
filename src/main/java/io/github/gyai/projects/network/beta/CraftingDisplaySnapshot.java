package io.github.gyai.projects.network.beta;

import java.util.List;
import java.util.UUID;

public record CraftingDisplaySnapshot(
        String recipeId,
        long recipeRevision,
        List<Entry> inputPreview,
        List<Entry> outputPreview,
        UUID requestId,
        TransactionStatus transactionStatus
) {
    public enum TransactionStatus { PREVIEW, PENDING, COMMITTED, REJECTED, COMMIT_UNCERTAIN }

    public CraftingDisplaySnapshot {
        recipeId = BetaDisplayValidation.id(recipeId, "recipeId");
        if (recipeRevision < 0 || requestId == null || transactionStatus == null) {
            throw new IllegalArgumentException("Invalid crafting snapshot");
        }
        inputPreview = BetaDisplayValidation.list(inputPreview, 128, "crafting inputs");
        outputPreview = BetaDisplayValidation.list(outputPreview, 128, "crafting outputs");
    }

    public record Entry(String canonicalId, long quantity) {
        public Entry {
            canonicalId = BetaDisplayValidation.id(canonicalId, "entryId");
            if (quantity < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        }
    }
}
