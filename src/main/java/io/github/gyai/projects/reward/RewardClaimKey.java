package io.github.gyai.projects.reward;

import io.github.gyai.projects.transaction.DomainId;

import java.util.Objects;
import java.util.UUID;

public record RewardClaimKey(
        UUID playerId,
        String rewardSourceType,
        UUID rewardSourceInstanceId,
        String rewardDefinitionId,
        long rewardRevision
) {
    public RewardClaimKey {
        Objects.requireNonNull(playerId, "playerId");
        rewardSourceType = DomainId.requireNamespaced(
                rewardSourceType, "reward source type");
        Objects.requireNonNull(rewardSourceInstanceId, "rewardSourceInstanceId");
        rewardDefinitionId = DomainId.requireNamespaced(
                rewardDefinitionId, "reward definition ID");
        if (rewardRevision < 0) throw new IllegalArgumentException("Negative reward revision");
    }

    public String stableIdentity() {
        return playerId + "|" + rewardSourceType + "|" + rewardSourceInstanceId
                + "|" + rewardDefinitionId + "|" + rewardRevision;
    }
}
