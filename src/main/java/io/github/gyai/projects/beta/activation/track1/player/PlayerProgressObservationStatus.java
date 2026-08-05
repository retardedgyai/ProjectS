package io.github.gyai.projects.beta.activation.track1.player;

public enum PlayerProgressObservationStatus {
    OBSERVED_MATCH,
    OBSERVED_MISMATCH,
    STAGING_MISSING,
    POLICY_DENIED,
    QUARANTINED,
    CAPACITY_REACHED,
    CLOSED
}
