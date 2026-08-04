package io.github.gyai.projects.transaction;

import io.github.gyai.projects.crafting.OutputProposal;

import java.util.Objects;
import java.util.Optional;

public interface TransactionParticipant {
    Validation validate(TransactionRequest request);

    ReservationToken reserve(TransactionRequest request);

    void consume(TransactionRequest request, ReservationToken token);

    OutputProposal produce(TransactionRequest request, ReservationToken token);

    void persist(
            TransactionRequest request,
            ReservationToken token,
            OutputProposal output
    );

    void commit(
            TransactionRequest request,
            ReservationToken token,
            OutputProposal output
    );

    void rollback(
            TransactionRequest request,
            ReservationToken token,
            TransactionStage lastCompletedStage,
            OutputProposal output
    );

    record Validation(
            boolean accepted,
            String reason,
            Optional<InventoryCapacityProposal> capacityProposal
    ) {
        public Validation {
            reason = reason == null ? "" : reason;
            if (reason.length() > 256) {
                throw new IllegalArgumentException("Validation reason is too long");
            }
            capacityProposal = capacityProposal == null
                    ? Optional.empty() : capacityProposal;
            if (accepted != capacityProposal.isPresent()) {
                throw new IllegalArgumentException(
                        "Accepted validation requires exactly one capacity proposal");
            }
        }

        public static Validation allow(InventoryCapacityProposal capacityProposal) {
            return new Validation(
                    true, "", Optional.of(Objects.requireNonNull(
                            capacityProposal, "capacityProposal")));
        }

        public static Validation deny(String reason) {
            return new Validation(false, reason, Optional.empty());
        }
    }
}
