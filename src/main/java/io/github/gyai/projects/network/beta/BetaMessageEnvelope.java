package io.github.gyai.projects.network.beta;

import java.util.UUID;

public record BetaMessageEnvelope(
        int aggregateVersion,
        BetaMessageKind kind,
        BetaCapabilityId capabilityId,
        int capabilityPayloadVersion,
        UUID requestOrSessionId,
        byte[] payload
) {
    public BetaMessageEnvelope {
        if (aggregateVersion != BetaProtocolVersion.CURRENT || kind == null
                || capabilityId == null || capabilityPayloadVersion <= 0
                || requestOrSessionId == null) {
            throw new IllegalArgumentException("Invalid envelope metadata");
        }
        payload = payload == null ? new byte[0] : payload.clone();
        if (payload.length > BetaProtocolLimits.DEFAULTS.packetBytes()) {
            throw new IllegalArgumentException("Payload is oversized");
        }
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
