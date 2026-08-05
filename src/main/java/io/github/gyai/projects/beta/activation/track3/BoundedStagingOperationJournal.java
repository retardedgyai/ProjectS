package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.operation.EquipmentMutationProposal;
import io.github.gyai.projects.equipment.operation.EquipmentOperationJournal;
import io.github.gyai.projects.transaction.TransactionAuditResult;

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
        putBounded(terminal, result.requestId(), result);
        resolved.remove(result.requestId());
        persisted.remove(result.requestId());
        auditSink.terminal(result);
    }

    @Override
    public synchronized void rollbackProposal(UUID requestId) {
        persisted.remove(requestId);
        resolved.remove(requestId);
    }

    public synchronized int size() {
        return terminal.size() + resolved.size() + persisted.size();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        terminal.clear();
        resolved.clear();
        persisted.clear();
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
