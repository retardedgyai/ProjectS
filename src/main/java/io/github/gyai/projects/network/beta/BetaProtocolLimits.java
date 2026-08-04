package io.github.gyai.projects.network.beta;

public record BetaProtocolLimits(
        int handshakeBytes,
        int packetBytes,
        int stringBytes,
        int canonicalIdBytes,
        int listEntries,
        int mapEntries,
        int mobEditorPageEntries,
        int nestingDepth
) {
    public static final BetaProtocolLimits DEFAULTS = new BetaProtocolLimits(
            8 * 1024, 32 * 1024, 256, 128, 128, 64, 50, 8);

    public BetaProtocolLimits {
        if (handshakeBytes <= 0 || packetBytes < handshakeBytes
                || stringBytes <= 0 || canonicalIdBytes <= 0
                || listEntries <= 0 || mapEntries <= 0
                || mobEditorPageEntries <= 0 || nestingDepth <= 0) {
            throw new IllegalArgumentException("Protocol limits must be positive and ordered");
        }
    }
}
