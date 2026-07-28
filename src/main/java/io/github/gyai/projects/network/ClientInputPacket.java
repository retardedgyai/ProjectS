package io.github.gyai.projects.network;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public record ClientInputPacket(SkillInputType inputType) {
    public static final int MAX_PAYLOAD_BYTES = 32;

    public static Optional<ClientInputPacket> decode(byte[] payload) {
        if (payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            return Optional.empty();
        }

        try {
            String wireName = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(payload))
                    .toString();
            return SkillInputType.fromWireName(wireName).map(ClientInputPacket::new);
        } catch (CharacterCodingException ignored) {
            return Optional.empty();
        }
    }
}
