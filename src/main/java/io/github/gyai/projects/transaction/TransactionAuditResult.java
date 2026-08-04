package io.github.gyai.projects.transaction;

import io.github.gyai.projects.crafting.OutputProposal;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TransactionAuditResult(
        UUID requestId,
        UUID playerId,
        String operationId,
        String recipeId,
        long expectedRevision,
        long expectedOutputUnits,
        List<TransactionRequest.InputRevision> inputs,
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
        operationId = DomainId.requireNamespaced(operationId, "operation ID");
        recipeId = DomainId.requireNamespaced(recipeId, "recipe ID");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("Negative expected revision");
        }
        expectedOutputUnits = QuantityMath.requirePositive(
                expectedOutputUnits, "expected output units");
        Objects.requireNonNull(outcome, "outcome");
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        if (inputs.isEmpty() || inputs.size() > 64) {
            throw new IllegalArgumentException("Audit result requires 1..64 inputs");
        }
        HashSet<String> inputIds = new HashSet<>();
        for (TransactionRequest.InputRevision input : inputs) {
            Objects.requireNonNull(input, "input");
            if (!inputIds.add(input.inputId())) {
                throw new IllegalArgumentException(
                        "Duplicate audit input: " + input.inputId());
            }
        }
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
                requestId, playerId, operationId, recipeId, expectedRevision,
                expectedOutputUnits, inputs, outcome,
                completedStages, output, reason, true, completedAt);
    }

    public enum Outcome {
        COMMITTED,
        REJECTED,
        ROLLED_BACK,
        ROLLBACK_FAILED,
        COMMIT_UNCERTAIN,
        INPUT_CONFLICT,
        TERMINAL_LIMIT,
        REPLAY_CONFLICT,
        DUPLICATE_ACTIVE,
        ACTIVE_LIMIT,
        CLOSED
    }
}
