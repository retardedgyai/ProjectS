package io.github.gyai.projects.equipment.operation;

import io.github.gyai.projects.crafting.OutputProposal;
import io.github.gyai.projects.equipment.EquipmentWriteBoundary;
import io.github.gyai.projects.transaction.InventoryCapacityProposal;
import io.github.gyai.projects.transaction.ReservationToken;
import io.github.gyai.projects.transaction.TransactionAuditResult;
import io.github.gyai.projects.transaction.TransactionParticipant;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.transaction.TransactionStage;

import java.util.Objects;
import java.util.Optional;

/** Track D participant adapter. No Bukkit types or production writer are supplied here. */
public final class EquipmentOperationParticipant implements TransactionParticipant {
    private final EquipmentOperationPlan plan;
    private final EquipmentResourcePort resources;
    private final EquipmentWriteBoundary writer;
    private final EquipmentOperationJournal journal;
    private EquipmentMutationProposal resolved;

    public EquipmentOperationParticipant(
            EquipmentOperationPlan plan,
            EquipmentResourcePort resources,
            EquipmentWriteBoundary writer,
            EquipmentOperationJournal journal
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    @Override
    public Optional<TransactionAuditResult> findTerminal(TransactionRequest request) {
        return journal.findTerminal(request.requestId());
    }

    @Override
    public Validation validate(TransactionRequest request) {
        if (!plan.transactionRequest().equals(request)) {
            return Validation.deny("operation-plan-request-mismatch");
        }
        return resources.validate(request, plan.resources())
                .map(Validation::allow)
                .orElseGet(() -> Validation.deny("output-capacity-or-resources-unavailable"));
    }

    @Override
    public ReservationToken reserve(
            TransactionRequest request,
            InventoryCapacityProposal capacityProposal
    ) {
        ReservationToken token = resources.reserve(
                request, plan.resources(), capacityProposal);
        try {
            synchronized (this) {
                if (resolved != null) {
                    throw new IllegalStateException("proposal-already-resolved");
                }
                Optional<EquipmentMutationProposal> recorded =
                        journal.findResolvedProposal(request.requestId());
                EquipmentMutationProposal candidate = recorded.orElseGet(() ->
                        Objects.requireNonNull(plan.resolver().resolve(), "resolved proposal"));
                if (!candidate.transactionRequest().equals(request)
                        || !candidate.resources().equals(plan.resources())) {
                    throw new IllegalStateException("resolved-proposal-plan-mismatch");
                }
                if (recorded.isEmpty()) journal.recordResolvedProposal(candidate);
                resolved = candidate;
            }
            return token;
        } catch (RuntimeException failure) {
            resources.rollback(
                    request, plan.resources(), token, TransactionStage.VALIDATE);
            throw failure;
        }
    }

    @Override
    public void consume(TransactionRequest request, ReservationToken token) {
        requireResolved();
        resources.consume(request, plan.resources(), token);
    }

    @Override
    public OutputProposal produce(TransactionRequest request, ReservationToken token) {
        requireResolved();
        return new OutputProposal("projects:equipment-operation", 1, true);
    }

    @Override
    public void persist(
            TransactionRequest request,
            ReservationToken token,
            OutputProposal output
    ) {
        journal.persistProposal(requireResolved());
    }

    @Override
    public TransactionAuditResult commit(
            TransactionRequest request,
            ReservationToken token,
            OutputProposal output,
            TransactionAuditResult proposedCommittedResult
    ) {
        EquipmentMutationProposal proposal = requireResolved();
        EquipmentWriteBoundary.WriteResult write = writer.write(
                new EquipmentWriteBoundary.WriteRequest(
                        proposal.proposedItem(), proposal.expectedRevision(),
                        canonicalWriteRequestId(request)));
        if (!write.committed()) throw new IllegalStateException("equipment-write-not-committed");
        journal.recordTerminal(proposedCommittedResult);
        return proposedCommittedResult;
    }

    @Override
    public void recordTerminal(TransactionAuditResult terminalResult) {
        journal.recordTerminal(terminalResult);
    }

    @Override
    public void rollback(
            TransactionRequest request,
            ReservationToken token,
            TransactionStage lastCompletedStage,
            OutputProposal output
    ) {
        journal.rollbackProposal(request.requestId());
        resources.rollback(request, plan.resources(), token, lastCompletedStage);
    }

    public synchronized boolean resolved() {
        return resolved != null;
    }

    private synchronized EquipmentMutationProposal requireResolved() {
        if (resolved == null) throw new IllegalStateException("proposal-not-resolved");
        return resolved;
    }

    private String canonicalWriteRequestId(TransactionRequest request) {
        return "projects:request-" + request.requestId().toString().replace("-", "");
    }
}
