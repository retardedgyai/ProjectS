package io.github.gyai.projects.transaction;

import io.github.gyai.projects.crafting.OutputProposal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TransactionEngine implements AutoCloseable {
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

    private final int maximumActive;
    private final int maximumTerminalResults;
    private final Clock clock;
    private final Object lifecycleLock = new Object();
    private final ConcurrentHashMap<UUID, ActiveTransaction> active =
            new ConcurrentHashMap<>();
    private final LinkedHashMap<UUID, TransactionAuditResult> terminalResults =
            new LinkedHashMap<>();
    private final Map<String, UUID> activeInputOwners = new LinkedHashMap<>();
    private volatile boolean closed;

    public TransactionEngine(
            int maximumActive,
            int maximumTerminalResults,
            Clock clock
    ) {
        if (maximumActive <= 0 || maximumTerminalResults <= 0) {
            throw new IllegalArgumentException("Transaction bounds must be positive");
        }
        this.maximumActive = maximumActive;
        this.maximumTerminalResults = maximumTerminalResults;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public TransactionAuditResult execute(
            TransactionRequest request,
            TransactionParticipant participant
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(participant, "participant");

        TransactionAuditResult replay = lookupTerminal(request, participant);
        if (replay != null) return replay;

        ActiveTransaction transaction = new ActiveTransaction(request);
        TransactionAuditResult admissionFailure = admit(transaction);
        if (admissionFailure != null) return admissionFailure;

        try {
            checkCancelled(transaction);
            TransactionParticipant.Validation validation = Objects.requireNonNull(
                    participant.validate(request), "validation");
            transaction.complete(TransactionStage.VALIDATE);
            if (!validation.accepted()) {
                return transaction.result(
                        TransactionAuditResult.Outcome.REJECTED,
                        validation.reason(), clock.instant());
            }
            transaction.capacityProposal = validation.capacityProposal()
                    .orElseThrow();
            if (transaction.capacityProposal.requiredUnits()
                    < request.expectedOutputUnits()) {
                throw new IllegalStateException(
                        "Reserved output capacity is insufficient");
            }

            checkCancelled(transaction);
            transaction.reservation = Objects.requireNonNull(
                    participant.reserve(request, transaction.capacityProposal),
                    "reservation");
            transaction.complete(TransactionStage.RESERVE);

            checkCancelled(transaction);
            participant.consume(request, transaction.reservation);
            transaction.complete(TransactionStage.CONSUME);

            checkCancelled(transaction);
            transaction.output = Objects.requireNonNull(
                    participant.produce(request, transaction.reservation), "output");
            if (transaction.output.quantity() != request.expectedOutputUnits()
                    || transaction.output.quantity()
                    > transaction.capacityProposal.requiredUnits()) {
                throw new IllegalStateException(
                        "Produced output differs from reserved proposal");
            }
            transaction.complete(TransactionStage.PRODUCE);

            checkCancelled(transaction);
            participant.persist(request, transaction.reservation, transaction.output);
            transaction.complete(TransactionStage.PERSIST);

            checkCancelled(transaction);
            TransactionAuditResult proposed = transaction.committedResult(clock.instant());
            TransactionAuditResult committed;
            try {
                committed = participant.commit(
                        request, transaction.reservation,
                        transaction.output, proposed);
            } catch (RuntimeException commitFailure) {
                return resolveCommitUncertainty(
                        transaction, participant, proposed,
                        "commit=" + message(commitFailure));
            }
            if (!isExactCommitReceipt(proposed, committed)) {
                return resolveCommitUncertainty(
                        transaction, participant, proposed,
                        "invalid-durable-commit-receipt");
            }
            transaction.complete(TransactionStage.COMMIT);
            cacheIfSpace(committed, true);
            return committed;
        } catch (RuntimeException exception) {
            if (transaction.reservation == null) {
                return transaction.result(
                        TransactionAuditResult.Outcome.REJECTED,
                        message(exception), clock.instant());
            }
            return rollback(transaction, participant, message(exception));
        } finally {
            release(transaction);
        }
    }

    public int cancelForPlayer(UUID playerId) {
        if (playerId == null) return 0;
        int count = 0;
        for (ActiveTransaction transaction : active.values()) {
            if (transaction.request.playerId().equals(playerId)
                    && transaction.cancelled.compareAndSet(false, true)) {
                count++;
            }
        }
        return count;
    }

    public TransactionAuditResult recover(
            TransactionRecoveryRecord record,
            TransactionParticipant participant
    ) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(participant, "participant");
        TransactionRequest request = record.request();
        TransactionAuditResult replay = lookupTerminal(request, participant);
        if (replay != null) return replay;

        ActiveTransaction transaction = new ActiveTransaction(request);
        transaction.reservation = record.reservation();
        transaction.output = record.output().orElse(null);
        transaction.complete(record.lastCompletedStage());
        TransactionAuditResult admissionFailure = admit(transaction);
        if (admissionFailure != null) return admissionFailure;
        try {
            try {
                participant.rollback(
                        request, record.reservation(),
                        record.lastCompletedStage(), transaction.output);
                TransactionAuditResult result = transaction.result(
                        TransactionAuditResult.Outcome.ROLLED_BACK,
                        "recovered-incomplete-transaction", clock.instant());
                return recordTerminal(participant, result);
            } catch (RuntimeException rollbackFailure) {
                TransactionAuditResult result = transaction.result(
                        TransactionAuditResult.Outcome.ROLLBACK_FAILED,
                        "recovery-rollback=" + message(rollbackFailure),
                        clock.instant());
                return recordTerminal(participant, result);
            }
        } finally {
            release(transaction);
        }
    }

    public int activeCount() {
        return active.size();
    }

    public int committedResultCount() {
        synchronized (lifecycleLock) {
            return terminalResults.size();
        }
    }

    @Override
    public void close() {
        long deadline = System.nanoTime() + CLOSE_TIMEOUT.toNanos();
        synchronized (lifecycleLock) {
            if (!closed) {
                closed = true;
                active.values().forEach(value -> value.cancelled.set(true));
            }
            while (!active.isEmpty()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IllegalStateException(
                            "Timed out draining " + active.size()
                                    + " active transactions");
                }
                try {
                    long millis = Math.max(1, remaining / 1_000_000L);
                    lifecycleLock.wait(millis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Interrupted while draining transactions", exception);
                }
            }
        }
    }

    public boolean isClosed() {
        return closed;
    }

    private TransactionAuditResult lookupTerminal(
            TransactionRequest request,
            TransactionParticipant participant
    ) {
        TransactionAuditResult cached = terminal(request.requestId());
        if (cached != null) return replay(request, cached);
        Optional<TransactionAuditResult> durable = Objects.requireNonNull(
                participant.findTerminal(request), "terminal lookup");
        if (durable.isEmpty()) return null;
        TransactionAuditResult result = durable.orElseThrow();
        TransactionAuditResult replay = replay(request, result);
        if (replay.outcome() != TransactionAuditResult.Outcome.REPLAY_CONFLICT) {
            cacheIfSpace(result, false);
        }
        return replay;
    }

    private TransactionAuditResult admit(ActiveTransaction transaction) {
        TransactionRequest request = transaction.request;
        synchronized (lifecycleLock) {
            if (closed) {
                return immediate(request, TransactionAuditResult.Outcome.CLOSED,
                        "transaction-engine-closed");
            }
            TransactionAuditResult replay = terminalResults.get(request.requestId());
            if (replay != null) return replay(request, replay);
            if (active.containsKey(request.requestId())) {
                return immediate(request,
                        TransactionAuditResult.Outcome.DUPLICATE_ACTIVE,
                        "duplicate-active-request");
            }
            if (terminalResults.size() + active.size()
                    >= maximumTerminalResults) {
                return immediate(request,
                        TransactionAuditResult.Outcome.TERMINAL_LIMIT,
                        "safe-terminal-retention-limit");
            }
            if (active.size() >= maximumActive) {
                return immediate(request,
                        TransactionAuditResult.Outcome.ACTIVE_LIMIT,
                        "active-transaction-limit");
            }
            for (TransactionRequest.InputRevision input : request.inputs()) {
                if (activeInputOwners.containsKey(input.inputId())) {
                    return immediate(request,
                            TransactionAuditResult.Outcome.INPUT_CONFLICT,
                            "input-already-reserved:" + input.inputId());
                }
            }
            active.put(request.requestId(), transaction);
            request.inputs().forEach(input -> activeInputOwners.put(
                    input.inputId(), request.requestId()));
            return null;
        }
    }

    private TransactionAuditResult rollback(
            ActiveTransaction transaction,
            TransactionParticipant participant,
            String originalReason
    ) {
        String reason = originalReason;
        TransactionAuditResult.Outcome outcome =
                TransactionAuditResult.Outcome.ROLLED_BACK;
        try {
            participant.rollback(
                    transaction.request, transaction.reservation,
                    transaction.lastCompleted(), transaction.output);
        } catch (RuntimeException rollbackFailure) {
            outcome = TransactionAuditResult.Outcome.ROLLBACK_FAILED;
            reason = bounded(reason + "; rollback=" + message(rollbackFailure));
        }
        TransactionAuditResult result = transaction.result(
                outcome, reason, clock.instant());
        return recordTerminal(participant, result);
    }

    private TransactionAuditResult recordTerminal(
            TransactionParticipant participant,
            TransactionAuditResult result
    ) {
        try {
            participant.recordTerminal(result);
        } catch (RuntimeException persistenceFailure) {
            result = copyAsRollbackFailure(
                    result, result.reason() + "; terminal="
                            + message(persistenceFailure));
            failStop();
        }
        cacheIfSpace(result, true);
        return result;
    }

    private TransactionAuditResult resolveCommitUncertainty(
            ActiveTransaction transaction,
            TransactionParticipant participant,
            TransactionAuditResult proposed,
            String reason
    ) {
        try {
            Optional<TransactionAuditResult> durable = Objects.requireNonNull(
                    participant.findTerminal(transaction.request),
                    "terminal lookup");
            if (durable.isPresent()
                    && isExactCommitReceipt(proposed, durable.orElseThrow())) {
                transaction.complete(TransactionStage.COMMIT);
                TransactionAuditResult committed = durable.orElseThrow();
                cacheIfSpace(committed, true);
                return committed;
            }
        } catch (RuntimeException lookupFailure) {
            reason = bounded(reason + "; lookup=" + message(lookupFailure));
        }
        TransactionAuditResult uncertain = transaction.result(
                TransactionAuditResult.Outcome.COMMIT_UNCERTAIN,
                reason, clock.instant());
        try {
            participant.recordTerminal(uncertain);
        } catch (RuntimeException persistenceFailure) {
            uncertain = copyWithReason(
                    uncertain, uncertain.reason() + "; terminal="
                            + message(persistenceFailure));
            failStop();
        }
        cacheIfSpace(uncertain, true);
        return uncertain;
    }

    private TransactionAuditResult copyAsRollbackFailure(
            TransactionAuditResult result,
            String reason
    ) {
        return new TransactionAuditResult(
                result.requestId(), result.playerId(), result.operationId(),
                result.recipeId(), result.expectedRevision(),
                result.expectedOutputUnits(), result.inputs(),
                TransactionAuditResult.Outcome.ROLLBACK_FAILED,
                result.completedStages(), result.output(), bounded(reason),
                false, result.completedAt());
    }

    private TransactionAuditResult copyWithReason(
            TransactionAuditResult result,
            String reason
    ) {
        return new TransactionAuditResult(
                result.requestId(), result.playerId(), result.operationId(),
                result.recipeId(), result.expectedRevision(),
                result.expectedOutputUnits(), result.inputs(), result.outcome(),
                result.completedStages(), result.output(), bounded(reason),
                false, result.completedAt());
    }

    private void release(ActiveTransaction transaction) {
        synchronized (lifecycleLock) {
            if (active.remove(transaction.request.requestId(), transaction)) {
                transaction.request.inputs().forEach(input ->
                        activeInputOwners.remove(
                                input.inputId(), transaction.request.requestId()));
            }
            lifecycleLock.notifyAll();
        }
    }

    private void failStop() {
        synchronized (lifecycleLock) {
            closed = true;
            active.values().forEach(value -> value.cancelled.set(true));
        }
    }

    private TransactionAuditResult terminal(UUID requestId) {
        synchronized (lifecycleLock) {
            return terminalResults.get(requestId);
        }
    }

    private void cacheIfSpace(
            TransactionAuditResult result,
            boolean consumesActiveSlot
    ) {
        synchronized (lifecycleLock) {
            if (terminalResults.containsKey(result.requestId())) return;
            int reserved = terminalResults.size() + active.size();
            if (reserved < maximumTerminalResults
                    || (consumesActiveSlot
                    && reserved == maximumTerminalResults)) {
                terminalResults.put(result.requestId(), result);
            }
        }
    }

    private TransactionAuditResult immediate(
            TransactionRequest request,
            TransactionAuditResult.Outcome outcome,
            String reason
    ) {
        return new TransactionAuditResult(
                request.requestId(), request.playerId(), request.operationId(),
                request.recipeId(), request.expectedRevision(),
                request.expectedOutputUnits(), request.inputs(),
                outcome, List.of(), Optional.empty(), reason, false,
                clock.instant());
    }

    private TransactionAuditResult replay(
            TransactionRequest request,
            TransactionAuditResult terminal
    ) {
        if (terminal.requestId().equals(request.requestId())
                && terminal.playerId().equals(request.playerId())
                && terminal.operationId().equals(request.operationId())
                && terminal.recipeId().equals(request.recipeId())
                && terminal.expectedRevision() == request.expectedRevision()
                && terminal.expectedOutputUnits() == request.expectedOutputUnits()
                && terminal.inputs().equals(request.inputs())) {
            return terminal.asReplay();
        }
        return immediate(request, TransactionAuditResult.Outcome.REPLAY_CONFLICT,
                "request-id-reused-with-different-operation");
    }

    private static boolean isExactCommitReceipt(
            TransactionAuditResult proposed,
            TransactionAuditResult committed
    ) {
        return proposed.equals(committed)
                && committed.outcome()
                == TransactionAuditResult.Outcome.COMMITTED;
    }

    private static void checkCancelled(ActiveTransaction transaction) {
        if (transaction.cancelled.get()) {
            throw new TransactionCancelledException();
        }
    }

    private static String message(RuntimeException exception) {
        String value = exception.getMessage();
        return bounded(value == null || value.isBlank()
                ? exception.getClass().getSimpleName() : value);
    }

    private static String bounded(String value) {
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private static final class ActiveTransaction {
        private final TransactionRequest request;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final ArrayList<TransactionStage> completed = new ArrayList<>();
        private ReservationToken reservation;
        private InventoryCapacityProposal capacityProposal;
        private OutputProposal output;

        private ActiveTransaction(TransactionRequest request) {
            this.request = request;
        }

        private void complete(TransactionStage stage) {
            completed.add(stage);
        }

        private TransactionStage lastCompleted() {
            return completed.isEmpty() ? TransactionStage.VALIDATE
                    : completed.getLast();
        }

        private TransactionAuditResult committedResult(Instant completedAt) {
            ArrayList<TransactionStage> stages = new ArrayList<>(completed);
            stages.add(TransactionStage.COMMIT);
            return result(
                    TransactionAuditResult.Outcome.COMMITTED, "", completedAt,
                    stages);
        }

        private TransactionAuditResult result(
                TransactionAuditResult.Outcome outcome,
                String reason,
                Instant completedAt
        ) {
            return result(outcome, reason, completedAt, completed);
        }

        private TransactionAuditResult result(
                TransactionAuditResult.Outcome outcome,
                String reason,
                Instant completedAt,
                List<TransactionStage> stages
        ) {
            return new TransactionAuditResult(
                    request.requestId(), request.playerId(), request.operationId(),
                    request.recipeId(), request.expectedRevision(),
                    request.expectedOutputUnits(), request.inputs(),
                    outcome, stages, Optional.ofNullable(output), reason, false,
                    completedAt);
        }
    }

    private static final class TransactionCancelledException extends RuntimeException {
        private TransactionCancelledException() {
            super("transaction-cancelled");
        }
    }
}
