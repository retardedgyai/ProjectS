package io.github.gyai.projects.reward;

import io.github.gyai.projects.transaction.TransactionAuditResult;
import io.github.gyai.projects.transaction.TransactionEngine;
import io.github.gyai.projects.transaction.TransactionParticipant;
import io.github.gyai.projects.transaction.TransactionRequest;

import java.util.Objects;
import java.util.function.Function;

public final class TransactionRewardDeliveryPort implements RewardDeliveryPort {
    private final TransactionEngine engine;
    private final RewardTransactionRequestFactory requestFactory;
    private final Function<RewardClaimRequest, TransactionParticipant> participantFactory;

    public TransactionRewardDeliveryPort(
            TransactionEngine engine,
            RewardTransactionRequestFactory requestFactory,
            Function<RewardClaimRequest, TransactionParticipant> participantFactory
    ) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory");
        this.participantFactory = Objects.requireNonNull(
                participantFactory, "participantFactory");
    }

    @Override
    public RewardDeliveryReceipt deliver(RewardClaimRequest claim) {
        TransactionRequest request = Objects.requireNonNull(
                requestFactory.create(claim), "transaction request");
        if (!request.playerId().equals(claim.key().playerId())
                || !request.requestId().equals(RewardTransactionIdentity.requestId(claim.key()))) {
            return new RewardDeliveryReceipt(
                    RewardDeliveryReceipt.Status.REJECTED,
                    "unstable-reward-transaction-identity", true);
        }
        TransactionAuditResult result = engine.execute(request,
                Objects.requireNonNull(participantFactory.apply(claim), "participant"));
        return map(result);
    }

    private static RewardDeliveryReceipt map(TransactionAuditResult result) {
        return switch (result.outcome()) {
            case COMMITTED -> new RewardDeliveryReceipt(
                    RewardDeliveryReceipt.Status.DELIVERED, "", true);
            case COMMIT_UNCERTAIN -> new RewardDeliveryReceipt(
                    RewardDeliveryReceipt.Status.COMMIT_UNCERTAIN,
                    result.reason(), true);
            case REJECTED -> new RewardDeliveryReceipt(
                    "inventory-full".equals(result.reason())
                            ? RewardDeliveryReceipt.Status.FULL_INVENTORY
                            : RewardDeliveryReceipt.Status.REJECTED,
                    result.reason(), true);
            case ROLLED_BACK, ROLLBACK_FAILED -> new RewardDeliveryReceipt(
                    RewardDeliveryReceipt.Status.PERSIST_FAILURE,
                    result.reason(), true);
            default -> new RewardDeliveryReceipt(
                    RewardDeliveryReceipt.Status.REJECTED,
                    result.reason(), true);
        };
    }
}
