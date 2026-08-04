package io.github.gyai.projects.equipment.operation;

import io.github.gyai.projects.transaction.TransactionAuditResult;

import java.util.Optional;
import java.util.UUID;

public interface EquipmentOperationJournal {
    Optional<TransactionAuditResult> findTerminal(UUID requestId);

    /** Durable resolved outcome used to prevent RNG replay after interruption. */
    Optional<EquipmentMutationProposal> findResolvedProposal(UUID requestId);

    void recordResolvedProposal(EquipmentMutationProposal proposal);

    void persistProposal(EquipmentMutationProposal proposal);

    void recordTerminal(TransactionAuditResult result);

    void rollbackProposal(UUID requestId);
}
