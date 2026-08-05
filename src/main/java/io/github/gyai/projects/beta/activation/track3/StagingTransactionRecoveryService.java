package io.github.gyai.projects.beta.activation.track3;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Classifies durable state on restart. It deliberately has no execution/retry port. */
public final class StagingTransactionRecoveryService implements AutoCloseable {
    private final StagingTransactionJournalRepository repository;
    private final LinkedHashSet<UUID> blockedRequests = new LinkedHashSet<>();
    private final LinkedHashSet<String> blockedOperations = new LinkedHashSet<>();
    private boolean closed;

    public StagingTransactionRecoveryService(StagingTransactionJournalRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
    }

    public synchronized StagingTransactionRecoveryResult recover() {
        if (closed) throw new IllegalStateException("recovery service is closed");
        blockedRequests.clear();
        blockedOperations.clear();
        int discarded = 0, terminal = 0, required = 0, quarantined = 0;
        for (StagingTransactionJournalRepository.Entry entry : repository.loadAll()) {
            switch (entry.stage()) {
                case VALIDATE, RESERVE -> discarded++;
                case COMMITTED, ROLLED_BACK -> terminal++;
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
        return new StagingTransactionRecoveryResult(discarded, terminal, required,
                quarantined, blockedRequests, blockedOperations);
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

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        blockedRequests.clear();
        blockedOperations.clear();
        repository.close();
    }
}
