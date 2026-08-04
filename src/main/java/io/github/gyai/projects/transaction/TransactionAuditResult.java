package io.github.gyai.projects.transaction;

import io.github.gyai.projects.crafting.OutputProposal;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TransactionAuditResult(
        UUID requestId,
        UUID playerId,
        String recipeId,
        long expectedRevision,
        Outcome outcome,
        List<TransactionStage> completedStages,
        Optional<OutputProposal> output,
        String reason,
        boolean replayed,
        Instant completedAt
) {
    public TransactionAuditResult {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        recipeId = DomainId.requireNamespaced(recipeId, "recipe ID");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("Negative expected revision");
        }
        Objects.requireNonNull(outcome, "outcome");
        completedStages = completedStages == null
                ? List.of() : List.copyOf(completedStages);
        output = output == null ? Optional.empty() : output;
        reason = reason == null ? "" : reason;
        if (reason.length() > 512) reason = reason.substring(0, 512);
        Objects.requireNonNull(completedAt, "completedAt");
    }

    public TransactionAuditResult asReplay() {
        if (replayed) return this;
        return new TransactionAuditResult(
                requestId, playerId, recipeId, expectedRevision, outcome,
                completedStages, output, reason, true, completedAt);
    }

    public enum Outcome {
        COMMITTED,
        REJECTED,
        ROLLED_BACK,
        REPLAY_CONFLICT,
        DUPLICATE_ACTIVE,
        ACTIVE_LIMIT,
        CLOSED
    }
}
