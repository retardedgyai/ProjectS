package io.github.gyai.projects.network;

import io.github.gyai.projects.combat.damage.DamageType;
import io.github.gyai.projects.monster.editor.HeadDefinition;
import io.github.gyai.projects.monster.editor.MobAiDefinition;
import io.github.gyai.projects.monster.editor.MobAppearanceDefinition;
import io.github.gyai.projects.monster.editor.MobBasicAttackDefinition;
import io.github.gyai.projects.monster.editor.MobDefinition;
import io.github.gyai.projects.monster.editor.MobEquipmentEntry;
import io.github.gyai.projects.monster.editor.MobStatsDefinition;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;

public final class MobEditorPacketIO {
    public static final int VERSION = 1;
    public static final int MAX_PAYLOAD_BYTES = 48 * 1024;
    public static final int MAX_MOBS = 128;
    public static final int MAX_HEADS = 64;

    private MobEditorPacketIO() {
    }

    public static MobDefinition readMob(DataInputStream input) throws IOException {
        int schema = input.readUnsignedByte();
        long revision = input.readLong();
        String id = readString(input, 64);
        String displayName = readString(input, 128);
        String entityType = readString(input, 64);
        MobDefinition.Category category = readEnum(
                input, MobDefinition.Category.class);
        boolean enabled = input.readBoolean();
        int level = input.readUnsignedShort();
        MobDefinition.NameplateMode nameplate = readEnum(
                input, MobDefinition.NameplateMode.class);
        List<String> tags = readStrings(input, 32, 32);
        MobStatsDefinition stats = new MobStatsDefinition(
                input.readDouble(), input.readDouble(), input.readDouble(),
                input.readDouble(), input.readDouble(), input.readDouble(),
                input.readDouble(), input.readDouble(), input.readDouble(),
                input.readDouble());
        MobBasicAttackDefinition attack = new MobBasicAttackDefinition(
                readEnum(input, DamageType.class), input.readDouble(),
                input.readDouble(), input.readDouble(), input.readDouble(),
                input.readDouble(), input.readBoolean());
        MobAiDefinition ai = new MobAiDefinition(
                readEnum(input, MobAiDefinition.Preset.class),
                readEnum(input, MobAiDefinition.TargetPriority.class),
                input.readDouble(), input.readDouble(), input.readDouble(),
                input.readDouble(), input.readDouble(), input.readBoolean(),
                input.readBoolean(), input.readBoolean(), input.readBoolean());
        double scale = input.readDouble();
        MobAppearanceDefinition.Age age = readEnum(
                input, MobAppearanceDefinition.Age.class);
        boolean glowing = input.readBoolean();
        String glowingColor = readString(input, 32);
        int variantCount = input.readUnsignedByte();
        if (variantCount > 16) throw new IOException("Too many variants");
        LinkedHashMap<String, String> variants = new LinkedHashMap<>();
        for (int index = 0; index < variantCount; index++) {
            String key = readString(input, 32);
            if (variants.putIfAbsent(key, readString(input, 64)) != null) {
                throw new IOException("Duplicate variant");
            }
        }
        EnumMap<MobAppearanceDefinition.Slot, MobEquipmentEntry> equipment =
                new EnumMap<>(MobAppearanceDefinition.Slot.class);
        for (MobAppearanceDefinition.Slot slot : MobAppearanceDefinition.Slot.values()) {
            equipment.put(slot, new MobEquipmentEntry(
                    readEnum(input, MobEquipmentEntry.SourceType.class),
                    readString(input, 64), readString(input, 64),
                    readString(input, 16), input.readBoolean(),
                    input.readBoolean(), input.readBoolean()));
        }
        return new MobDefinition(
                schema, revision, id, displayName, entityType, category,
                enabled, level, nameplate, tags, stats, attack, ai,
                new MobAppearanceDefinition(
                        scale, age, glowing, glowingColor, variants, equipment));
    }

    public static void writeMob(DataOutputStream output, MobDefinition value)
            throws IOException {
        output.writeByte(value.schemaVersion());
        output.writeLong(value.revision());
        writeString(output, value.id(), 64);
        writeString(output, value.displayName(), 128);
        writeString(output, value.entityType(), 64);
        writeEnum(output, value.category());
        output.writeBoolean(value.enabled());
        output.writeShort(value.level());
        writeEnum(output, value.nameplateMode());
        writeStrings(output, value.tags(), 32, 32);
        MobStatsDefinition stats = value.stats();
        output.writeDouble(stats.maxHealth());
        output.writeDouble(stats.physicalAttack());
        output.writeDouble(stats.magicalAttack());
        output.writeDouble(stats.physicalDefense());
        output.writeDouble(stats.magicalDefense());
        output.writeDouble(stats.movementSpeed());
        output.writeDouble(stats.attackSpeed());
        output.writeDouble(stats.criticalChance());
        output.writeDouble(stats.criticalDamage());
        output.writeDouble(stats.damageReduction());
        MobBasicAttackDefinition attack = value.basicAttack();
        writeEnum(output, attack.damageType());
        output.writeDouble(attack.fixedDamage());
        output.writeDouble(attack.coefficient());
        output.writeDouble(attack.intervalSeconds());
        output.writeDouble(attack.range());
        output.writeDouble(attack.knockback());
        output.writeBoolean(attack.criticalAllowed());
        MobAiDefinition ai = value.ai();
        writeEnum(output, ai.preset());
        writeEnum(output, ai.targetPriority());
        output.writeDouble(ai.aggroRange());
        output.writeDouble(ai.chaseRange());
        output.writeDouble(ai.leashRange());
        output.writeDouble(ai.attackRange());
        output.writeDouble(ai.targetRefreshSeconds());
        output.writeBoolean(ai.returnHome());
        output.writeBoolean(ai.resetHealthOnReturn());
        output.writeBoolean(ai.avoidFalls());
        output.writeBoolean(ai.avoidWater());
        MobAppearanceDefinition appearance = value.appearance();
        output.writeDouble(appearance.scale());
        writeEnum(output, appearance.age());
        output.writeBoolean(appearance.glowing());
        writeString(output, appearance.glowingColor(), 32);
        if (appearance.variants().size() > 16) throw new IOException("Too many variants");
        output.writeByte(appearance.variants().size());
        for (var entry : appearance.variants().entrySet()) {
            writeString(output, entry.getKey(), 32);
            writeString(output, entry.getValue(), 64);
        }
        for (MobAppearanceDefinition.Slot slot : MobAppearanceDefinition.Slot.values()) {
            MobEquipmentEntry entry = appearance.equipment().get(slot);
            writeEnum(output, entry.sourceType());
            writeString(output, entry.referenceId(), 64);
            writeString(output, entry.material(), 64);
            writeString(output, entry.color(), 16);
            output.writeBoolean(entry.glint());
            output.writeBoolean(entry.visible());
            output.writeBoolean(entry.visualOnly());
        }
    }

    public static HeadDefinition readHead(DataInputStream input) throws IOException {
        return new HeadDefinition(
                input.readUnsignedByte(), input.readLong(),
                readString(input, 64), readString(input, 128),
                readEnum(input, HeadDefinition.SourceType.class),
                readString(input, 64), readString(input, 16_384),
                readString(input, 64), readStrings(input, 32, 32),
                input.readBoolean(), readString(input, 256));
    }

    public static void writeHead(DataOutputStream output, HeadDefinition value)
            throws IOException {
        output.writeByte(value.schemaVersion());
        output.writeLong(value.revision());
        writeString(output, value.id(), 64);
        writeString(output, value.displayName(), 128);
        writeEnum(output, value.sourceType());
        writeString(output, value.playerName(), 64);
        String textureValue = value.sourceType() == HeadDefinition.SourceType.TEXTURE_VALUE
                ? io.github.gyai.projects.monster.editor.HeadDefinitionValidator
                .canonicalTextureValue(value.textureValue()) : value.textureValue();
        writeString(output, textureValue, 16_384);
        writeString(output, value.projectsItemId(), 64);
        writeStrings(output, value.tags(), 32, 32);
        output.writeBoolean(value.favorite());
        writeString(output, value.sourceNote(), 256);
    }

    public static String readString(DataInputStream input, int maximumBytes)
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
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException exception) {
            throw new IOException("Invalid UTF-8", exception);
        }
    }

    public static void writeString(
            DataOutputStream output,
            String value,
            int maximumBytes
    ) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximumBytes) throw new IOException("String is too long");
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    public static String boundedUtf8(String value, int maximumBytes) {
        if (value == null || value.isEmpty()) return "";
        if (value.getBytes(StandardCharsets.UTF_8).length <= maximumBytes) return value;
        String suffix = "…";
        int budget = maximumBytes - suffix.getBytes(StandardCharsets.UTF_8).length;
        StringBuilder result = new StringBuilder();
        int used = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String next = new String(Character.toChars(codePoint));
            int bytes = next.getBytes(StandardCharsets.UTF_8).length;
            if (used + bytes > budget) break;
            result.append(next);
            used += bytes;
            offset += Character.charCount(codePoint);
        }
        return result.append(suffix).toString();
    }

    private static <T extends Enum<T>> T readEnum(
            DataInputStream input,
            Class<T> type
    ) throws IOException {
        String value = readString(input, 32);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid enum", exception);
        }
    }

    private static void writeEnum(DataOutputStream output, Enum<?> value)
            throws IOException {
        if (value == null) throw new IOException("Missing enum");
        writeString(output, value.name(), 32);
    }

    private static List<String> readStrings(
            DataInputStream input,
            int maximumCount,
            int maximumBytes
    ) throws IOException {
        int count = input.readUnsignedByte();
        if (count > maximumCount) throw new IOException("Too many strings");
        ArrayList<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(readString(input, maximumBytes));
        }
        return List.copyOf(values);
    }

    private static void writeStrings(
            DataOutputStream output,
            List<String> values,
            int maximumCount,
            int maximumBytes
    ) throws IOException {
        if (values.size() > maximumCount) throw new IOException("Too many strings");
        output.writeByte(values.size());
        for (String value : values) writeString(output, value, maximumBytes);
    }
}
