package io.github.gyai.projects.persistence.player;

public enum PlayerProgressLoadStatus {
    LOADED,
    MISSING,
    QUARANTINED_CORRUPT,
    QUARANTINED_UNKNOWN_VERSION,
    CLOSED,
    FAILED
}
