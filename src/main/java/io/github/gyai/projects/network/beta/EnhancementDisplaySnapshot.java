package io.github.gyai.projects.network.beta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EnhancementDisplaySnapshot(
        UUID itemInstanceId,
        long itemRevision,
        int currentLevel,
        boolean broken,
        PreviewStatus previewStatus,
        Map<String, Long> costs,
        List<String> possibleOutcomes,
        UUID requestId,
        TerminalStatus terminalStatus
) {
    public enum PreviewStatus { AVAILABLE, UNAVAILABLE_BALANCE_DATA }
    public enum TerminalStatus { NONE, PENDING, COMMITTED, REJECTED, COMMIT_UNCERTAIN }

    public EnhancementDisplaySnapshot {
        if (itemInstanceId == null || itemRevision < 0 || currentLevel < 0
                || previewStatus == null || requestId == null || terminalStatus == null) {
            throw new IllegalArgumentException("Invalid enhancement snapshot");
        }
        costs = BetaDisplayValidation.map(costs, 64, "enhancement costs");
        costs.forEach((id, value) -> {
            BetaDisplayValidation.id(id, "costId");
            if (value < 0) throw new IllegalArgumentException("Cost cannot be negative");
        });
        possibleOutcomes = BetaDisplayValidation.list(
                possibleOutcomes, 128, "enhancement outcomes");
        possibleOutcomes.forEach(id -> BetaDisplayValidation.id(id, "outcomeId"));
        if (previewStatus == PreviewStatus.UNAVAILABLE_BALANCE_DATA
                && (!costs.isEmpty() || !possibleOutcomes.isEmpty())) {
            throw new IllegalArgumentException("Unapproved balance data cannot be previewed");
        }
    }
}
