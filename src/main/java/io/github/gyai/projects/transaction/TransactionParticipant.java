package io.github.gyai.projects.transaction;

import io.github.gyai.projects.crafting.OutputProposal;

import java.util.Objects;
import java.util.Optional;

public interface TransactionParticipant {
    /** Returns a durable terminal record retained for safe request retries. */
    Optional<TransactionAuditResult> findTerminal(TransactionRequest request);

    Validation validate(TransactionRequest request);

    ReservationToken reserve(
            TransactionRequest request,
            InventoryCapacityProposal capacityProposal
    );

    void consume(TransactionRequest request, ReservationToken token);

    OutputProposal produce(TransactionRequest request, ReservationToken token);

    void persist(
            TransactionRequest request,
            ReservationToken token,
            OutputProposal output
    );

    /**
     * Atomically exposes the output and persists the proposed committed result.
     * A returned result must equal the proposal. Implementations that throw after
     * persisting must make the committed result visible through {@link #findTerminal}.
     */
    TransactionAuditResult commit(
            TransactionRequest request,
            ReservationToken token,
            OutputProposal output,
            TransactionAuditResult proposedCommittedResult
    );

    /** Persists a rollback or uncertainty result before the request may be retried. */
    void recordTerminal(TransactionAuditResult terminalResult);

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
