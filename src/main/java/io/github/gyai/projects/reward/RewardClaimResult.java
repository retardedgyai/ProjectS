package io.github.gyai.projects.reward;

import java.time.Instant;
import java.util.Objects;

public record RewardClaimResult(
        RewardClaimKey key,
        Status status,
        String reason,
        boolean terminal,
        boolean replayed,
        Instant completedAt
) {
    public RewardClaimResult {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(status, "status");
        reason = reason == null ? "" : reason;
        Objects.requireNonNull(completedAt, "completedAt");
    }

    public RewardClaimResult asReplay() {
        return replayed ? this : new RewardClaimResult(
                key, status, reason, terminal, true, completedAt);
    }

    public enum Status {
        DELIVERED, FULL_INVENTORY, PERSIST_FAILURE, COMMIT_UNCERTAIN,
        REJECTED, CLAIM_STORE_FAILURE
    }
}
