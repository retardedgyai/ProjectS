package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.operation.EquipmentMutationProposal;
import io.github.gyai.projects.transaction.TransactionAuditResult;

public interface StagingTransactionAuditSink {
    void resolved(EquipmentMutationProposal proposal);

    void terminal(TransactionAuditResult result);

    static StagingTransactionAuditSink noOp() {
        return new StagingTransactionAuditSink() {
            @Override public void resolved(EquipmentMutationProposal proposal) { }
            @Override public void terminal(TransactionAuditResult result) { }
        };
    }
}
