package io.github.gyai.projects.reward;

import io.github.gyai.projects.transaction.TransactionRequest;

@FunctionalInterface
public interface RewardTransactionRequestFactory {
    /** Supplies approved contents/quantity without embedding unapproved values in Track F. */
    TransactionRequest create(RewardClaimRequest claim);
}
