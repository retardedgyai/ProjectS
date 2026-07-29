package io.github.gyai.projects.network;

import io.github.gyai.projects.combat.classsystem.WarriorLoadout;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public record WarriorLoadoutStatePacket(
        boolean available,
        boolean inCombat,
        boolean success,
        String classId,
        String reason,
        WarriorLoadout loadout
) {
    public static final String CHANNEL = "projects:loadout_state_v1";
    private static final int PROTOCOL_VERSION = 1;

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(PROTOCOL_VERSION);
            output.writeBoolean(available);
            output.writeBoolean(inCombat);
            output.writeBoolean(success);
            writeString(output, classId);
            writeString(output, reason);
            writeString(output, loadout.q());
            writeString(output, loadout.e());
            writeString(output, loadout.r());
            writeString(output, loadout.f());
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to encode Warrior loadout", exception);
        }
    }

    private static void writeString(
            DataOutputStream output,
            String value
    ) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > 255) {
            throw new IllegalArgumentException("Loadout text is too long");
        }
        output.writeByte(encoded.length);
        output.write(encoded);
    }
}
