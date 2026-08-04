package io.github.gyai.projects.transaction;

import io.github.gyai.projects.crafting.OutputProposal;

import java.time.Clock;
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
    private final int maximumActive;
    private final int maximumCommittedResults;
    private final Clock clock;
    private final Object lifecycleLock = new Object();
    private final ConcurrentHashMap<UUID, ActiveTransaction> active =
            new ConcurrentHashMap<>();
    private final LinkedHashMap<UUID, TransactionAuditResult> committed =
            new LinkedHashMap<>();
    private volatile boolean closed;

    public TransactionEngine(
            int maximumActive,
            int maximumCommittedResults,
            Clock clock
    ) {
        if (maximumActive <= 0 || maximumCommittedResults <= 0) {
            throw new IllegalArgumentException("Transaction bounds must be positive");
        }
        this.maximumActive = maximumActive;
        this.maximumCommittedResults = maximumCommittedResults;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public TransactionAuditResult execute(
            TransactionRequest request,
            TransactionParticipant participant
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(participant, "participant");

        TransactionAuditResult replay = committed(request.requestId());
        if (replay != null) return replay(request, replay);

        ActiveTransaction transaction = new ActiveTransaction(request);
        synchronized (lifecycleLock) {
            if (closed) return immediate(request, TransactionAuditResult.Outcome.CLOSED,
                    "transaction-engine-closed");
            replay = committed.get(request.requestId());
            if (replay != null) return replay(request, replay);
            if (active.containsKey(request.requestId())) {
                return immediate(request,
                        TransactionAuditResult.Outcome.DUPLICATE_ACTIVE,
                        "duplicate-active-request");
            }
            if (active.size() >= maximumActive) {
                return immediate(request,
                        TransactionAuditResult.Outcome.ACTIVE_LIMIT,
                        "active-transaction-limit");
            }
            active.put(request.requestId(), transaction);
        }

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

            checkCancelled(transaction);
            transaction.reservation = Objects.requireNonNull(
                    participant.reserve(request), "reservation");
            transaction.complete(TransactionStage.RESERVE);

            checkCancelled(transaction);
            participant.consume(request, transaction.reservation);
            transaction.complete(TransactionStage.CONSUME);

            checkCancelled(transaction);
            transaction.output = Objects.requireNonNull(
                    participant.produce(request, transaction.reservation), "output");
            transaction.complete(TransactionStage.PRODUCE);

            checkCancelled(transaction);
            participant.persist(request, transaction.reservation, transaction.output);
            transaction.complete(TransactionStage.PERSIST);

            checkCancelled(transaction);
            participant.commit(request, transaction.reservation, transaction.output);
            transaction.complete(TransactionStage.COMMIT);

            TransactionAuditResult result = transaction.result(
                    TransactionAuditResult.Outcome.COMMITTED, "", clock.instant());
            remember(result);
            return result;
        } catch (RuntimeException exception) {
            if (transaction.reservation == null) {
                return transaction.result(
                        TransactionAuditResult.Outcome.REJECTED,
                        message(exception), clock.instant());
            }
            String reason = message(exception);
            try {
                participant.rollback(
                        request,
                        transaction.reservation,
                        transaction.lastCompleted(),
                        transaction.output);
            } catch (RuntimeException rollbackFailure) {
                reason = bounded(reason + "; rollback=" + message(rollbackFailure));
            }
            return transaction.result(
                    TransactionAuditResult.Outcome.ROLLED_BACK,
                    reason, clock.instant());
        } finally {
            active.remove(request.requestId(), transaction);
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
        TransactionAuditResult replay = committed(record.request().requestId());
        if (replay != null) return replay(record.request(), replay);
        participant.rollback(
                record.request(), record.reservation(),
                record.lastCompletedStage(), record.output().orElse(null));
        TransactionAuditResult result = new TransactionAuditResult(
                record.request().requestId(), record.request().playerId(),
                record.request().recipeId(), record.request().expectedRevision(),
                TransactionAuditResult.Outcome.ROLLED_BACK,
                List.of(record.lastCompletedStage()), record.output(),
                "recovered-incomplete-transaction", false, clock.instant());
        remember(result);
        return result;
    }

    public int activeCount() {
        return active.size();
    }

    public int committedResultCount() {
        synchronized (lifecycleLock) {
            return committed.size();
        }
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (closed) return;
            closed = true;
            active.values().forEach(value -> value.cancelled.set(true));
        }
    }

    public boolean isClosed() {
        return closed;
    }

    private TransactionAuditResult committed(UUID requestId) {
        synchronized (lifecycleLock) {
            return committed.get(requestId);
        }
    }

    private void remember(TransactionAuditResult result) {
        synchronized (lifecycleLock) {
            committed.put(result.requestId(), result);
            while (committed.size() > maximumCommittedResults) {
                UUID oldest = committed.keySet().iterator().next();
                committed.remove(oldest);
            }
        }
    }

    private TransactionAuditResult immediate(
            TransactionRequest request,
            TransactionAuditResult.Outcome outcome,
            String reason
    ) {
        return new TransactionAuditResult(
                request.requestId(), request.playerId(), request.recipeId(),
                request.expectedRevision(), outcome, List.of(), Optional.empty(),
                reason, false, clock.instant());
    }

    private TransactionAuditResult replay(
            TransactionRequest request,
            TransactionAuditResult terminal
    ) {
        if (terminal.playerId().equals(request.playerId())
                && terminal.recipeId().equals(request.recipeId())
                && terminal.expectedRevision() == request.expectedRevision()) {
            return terminal.asReplay();
        }
        return immediate(request, TransactionAuditResult.Outcome.REPLAY_CONFLICT,
                "request-id-reused-with-different-operation");
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

        private TransactionAuditResult result(
                TransactionAuditResult.Outcome outcome,
                String reason,
                Instant completedAt
        ) {
            return new TransactionAuditResult(
                    request.requestId(), request.playerId(), request.recipeId(),
                    request.expectedRevision(), outcome, completed,
                    Optional.ofNullable(output), reason, false, completedAt);
        }
    }

    private static final class TransactionCancelledException extends RuntimeException {
        private TransactionCancelledException() {
            super("transaction-cancelled");
        }
    }
}
