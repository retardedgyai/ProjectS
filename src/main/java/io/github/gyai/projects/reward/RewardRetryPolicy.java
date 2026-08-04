package io.github.gyai.projects.reward;

/**
 * Owner-supplied retry semantics. In particular, Wave 2 does not guess whether
 * FULL_INVENTORY is terminal or may be retried later.
 */
@FunctionalInterface
public interface RewardRetryPolicy {
    boolean terminal(RewardDeliveryReceipt receipt);
}
