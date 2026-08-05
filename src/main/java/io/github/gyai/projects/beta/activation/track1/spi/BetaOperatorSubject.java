package io.github.gyai.projects.beta.activation.track1.spi;

import java.util.UUID;

/** Immutable callback-scoped identity; never retains a Bukkit sender or player. */
public record BetaOperatorSubject(UUID playerId, String worldName, boolean compatibleClient) {
}
