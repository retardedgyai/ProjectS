package io.github.gyai.projects.network;

import io.github.gyai.projects.monster.editor.MobDefinition;
import io.github.gyai.projects.manager.MonsterManager;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/** Pure, bounded request boundary used before the Bukkit channel dispatches v2. */
public final class MobEditorV2RequestDecoder {
    private MobEditorV2RequestDecoder() { }

    public static Request decode(byte[] payload) throws IOException {
        if (payload == null || payload.length < 2
                || payload.length > MobEditorV2PacketIO.MAX_PAYLOAD_BYTES) {
            throw new IOException("Invalid payload size");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readUnsignedByte() != MobEditorV2PacketIO.VERSION) {
                throw new IOException("Unsupported version");
            }
            int operation = input.readUnsignedByte();
            MobDefinition mob = null;
            boolean cursor = false;
            switch (operation) {
                case MobEditorChannel.UPDATE_DRAFT,
                        MobEditorChannel.VALIDATE_DRAFT,
                        MobEditorChannel.SAVE_DRAFT -> mob = MobEditorV2PacketIO.readMob(input);
                case MobEditorChannel.TEST_SPAWN -> {
                    mob = MobEditorV2PacketIO.readMob(input);
                    cursor = input.readBoolean();
                }
                case MobEditorChannel.OPEN,
                        MobEditorChannel.APPLY_DEFINITION,
                        MobEditorChannel.DESPAWN_TEST_MOBS,
                        MobEditorChannel.RELOAD,
                        MobEditorChannel.CLOSE,
                        MobEditorChannel.DESPAWN_ALL_TEST_MOBS -> { }
                case MobEditorChannel.REQUEST_DETAIL,
                        MobEditorChannel.CREATE_DRAFT -> MobEditorPacketIO.readString(input, 64);
                case MobEditorChannel.CONTROL_TEST_MOBS -> {
                    int control = input.readUnsignedByte();
                    if (control >= MonsterManager.TestControl.values().length) {
                        throw new IOException("Invalid test control");
                    }
                }
                case MobEditorChannel.REQUEST_HEAD_LIST,
                        MobEditorChannel.REQUEST_MOB_LIST -> {
                    MobEditorPacketIO.readString(input, 64);
                    input.readUnsignedShort();
                }
                case MobEditorChannel.REQUEST_HEAD_DETAIL -> {
                    MobEditorPacketIO.readString(input, 64);
                    MobEditorPacketIO.readString(input, 64);
                    input.readUnsignedShort();
                }
                case MobEditorChannel.CREATE_HEAD -> MobEditorV2PacketIO.readHead(input);
                case MobEditorChannel.UPDATE_HEAD_FAVORITE -> {
                    MobEditorPacketIO.readString(input, 64);
                    String revision = MobEditorPacketIO.readString(input, 32);
                    try {
                        Long.parseLong(revision);
                    } catch (NumberFormatException exception) {
                        throw new IOException("Invalid revision", exception);
                    }
                    input.readBoolean();
                }
                default -> throw new IOException("Unknown operation");
            }
            if (input.available() != 0) {
                throw new IOException("Trailing bytes");
            }
            return new Request(operation, mob, cursor);
        }
    }
    public record Request(int operation, MobDefinition mob, boolean cursor) { }
}
