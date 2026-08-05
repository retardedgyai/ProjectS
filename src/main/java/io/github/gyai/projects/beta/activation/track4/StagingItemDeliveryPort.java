package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.reward.RewardClaimRequest;
import io.github.gyai.projects.reward.RewardDeliveryReceipt;

/** Track 3 delivery boundary. Implementations own the transaction/inventory commit. */
public interface StagingItemDeliveryPort {
    RewardDeliveryReceipt deliver(
            RewardClaimRequest claim,
            String canonicalItemId,
            int quantity);

    boolean available();
}
