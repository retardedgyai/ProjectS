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
        if (playerId == null || advertisementRevision < 0) {
            throw new IllegalArgumentException("Invalid capability snapshot identity");
        }
        acknowledgedCapabilities = Map.copyOf(
                acknowledgedCapabilities == null ? Map.of() : acknowledgedCapabilities);
        if (acknowledgedCapabilities.size() > BetaCapabilityId.values().length
                || acknowledgedCapabilities.values().stream().anyMatch(version -> version <= 0)) {
            throw new IllegalArgumentException("Invalid acknowledged capabilities");
        }
        if (oldClient) {
            if (sessionId != null || expiresAt != null || advertisementRevision != 0
                    || !acknowledgedCapabilities.isEmpty()) {
                throw new IllegalArgumentException("Old-client snapshot must be empty");
            }
        } else if (sessionId == null || expiresAt == null) {
            throw new IllegalArgumentException("Negotiated snapshot requires session and expiry");
        }
    }

    public boolean supports(BetaCapabilityId id, int payloadVersion) {
        return !oldClient && acknowledgedCapabilities.getOrDefault(id, -1) == payloadVersion;
    }

    public static BetaCapabilitySnapshot oldClient(UUID playerId) {
        return new BetaCapabilitySnapshot(playerId, null, 0, Map.of(), null, true);
    }
}
