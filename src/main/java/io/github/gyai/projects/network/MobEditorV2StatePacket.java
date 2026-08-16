package io.github.gyai.projects.network;

import io.github.gyai.projects.monster.editor.HeadDefinition;
import io.github.gyai.projects.monster.editor.MobDefinition;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/** Clientbound production editor v2 state, including the registry catalog. */
public record MobEditorV2StatePacket(boolean permitted, boolean success, boolean revisionConflict,
                                     String message, List<MobDefinition> mobs, MobDefinition detail,
                                     List<HeadDefinition> heads, HeadDefinition headDetail,
                                     List<MobEditorV2PacketIO.CatalogEntry> catalog) {
    public static final String CHANNEL = "projects:mob_editor_state_v2";
    private static final int MAX_SUMMARY_TAGS = 8;

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(MobEditorV2PacketIO.VERSION);
                out.writeBoolean(permitted); out.writeBoolean(success); out.writeBoolean(revisionConflict);
                MobEditorPacketIO.writeString(out, message, 256);
                MobEditorV2PacketIO.writeCatalog(out, catalog);
                if (mobs.size() > MobEditorPacketIO.MAX_MOBS || heads.size() > MobEditorPacketIO.MAX_HEADS) throw new IOException("Too many summaries");
                out.writeShort(mobs.size());
                for (MobDefinition mob : mobs) {
                    writeMobSummary(out, mob);
                }
                out.writeBoolean(detail != null); if (detail != null) MobEditorV2PacketIO.writeMob(out, detail);
                out.writeByte(heads.size());
                for (HeadDefinition head : heads) {
                    writeHeadSummary(out, head);
                }
                out.writeBoolean(headDetail != null); if (headDetail != null) MobEditorV2PacketIO.writeHead(out, headDetail);
            }
            byte[] result = bytes.toByteArray();
            if (result.length > MobEditorV2PacketIO.MAX_PAYLOAD_BYTES) throw new IOException("State payload too large");
            return result;
        } catch (IOException e) { throw new IllegalStateException("Mob Editor v2 state encoding failed", e); }
    }

    private static void writeMobSummary(DataOutputStream output, MobDefinition mob)
            throws IOException {
        MobEditorV2PacketIO.requireUniqueTags(mob.tags(), "Duplicate mob tag");
        MobEditorPacketIO.writeString(output, mob.id(), 64);
        MobEditorPacketIO.writeString(output, mob.displayName(), 128);
        MobEditorPacketIO.writeString(output, mob.entityType(), 64);
        MobEditorPacketIO.writeString(output, mob.category().name(), 32);
        output.writeBoolean(mob.enabled());
        output.writeLong(mob.revision());
        writeSummaryTags(output, mob.tags());
    }

    private static void writeHeadSummary(DataOutputStream output, HeadDefinition head)
            throws IOException {
        MobEditorV2PacketIO.requireUniqueTags(head.tags(), "Duplicate head tag");
        MobEditorPacketIO.writeString(output, head.id(), 64);
        MobEditorPacketIO.writeString(output, head.displayName(), 128);
        MobEditorPacketIO.writeString(output, head.sourceType().name(), 32);
        output.writeBoolean(head.favorite());
        writeSummaryTags(output, head.tags());
    }

    private static void writeSummaryTags(DataOutputStream output, List<String> tags)
            throws IOException {
        int count = Math.min(MAX_SUMMARY_TAGS, tags.size());
        output.writeByte(count);
        for (int index = 0; index < count; index++) {
            MobEditorPacketIO.writeString(output, tags.get(index), 32);
        }
    }
}
