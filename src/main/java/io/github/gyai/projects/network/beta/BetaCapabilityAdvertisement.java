package io.github.gyai.projects.network.beta;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record BetaCapabilityAdvertisement(
        int aggregateVersion,
        UUID sessionId,
        long advertisementRevision,
        List<BetaCapabilityDescriptor> capabilities
) {
    public BetaCapabilityAdvertisement {
        if (aggregateVersion != BetaProtocolVersion.CURRENT) {
            throw new IllegalArgumentException("Unsupported aggregate version");
        }
        if (sessionId == null || advertisementRevision < 0) {
            throw new IllegalArgumentException("Invalid advertisement identity");
        }
        capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
        if (capabilities.size() > BetaCapabilityId.values().length
                || new HashSet<>(capabilities.stream().map(BetaCapabilityDescriptor::id).toList()).size()
                != capabilities.size()) {
            throw new IllegalArgumentException("Duplicate or excessive capabilities");
        }
    }
}
