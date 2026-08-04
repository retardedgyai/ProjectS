package io.github.gyai.projects.equipment.operation;

import io.github.gyai.projects.transaction.TransactionRequest;

import java.util.Objects;

/**
 * Reservation-safe operation plan. Resolution is intentionally deferred until
 * the Track D participant has acquired its reservation.
 */
public record EquipmentOperationPlan(
        TransactionRequest transactionRequest,
        OperationResourcePlan resources,
        ProposalResolver resolver
) {
    public EquipmentOperationPlan {
        Objects.requireNonNull(transactionRequest, "transactionRequest");
        resources = resources == null ? OperationResourcePlan.none() : resources;
        Objects.requireNonNull(resolver, "resolver");
    }

    public static EquipmentOperationPlan fixed(EquipmentMutationProposal proposal) {
        Objects.requireNonNull(proposal, "proposal");
        return new EquipmentOperationPlan(
                proposal.transactionRequest(), proposal.resources(), () -> proposal);
    }

    @FunctionalInterface
    public interface ProposalResolver {
        EquipmentMutationProposal resolve();
    }
}
