package io.github.gyai.projects.network.beta;

import java.time.Duration;
import java.util.List;

public record BetaCapabilityPolicy(
        int maximumSessions,
        Duration sessionTtl,
        List<BetaCapabilityDescriptor> advertisedCapabilities
) {
    public static BetaCapabilityPolicy wave3Defaults() {
        return new BetaCapabilityPolicy(
                512,
                Duration.ofMinutes(5),
                java.util.Arrays.stream(BetaCapabilityId.values())
                        .map(BetaCapabilityDescriptor::v1)
                        .toList());
    }

    public BetaCapabilityPolicy {
        if (maximumSessions <= 0 || sessionTtl == null || sessionTtl.isNegative()
                || sessionTtl.isZero()) {
            throw new IllegalArgumentException("Invalid capability policy");
        }
        advertisedCapabilities = List.copyOf(
                advertisedCapabilities == null ? List.of() : advertisedCapabilities);
        if (advertisedCapabilities.size() > BetaCapabilityId.values().length) {
            throw new IllegalArgumentException("Too many capabilities");
        }
        if (advertisedCapabilities.stream()
                .map(BetaCapabilityDescriptor::id)
                .distinct()
                .count() != advertisedCapabilities.size()) {
            throw new IllegalArgumentException("Duplicate capability IDs");
        }
    }
}
