package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.operation.EquipmentMutationProposal;
import io.github.gyai.projects.equipment.operation.EquipmentOperationJournal;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.transaction.TransactionAuditResult;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.crafting.OutputProposal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bounded operation journal port implementation for an unregistered staging runtime. */
public final class BoundedStagingOperationJournal implements EquipmentOperationJournal,
        AutoCloseable {
    private final int maximumEntries;
    private final StagingTransactionAuditSink auditSink;
    private final LinkedHashMap<UUID, TransactionAuditResult> terminal = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, EquipmentMutationProposal> resolved = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, EquipmentMutationProposal> persisted = new LinkedHashMap<>();
    /* Kept separately from the proposal: it contains the commit-time UUID. */
    private final LinkedHashMap<UUID, EquipmentItemV1> finalizedEquipment = new LinkedHashMap<>();
    private boolean closed;

    public BoundedStagingOperationJournal(int maximumEntries) {
        this(maximumEntries, StagingTransactionAuditSink.noOp());
    }

    public BoundedStagingOperationJournal(
            int maximumEntries,
            StagingTransactionAuditSink auditSink
    ) {
        if (maximumEntries <= 0 || maximumEntries > 4096) {
            throw new IllegalArgumentException("invalid journal bound");
        }
        this.maximumEntries = maximumEntries;
        this.auditSink = java.util.Objects.requireNonNull(auditSink, "auditSink");
    }

    @Override
    public synchronized Optional<TransactionAuditResult> findTerminal(UUID requestId) {
        return Optional.ofNullable(terminal.get(requestId));
    }

    /** Durable pre-terminal record for scalar resource operations. */
    public synchronized void recordResourceIntent(
            TransactionRequest request, OutputProposal output
    ) {
        requireOpen();
        auditSink.resourceIntent(
                java.util.Objects.requireNonNull(request, "request"),
                java.util.Objects.requireNonNull(output, "output"));
    }

    @Override
    public synchronized Optional<EquipmentMutationProposal> findResolvedProposal(UUID requestId) {
        return Optional.ofNullable(resolved.get(requestId));
    }

    @Override
    public synchronized void recordResolvedProposal(EquipmentMutationProposal proposal) {
        requireOpen();
        auditSink.resolved(proposal);
        putBounded(resolved, proposal.requestId(), proposal);
    }

    @Override
    public synchronized void persistProposal(EquipmentMutationProposal proposal) {
        requireOpen();
        EquipmentMutationProposal expected = resolved.get(proposal.requestId());
        if (expected == null || !expected.equals(proposal)) {
            throw new IllegalStateException("unresolved staging proposal cannot persist");
        }
        putBounded(persisted, proposal.requestId(), proposal);
    }

    @Override
    public synchronized void recordTerminal(TransactionAuditResult result) {
        requireOpen();
        TransactionAuditResult existing = terminal.get(result.requestId());
        if (existing != null && !existing.equals(result) && !result.replayed()) {
            throw new IllegalStateException("conflicting staging terminal result");
        }
        // A terminal receipt must never be visible before its durable audit succeeds.
        auditSink.terminal(result);
        putBounded(terminal, result.requestId(), result);
        resolved.remove(result.requestId());
        persisted.remove(result.requestId());
        if (result.outcome() == TransactionAuditResult.Outcome.ROLLED_BACK
                || result.outcome() == TransactionAuditResult.Outcome.REJECTED
                || result.outcome() == TransactionAuditResult.Outcome.ROLLBACK_FAILED) {
            finalizedEquipment.remove(result.requestId());
        }
    }

    /** Stores the UUID-bearing item before the fallible inventory exposure. */
    public synchronized void recordFinalizedEquipment(UUID requestId, EquipmentItemV1 item) {
        requireOpen();
        if (requestId == null || item == null || item.instanceId().isEmpty()) {
            throw new IllegalArgumentException("finalized staging equipment requires an identity");
        }
        EquipmentItemV1 existing = finalizedEquipment.get(requestId);
        if (existing != null && !existing.equals(item)) {
            throw new IllegalStateException("conflicting finalized staging equipment");
        }
        auditSink.finalized(requestId, item);
        putBounded(finalizedEquipment, requestId, item);
    }

    public synchronized Optional<EquipmentItemV1> finalizedEquipment(UUID requestId) {
        return Optional.ofNullable(finalizedEquipment.get(requestId));
    }

    public synchronized void restoreFinalizedEquipment(UUID requestId, EquipmentItemV1 item) {
        requireOpen();
        putBounded(finalizedEquipment, requestId, item);
    }

    /** Loads an already-durable terminal result without writing it again. */
    public synchronized void restoreTerminal(TransactionAuditResult result) {
        requireOpen();
        TransactionAuditResult replay = java.util.Objects.requireNonNull(result).asReplay();
        TransactionAuditResult existing = terminal.get(replay.requestId());
        if (existing != null && !existing.equals(replay)) {
            throw new IllegalStateException("conflicting durable terminal replay");
        }
        putBounded(terminal, replay.requestId(), replay);
        resolved.remove(replay.requestId());
        persisted.remove(replay.requestId());
    }

    @Override
    public synchronized void rollbackProposal(UUID requestId) {
        persisted.remove(requestId);
        resolved.remove(requestId);
        finalizedEquipment.remove(requestId);
    }

    public synchronized int size() {
        return terminal.size() + resolved.size() + persisted.size() + finalizedEquipment.size();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        terminal.clear();
        resolved.clear();
        persisted.clear();
        finalizedEquipment.clear();
        closed = true;
    }

    private <T> void putBounded(Map<UUID, T> values, UUID key, T value) {
        if (!values.containsKey(key) && values.size() >= maximumEntries) {
            throw new IllegalStateException("staging journal capacity reached");
        }
        values.put(key, value);
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("staging journal is closed");
    }
}
