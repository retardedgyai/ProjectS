package io.github.gyai.projects.beta.activation.track1.bukkit;

import java.util.UUID;

/** Supplied by the future protocol Gate; Track 1 does not own client sessions. */
public interface CompatibleClientResolver {
    boolean hasCompatibleClient(UUID playerId);
}
