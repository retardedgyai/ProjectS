package io.github.gyai.projects.network;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public record HudStatePacket(
        boolean visible,
        String className,
        String resourceName,
        float resourceCurrent,
        float resourceMaximum,
        List<SkillSlotState> slots
) {
    public static final String CHANNEL = "projects:hud_state";
    private static final int PROTOCOL_VERSION = 1;
    private static final int SLOT_COUNT = 4;

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(PROTOCOL_VERSION);
            output.writeBoolean(visible);
            writeString(output, className);
            writeString(output, resourceName);
            output.writeFloat(resourceCurrent);
            output.writeFloat(resourceMaximum);
            for (int index = 0; index < SLOT_COUNT; index++) {
                SkillSlotState slot = index < slots.size()
                        ? slots.get(index)
                        : SkillSlotState.locked("", "");
                writeString(output, slot.key());
                writeString(output, slot.name());
                output.writeFloat(slot.cooldownSeconds());
                output.writeByte(Math.clamp(slot.charges(), 0, 127));
                output.writeByte(Math.clamp(slot.stacks(), 0, 127));
                output.writeBoolean(slot.enabled());
                output.writeBoolean(slot.active());
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode HUD state", exception);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > 255) {
            throw new IllegalArgumentException("HUD text is too long");
        }
        output.writeByte(encoded.length);
        output.write(encoded);
    }

    public record SkillSlotState(
            String key,
            String name,
            float cooldownSeconds,
            int charges,
            int stacks,
            boolean enabled,
            boolean active
    ) {
        public static SkillSlotState locked(String key, String name) {
            return new SkillSlotState(key, name, 0.0f, 0, 0, false, false);
        }
    }
}
