package io.github.gyai.projects.reward;

/** Delivery implementations must be idempotent for the stable transaction identity. */
@FunctionalInterface
public interface RewardDeliveryPort {
    RewardDeliveryReceipt deliver(RewardClaimRequest request);
}
