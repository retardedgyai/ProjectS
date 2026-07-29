package io.github.gyai.projects.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class BalancePacketIO {
    static final int VERSION = 1;
    static final int MAX_PAYLOAD_BYTES = 8_192;
    static final int MAX_STRING_BYTES = 256;

    private BalancePacketIO() {
    }

    static String readString(DataInputStream input, int maximumBytes)
            throws IOException {
        int length = input.readUnsignedShort();
        if (length > maximumBytes || length > input.available()) {
            throw new IOException("String is too long");
        }
        byte[] bytes = input.readNBytes(length);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (java.nio.charset.CharacterCodingException exception) {
            throw new IOException("Invalid UTF-8", exception);
        }
    }

    static void writeString(
            DataOutputStream output,
            String value,
            int maximumBytes
    ) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximumBytes) {
            throw new IOException("String is too long");
        }
        output.writeShort(bytes.length);
        output.write(bytes);
    }
}
