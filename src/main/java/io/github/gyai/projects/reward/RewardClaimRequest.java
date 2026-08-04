package io.github.gyai.projects.reward;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RewardClaimRequest(UUID requestId, RewardClaimKey key, Instant requestedAt) {
    public RewardClaimRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
