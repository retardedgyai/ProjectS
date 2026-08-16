package io.github.gyai.projects.network.beta;

import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface BetaDisplayPort<T> {
    Optional<T> snapshot(UUID playerId);
}
