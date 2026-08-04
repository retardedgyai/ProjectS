package io.github.gyai.projects.reward;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class RewardTransactionIdentity {
    private RewardTransactionIdentity() { }

    public static UUID requestId(RewardClaimKey key) {
        return UUID.nameUUIDFromBytes(
                ("projects:reward-claim|" + key.stableIdentity())
                        .getBytes(StandardCharsets.UTF_8));
    }
}
