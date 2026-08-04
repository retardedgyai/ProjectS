package io.github.gyai.projects.participation;

import java.time.Instant;
import java.util.Objects;

public record ParticipationRecord(
        ParticipationEvent event,
        double creditedContribution,
        Instant recordedAt
) {
    public ParticipationRecord {
        Objects.requireNonNull(event, "event");
        if (!Double.isFinite(creditedContribution) || creditedContribution < 0.0) {
            throw new IllegalArgumentException("Credit must be finite and non-negative");
        }
        Objects.requireNonNull(recordedAt, "recordedAt");
    }
}
