package io.github.gyai.projects.participation;

import java.time.Instant;
import java.util.Objects;

public record ParticipationEvent(
        ParticipationKey key,
        double reportedContribution,
        ContributionSemantics semantics,
        Instant occurredAt
) {
    public ParticipationEvent {
        Objects.requireNonNull(key, "key");
        if (!Double.isFinite(reportedContribution) || reportedContribution < 0.0) {
            throw new IllegalArgumentException("Contribution must be finite and non-negative");
        }
        Objects.requireNonNull(semantics, "semantics");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** The ledger never guesses whether an upstream value is a delta or replacement. */
    public enum ContributionSemantics { DELTA, ABSOLUTE }
}
