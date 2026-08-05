package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.crafting.OutputProposal;
import io.github.gyai.projects.transaction.InventoryCapacityProposal;
import io.github.gyai.projects.transaction.ReservationToken;
import io.github.gyai.projects.transaction.TransactionAuditResult;
import io.github.gyai.projects.transaction.TransactionParticipant;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.transaction.TransactionStage;

import java.util.Optional;

final class FailureInjectingTransactionParticipant implements TransactionParticipant {
    private final TransactionParticipant delegate;
    private final StagingFailurePoint failurePoint;

    FailureInjectingTransactionParticipant(
            TransactionParticipant delegate,
            StagingFailurePoint failurePoint
    ) {
        this.delegate = delegate;
        this.failurePoint = failurePoint == null ? StagingFailurePoint.NONE : failurePoint;
    }

    @Override
    public Optional<TransactionAuditResult> findTerminal(TransactionRequest request) {
        return delegate.findTerminal(request);
    }

    @Override
    public Validation validate(TransactionRequest request) {
        fail(StagingFailurePoint.VALIDATE);
        return delegate.validate(request);
    }

    @Override
    public ReservationToken reserve(TransactionRequest request,
                                    InventoryCapacityProposal capacityProposal) {
        fail(StagingFailurePoint.RESERVE);
        return delegate.reserve(request, capacityProposal);
    }

    @Override
    public void consume(TransactionRequest request, ReservationToken token) {
        fail(StagingFailurePoint.CONSUME);
        delegate.consume(request, token);
    }

    @Override
    public OutputProposal produce(TransactionRequest request, ReservationToken token) {
        fail(StagingFailurePoint.PRODUCE);
        return delegate.produce(request, token);
    }

    @Override
    public void persist(TransactionRequest request, ReservationToken token,
                        OutputProposal output) {
        fail(StagingFailurePoint.PERSIST);
        delegate.persist(request, token, output);
    }

    @Override
    public TransactionAuditResult commit(TransactionRequest request,
                                         ReservationToken token,
                                         OutputProposal output,
                                         TransactionAuditResult proposedCommittedResult) {
        fail(StagingFailurePoint.COMMIT);
        return delegate.commit(request, token, output, proposedCommittedResult);
    }

    @Override
    public void recordTerminal(TransactionAuditResult terminalResult) {
        delegate.recordTerminal(terminalResult);
    }

    @Override
    public void rollback(TransactionRequest request, ReservationToken token,
                         TransactionStage lastCompletedStage, OutputProposal output) {
        delegate.rollback(request, token, lastCompletedStage, output);
    }

    private void fail(StagingFailurePoint point) {
        if (failurePoint == point) {
            throw new IllegalStateException("injected-" + point.name().toLowerCase());
        }
    }
}
