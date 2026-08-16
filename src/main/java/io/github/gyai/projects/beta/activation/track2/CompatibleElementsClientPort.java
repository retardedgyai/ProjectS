package io.github.gyai.projects.beta.activation.track2;

import java.util.UUID;

/** UUID-only boundary for the currently acknowledged Elements protocol capability. */
@FunctionalInterface
public interface CompatibleElementsClientPort {
    boolean supportsElements(UUID playerId);
}
