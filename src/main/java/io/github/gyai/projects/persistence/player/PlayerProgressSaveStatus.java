package io.github.gyai.projects.persistence.player;

public enum PlayerProgressSaveStatus {
    COMMITTED,
    IDEMPOTENT,
    STALE,
    CONFLICT,
    QUEUE_FULL,
    CLOSED,
    FAILED
}
