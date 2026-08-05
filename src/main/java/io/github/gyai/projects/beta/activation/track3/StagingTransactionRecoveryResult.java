package io.github.gyai.projects.beta.activation.track3;

import java.util.Set;
import java.util.UUID;

public record StagingTransactionRecoveryResult(
        int discarded,
        int terminalReplayed,
        int recoveryRequired,
        int quarantined,
        Set<UUID> blockedRequestIds,
        Set<String> blockedOperationKeys
) {
    public StagingTransactionRecoveryResult {
        if (discarded < 0 || terminalReplayed < 0 || recoveryRequired < 0 || quarantined < 0
                || blockedRequestIds == null || blockedOperationKeys == null
                || blockedRequestIds.size() > 2_048 || blockedOperationKeys.size() > 2_048) {
            throw new IllegalArgumentException("invalid recovery result");
        }
        blockedRequestIds = Set.copyOf(blockedRequestIds);
        blockedOperationKeys = Set.copyOf(blockedOperationKeys);
    }
}
