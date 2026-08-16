package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.operation.EquipmentMutationProposal;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.crafting.OutputProposal;
import io.github.gyai.projects.transaction.TransactionAuditResult;
import io.github.gyai.projects.transaction.TransactionRequest;
import java.util.UUID;

public interface StagingTransactionAuditSink {
    void resolved(EquipmentMutationProposal proposal);

    void terminal(TransactionAuditResult result);

    /**
     * Persists a resource operation's resolved output before any live inventory
     * consumption. Recovery treats a retained intent as ambiguous and never
     * retries it automatically.
     */
    default void resourceIntent(TransactionRequest request, OutputProposal output) { }

    /** Durable intended equipment output, recorded before Bukkit exposure. */
    default void finalized(UUID requestId, EquipmentItemV1 item) { }

    static StagingTransactionAuditSink noOp() {
        return new StagingTransactionAuditSink() {
            @Override public void resolved(EquipmentMutationProposal proposal) { }
            @Override public void terminal(TransactionAuditResult result) { }
        };
    }
}
