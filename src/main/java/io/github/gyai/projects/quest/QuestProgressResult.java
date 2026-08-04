package io.github.gyai.projects.quest;

import java.util.Optional;
import java.util.UUID;

public record QuestProgressResult(
        UUID commandId,
        Status status,
        Optional<QuestProgressSnapshot> proposal,
        String reason
) {
    public QuestProgressResult {
        if (commandId == null || status == null) throw new IllegalArgumentException("Result identity required");
        proposal = proposal == null ? Optional.empty() : proposal;
        reason = reason == null ? "" : reason;
    }

    public enum Status { STARTED, UPDATED, COMPLETED, CLAIM_MARKED, STALE, UNKNOWN_QUEST, REJECTED }
}
