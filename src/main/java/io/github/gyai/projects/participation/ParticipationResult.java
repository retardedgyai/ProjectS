package io.github.gyai.projects.participation;

import java.util.Optional;

public record ParticipationResult(Status status, Optional<ParticipationRecord> record, String reason) {
    public ParticipationResult {
        if (status == null) throw new IllegalArgumentException("status is required");
        record = record == null ? Optional.empty() : record;
        reason = reason == null ? "" : reason;
    }

    public enum Status { RECORDED, DUPLICATE, STALE, INELIGIBLE, CLOSED, CAPACITY_REACHED }
}
