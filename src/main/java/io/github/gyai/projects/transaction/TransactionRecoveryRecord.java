package io.github.gyai.projects.transaction;

import io.github.gyai.projects.crafting.OutputProposal;

import java.util.Objects;
import java.util.Optional;

public record TransactionRecoveryRecord(
        TransactionRequest request,
        ReservationToken reservation,
        TransactionStage lastCompletedStage,
        Optional<OutputProposal> output
) {
    public TransactionRecoveryRecord {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(lastCompletedStage, "lastCompletedStage");
        if (lastCompletedStage == TransactionStage.VALIDATE
                || lastCompletedStage == TransactionStage.COMMIT) {
            throw new IllegalArgumentException(
                    "Recovery records require a reserved, uncommitted transaction");
        }
        output = output == null ? Optional.empty() : output;
        if ((lastCompletedStage == TransactionStage.PRODUCE
                || lastCompletedStage == TransactionStage.PERSIST)
                && output.isEmpty()) {
            throw new IllegalArgumentException(
                    "Produced recovery stage requires output proposal");
        }
    }
}
