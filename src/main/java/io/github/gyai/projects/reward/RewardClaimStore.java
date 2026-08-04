package io.github.gyai.projects.reward;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Implementations durably retain bounded terminal claims across restart. */
public interface RewardClaimStore {
    Optional<RewardClaimResult> findTerminal(RewardClaimKey key);

    /**
     * Acquires durable key ownership, runs at most one delivery operation, and
     * records a terminal result before releasing ownership. Implementations must
     * coordinate all service instances that share the store.
     */
    RewardClaimResult executeExclusive(
            RewardClaimKey key,
            UUID attemptId,
            Supplier<RewardClaimResult> operation
    );
}
