package io.github.gyai.projects.network;

import io.github.gyai.projects.monster.editor.HeadDefinition;
import io.github.gyai.projects.monster.editor.MobDefinition;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

public record MobEditorStatePacket(
        boolean permitted,
        boolean success,
        boolean revisionConflict,
        String message,
        List<MobDefinition> mobs,
        MobDefinition detail,
        List<HeadDefinition> heads,
        HeadDefinition headDetail
) {
    public static final String CHANNEL = "projects:mob_editor_state_v1";

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(MobEditorPacketIO.VERSION);
                output.writeBoolean(permitted);
                output.writeBoolean(success);
                output.writeBoolean(revisionConflict);
                MobEditorPacketIO.writeString(output, message, 256);
                if (mobs.size() > MobEditorPacketIO.MAX_MOBS) {
                    throw new IOException("Too many mobs");
                }
                output.writeShort(mobs.size());
                for (MobDefinition mob : mobs) writeMobSummary(output, mob);
                output.writeBoolean(detail != null);
                if (detail != null) MobEditorPacketIO.writeMob(output, detail);
                if (heads.size() > MobEditorPacketIO.MAX_HEADS) {
                    throw new IOException("Too many heads");
                }
                output.writeByte(heads.size());
                for (HeadDefinition head : heads) writeHeadSummary(output, head);
                output.writeBoolean(headDetail != null);
                if (headDetail != null) MobEditorPacketIO.writeHead(output, headDetail);
            }
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MobEditorPacketIO.MAX_PAYLOAD_BYTES) {
                throw new IOException("State payload is too large");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Mob Editor state encoding failed", exception);
        }
    }

    private static void writeMobSummary(DataOutputStream output, MobDefinition mob)
            throws IOException {
        MobEditorPacketIO.writeString(output, mob.id(), 64);
        MobEditorPacketIO.writeString(output, mob.displayName(), 128);
        MobEditorPacketIO.writeString(output, mob.entityType(), 64);
        MobEditorPacketIO.writeString(output, mob.category().name(), 32);
        output.writeBoolean(mob.enabled());
        output.writeLong(mob.revision());
        output.writeByte(Math.min(8, mob.tags().size()));
        for (String tag : mob.tags().stream().limit(8).toList()) {
            MobEditorPacketIO.writeString(output, tag, 32);
        }
    }

    private static void writeHeadSummary(DataOutputStream output, HeadDefinition head)
            throws IOException {
        MobEditorPacketIO.writeString(output, head.id(), 64);
        MobEditorPacketIO.writeString(output, head.displayName(), 128);
        MobEditorPacketIO.writeString(output, head.sourceType().name(), 32);
        output.writeBoolean(head.favorite());
        output.writeByte(Math.min(8, head.tags().size()));
        for (String tag : head.tags().stream().limit(8).toList()) {
            MobEditorPacketIO.writeString(output, tag, 32);
        }
    }
}
