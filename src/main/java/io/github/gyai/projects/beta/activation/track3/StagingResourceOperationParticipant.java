package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.crafting.OutputProposal;
import io.github.gyai.projects.equipment.operation.OperationResourcePlan;
import io.github.gyai.projects.transaction.InventoryCapacityProposal;
import io.github.gyai.projects.transaction.ReservationToken;
import io.github.gyai.projects.transaction.TransactionAuditResult;
import io.github.gyai.projects.transaction.TransactionParticipant;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.transaction.TransactionStage;

import java.util.Optional;
import java.util.UUID;

final class StagingResourceOperationParticipant implements TransactionParticipant {
    private final UUID playerId;
    private final TransactionRequest request;
    private final OperationResourcePlan resources;
    private final OutputProposal output;
    private final StagingInventoryPort inventory;
    private final StagingInventoryResourceAdapter adapter;
    private final BoundedStagingOperationJournal journal;

    StagingResourceOperationParticipant(
            UUID playerId,
            TransactionRequest request,
            OperationResourcePlan resources,
            OutputProposal output,
            StagingInventoryPort inventory,
            StagingInventoryResourceAdapter adapter,
            BoundedStagingOperationJournal journal
    ) {
        this.playerId = playerId;
        this.request = request;
        this.resources = resources;
        this.output = output;
        this.inventory = inventory;
        this.adapter = adapter;
        this.journal = journal;
    }

    @Override
    public Optional<TransactionAuditResult> findTerminal(TransactionRequest value) {
        return journal.findTerminal(value.requestId());
    }

    @Override
    public Validation validate(TransactionRequest value) {
        if (!request.equals(value)) return Validation.deny("operation-plan-request-mismatch");
        return adapter.validate(value, resources).map(Validation::allow)
                .orElseGet(() -> Validation.deny("output-capacity-or-resources-unavailable"));
    }

    @Override
    public ReservationToken reserve(TransactionRequest value,
                                    InventoryCapacityProposal capacityProposal) {
        return adapter.reserve(value, resources, capacityProposal);
    }

    @Override
    public void consume(TransactionRequest value, ReservationToken token) {
        if (!request.equals(value)) throw new IllegalStateException("operation-plan-request-mismatch");
        // This is intentionally before adapter.consume(), whose Bukkit-backed
        // implementation performs the first live inventory mutation.
        journal.recordResourceIntent(value, output);
        adapter.consume(value, resources, token);
    }

    @Override
    public OutputProposal produce(TransactionRequest value, ReservationToken token) {
        return output;
    }

    @Override
    public void persist(TransactionRequest value, ReservationToken token,
                        OutputProposal produced) {
        if (!output.equals(produced)) throw new IllegalStateException("staging output changed");
    }

    @Override
    public TransactionAuditResult commit(TransactionRequest value,
                                         ReservationToken token,
                                         OutputProposal produced,
                                         TransactionAuditResult proposedCommittedResult) {
        StagingInventoryPort.CommitResult result = inventory.commitResource(
                playerId, value.requestId(), token, output.outputId(), output.quantity());
        if (!result.committed()) throw new IllegalStateException(result.status());
        journal.recordTerminal(proposedCommittedResult);
        return proposedCommittedResult;
    }

    @Override
    public void recordTerminal(TransactionAuditResult terminalResult) {
        journal.recordTerminal(terminalResult);
    }

    @Override
    public void rollback(TransactionRequest value, ReservationToken token,
                         TransactionStage lastCompletedStage, OutputProposal produced) {
        adapter.rollback(value, resources, token, lastCompletedStage);
    }
}
