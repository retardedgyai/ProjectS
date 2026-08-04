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
        output = output == null ? Optional.empty() : output;
    }
}
