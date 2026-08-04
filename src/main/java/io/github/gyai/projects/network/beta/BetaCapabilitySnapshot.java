package io.github.gyai.projects.network.beta;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record BetaCapabilitySnapshot(
        UUID playerId,
        UUID sessionId,
        long advertisementRevision,
        Map<BetaCapabilityId, Integer> acknowledgedCapabilities,
        Instant expiresAt,
        boolean oldClient
) {
    public BetaCapabilitySnapshot {
        acknowledgedCapabilities = Map.copyOf(
                acknowledgedCapabilities == null ? Map.of() : acknowledgedCapabilities);
    }

    public boolean supports(BetaCapabilityId id, int payloadVersion) {
        return !oldClient && acknowledgedCapabilities.getOrDefault(id, -1) == payloadVersion;
    }

    public static BetaCapabilitySnapshot oldClient(UUID playerId) {
        return new BetaCapabilitySnapshot(playerId, null, 0, Map.of(), null, true);
    }
}
