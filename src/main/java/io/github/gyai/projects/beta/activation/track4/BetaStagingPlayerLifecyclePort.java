package io.github.gyai.projects.beta.activation.track4;

import java.util.UUID;

/** UUID-only boundary for connection-scoped staging cleanup. */
public interface BetaStagingPlayerLifecyclePort {
    void connectionStarted(UUID playerId);

    void connectionEnded(UUID playerId);

    void clearAll();
}
