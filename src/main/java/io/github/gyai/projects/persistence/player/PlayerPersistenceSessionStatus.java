package io.github.gyai.projects.persistence.player;

public enum PlayerPersistenceSessionStatus {
    ACTIVE_LOADED,
    ACTIVE_NEW,
    DISABLED_MEMORY_ONLY,
    DUPLICATE_CONNECTION,
    BLOCKED_LOAD_FAILURE,
    CAPACITY_REACHED,
    CLOSED
}
