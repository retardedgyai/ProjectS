package io.github.gyai.projects.beta.activation.track3;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import io.github.gyai.projects.crafting.OutputProposal;
import io.github.gyai.projects.transaction.TransactionAuditResult;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.transaction.TransactionStage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Classifies durable state on restart. It deliberately has no execution/retry port. */
public final class StagingTransactionRecoveryService implements AutoCloseable {
    private final StagingTransactionJournalRepository repository;
    private final BoundedStagingOperationJournal operationJournal;
    private final LinkedHashSet<UUID> blockedRequests = new LinkedHashSet<>();
    private final LinkedHashSet<String> blockedOperations = new LinkedHashSet<>();
    private boolean closed;
    private StagingTransactionRecoveryResult latest;

    public StagingTransactionRecoveryService(StagingTransactionJournalRepository repository) {
        this(repository, null);
    }

    public StagingTransactionRecoveryService(
            StagingTransactionJournalRepository repository,
            BoundedStagingOperationJournal operationJournal
    ) {
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.operationJournal = operationJournal;
    }

    public synchronized StagingTransactionRecoveryResult recover() {
        if (closed) throw new IllegalStateException("recovery service is closed");
        blockedRequests.clear();
        blockedOperations.clear();
        int discarded = 0, terminal = 0, required = 0, quarantined = 0;
        for (StagingTransactionJournalRepository.Entry entry : repository.loadAll()) {
            switch (entry.stage()) {
                case VALIDATE, RESERVE -> discarded++;
                case COMMITTED, ROLLED_BACK -> {
                    if (operationJournal == null) {
                        terminal++;
                    } else {
                        operationJournal.restoreTerminal(toTerminal(entry));
                        terminal++;
                    }
                }
                case RESERVED, CONSUMED, PRODUCED, PERSISTED -> {
                    required++;
                    block(entry);
                }
                case COMMIT_UNCERTAIN -> {
                    quarantined++;
                    block(entry);
                    repository.quarantine(entry.requestId(), "commit-uncertain");
                }
            }
        }
        latest = new StagingTransactionRecoveryResult(discarded, terminal, required,
                quarantined, blockedRequests, blockedOperations);
        return latest;
    }

    public synchronized StagingTransactionRecoveryResult recoverOnce() {
        return latest == null ? recover() : latest;
    }

    public boolean usesRepository(StagingTransactionJournalRepository value) {
        return repository == value;
    }

    public boolean restoresInto(BoundedStagingOperationJournal value) {
        return operationJournal == value;
    }

    public synchronized boolean blocked(UUID requestId, String operationKey) {
        return blockedRequests.contains(requestId)
                || operationKey != null && blockedOperations.contains(operationKey);
    }

    public synchronized Set<UUID> blockedRequests() { return Set.copyOf(blockedRequests); }

    private void block(StagingTransactionJournalRepository.Entry entry) {
        if (blockedRequests.size() < StagingTransactionJournalRepository.MAXIMUM_FILES) {
            blockedRequests.add(entry.requestId());
        }
        String key = entry.playerId() + ":" + entry.operationType();
        if (blockedOperations.size() < StagingTransactionJournalRepository.MAXIMUM_FILES) {
            blockedOperations.add(key);
        }
    }

    private static TransactionAuditResult toTerminal(
            StagingTransactionJournalRepository.Entry entry
    ) {
        List<TransactionRequest.InputRevision> inputs = entry.inputIdentities().stream()
                .map(StagingTransactionRecoveryService::input).toList();
        List<TransactionStage> stages = entry.completedStages().stream()
                .map(TransactionStage::valueOf).toList();
        Optional<OutputProposal> output = output(entry);
        TransactionAuditResult.Outcome outcome = entry.stage()
                == StagingTransactionJournalRepository.Stage.COMMITTED
                ? TransactionAuditResult.Outcome.COMMITTED
                : TransactionAuditResult.Outcome.ROLLED_BACK;
        return new TransactionAuditResult(entry.requestId(), entry.playerId(),
                entry.operationType(), entry.recipeId(), entry.expectedRevision(),
                entry.expectedOutputUnits(), inputs, outcome, stages, output,
                entry.reason(), true, Instant.ofEpochMilli(entry.updatedAtMillis()));
    }

    private static TransactionRequest.InputRevision input(String value) {
        int separator = value.lastIndexOf('@');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("malformed durable input identity");
        }
        return new TransactionRequest.InputRevision(value.substring(0, separator),
                Long.parseLong(value.substring(separator + 1)));
    }

    private static Optional<OutputProposal> output(
            StagingTransactionJournalRepository.Entry entry
    ) {
        String value = entry.proposedOutputIdentity();
        if (value.isBlank()) return Optional.empty();
        int separator = value.lastIndexOf(':');
        if (separator <= 0 || separator == value.length() - 1) return Optional.empty();
        return Optional.of(new OutputProposal(value.substring(0, separator),
                Long.parseLong(value.substring(separator + 1)), entry.outputEquipmentBase()));
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        blockedRequests.clear();
        blockedOperations.clear();
        latest = null;
        repository.close();
    }
}
