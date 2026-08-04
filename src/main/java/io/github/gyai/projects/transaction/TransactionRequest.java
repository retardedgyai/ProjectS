package io.github.gyai.projects.transaction;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TransactionRequest(
        UUID requestId,
        UUID playerId,
        String operationId,
        String recipeId,
        long expectedRevision,
        long expectedOutputUnits,
        List<InputRevision> inputs
) {
    public TransactionRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        operationId = DomainId.requireNamespaced(operationId, "operation ID");
        recipeId = DomainId.requireNamespaced(recipeId, "recipe ID");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("Negative expected revision");
        }
        expectedOutputUnits = QuantityMath.requirePositive(
                expectedOutputUnits, "expected output units");
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        if (inputs.isEmpty() || inputs.size() > 64) {
            throw new IllegalArgumentException("Transaction requires 1..64 inputs");
        }
        HashSet<String> identities = new HashSet<>();
        for (InputRevision input : inputs) {
            Objects.requireNonNull(input, "input");
            if (!identities.add(input.inputId())) {
                throw new IllegalArgumentException(
                        "Duplicate input identity: " + input.inputId());
            }
        }
    }

    public record InputRevision(String inputId, long revision) {
        public InputRevision {
            inputId = DomainId.requireNamespaced(inputId, "input ID");
            if (revision < 0) {
                throw new IllegalArgumentException("Negative input revision");
            }
        }
    }
}
