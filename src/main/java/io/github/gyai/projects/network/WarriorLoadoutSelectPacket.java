package io.github.gyai.projects.network;

import io.github.gyai.projects.combat.classsystem.WarriorLoadoutSlot;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public record WarriorLoadoutSelectPacket(
        WarriorLoadoutSlot slot,
        String skillId
) {
    public static final String CHANNEL = "projects:loadout_sel_v1";
    private static final int PROTOCOL_VERSION = 1;
    private static final int MAXIMUM_BYTES = 80;

    public static Optional<WarriorLoadoutSelectPacket> decode(
            byte[] payload
    ) {
        if (payload == null
                || payload.length < 4
                || payload.length > MAXIMUM_BYTES) {
            return Optional.empty();
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            if (input.readUnsignedByte() != PROTOCOL_VERSION) {
                return Optional.empty();
            }
            int slotId = input.readUnsignedByte();
            if (slotId >= WarriorLoadoutSlot.values().length) {
                return Optional.empty();
            }
            int length = input.readUnsignedByte();
            if (length < 1 || length > 64 || input.available() != length) {
                return Optional.empty();
            }
            byte[] text = input.readNBytes(length);
            String skillId = new String(text, StandardCharsets.UTF_8);
            if (!skillId.matches("[a-z0-9_]+")) {
                return Optional.empty();
            }
            return Optional.of(new WarriorLoadoutSelectPacket(
                    WarriorLoadoutSlot.values()[slotId], skillId));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }
}
