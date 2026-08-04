package io.github.gyai.projects.network.beta;

public record BetaCapabilityDescriptor(BetaCapabilityId id, int payloadVersion) {
    public static final int WAVE_3_PAYLOAD_VERSION = 1;

    public BetaCapabilityDescriptor {
        if (id == null) throw new IllegalArgumentException("Capability ID is required");
        if (payloadVersion <= 0) throw new IllegalArgumentException("Payload version must be positive");
    }

    public static BetaCapabilityDescriptor v1(BetaCapabilityId id) {
        return new BetaCapabilityDescriptor(id, WAVE_3_PAYLOAD_VERSION);
    }
}
