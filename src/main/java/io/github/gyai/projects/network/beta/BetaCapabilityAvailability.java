package io.github.gyai.projects.network.beta;

import java.util.UUID;

@FunctionalInterface
public interface BetaCapabilityAvailability {
    boolean isAvailable(UUID playerId, BetaCapabilityId capabilityId);
}
