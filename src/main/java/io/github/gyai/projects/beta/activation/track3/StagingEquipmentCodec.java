package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.BaseStatRoll;
import io.github.gyai.projects.equipment.BindingPolicy;
import io.github.gyai.projects.equipment.CrafterIdentity;
import io.github.gyai.projects.equipment.EquipmentCategory;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentModSlot;
import io.github.gyai.projects.equipment.EquipmentQuality;
import io.github.gyai.projects.equipment.EquipmentRarity;
import io.github.gyai.projects.equipment.EquipmentSlot;
import io.github.gyai.projects.equipment.EquipmentTier;
import io.github.gyai.projects.equipment.TradePolicy;
import io.github.gyai.projects.mod.ModEntry;
import io.github.gyai.projects.mod.ModRank;
import io.github.gyai.projects.mod.ModSlotEntry;
import io.github.gyai.projects.mod.ModSource;
import io.github.gyai.projects.mod.UnknownModEntry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Explicit binary codec; it does not use Java serialization or Bukkit objects. */
public final class StagingEquipmentCodec {
    private static final int MAGIC = 0x50534231;
    private static final int MAXIMUM_TEXT_BYTES = 512;

    public StagingEquipmentDocument encode(EquipmentItemV1 item, long revision) {
        if (item == null || revision < 0 || !StagingEconomyCatalog.isStagingItem(item.itemId())) {
            throw new IllegalArgumentException("only staging equipment can be encoded");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(item.schemaVersion());
            writeText(out, item.itemId());
            writeText(out, item.category().name());
            writeText(out, item.slot().name());
            writeText(out, item.tier().name());
            out.writeInt(item.itemLevel());
            writeText(out, item.rarity().name());
            writeText(out, item.quality().name());
            out.writeInt(item.enhancementLevel());
            out.writeBoolean(item.broken());
            writeText(out, item.binding().name());
            out.writeBoolean(item.tradePolicy().directTradeAllowed());
            out.writeBoolean(item.tradePolicy().marketAllowed());
            out.writeBoolean(item.tradePolicy().dismantleAllowed());
            out.writeBoolean(item.instanceId().isPresent());
            if (item.instanceId().isPresent()) writeUuid(out, item.instanceId().orElseThrow());
            out.writeBoolean(item.crafter().isPresent());
            if (item.crafter().isPresent()) {
                CrafterIdentity crafter = item.crafter().orElseThrow();
                writeUuid(out, crafter.playerId());
                writeText(out, crafter.displaySnapshot());
            }
            out.writeInt(item.baseStatRolls().size());
            for (BaseStatRoll roll : item.baseStatRolls()) {
                writeText(out, roll.statId());
                out.writeDouble(roll.value());
            }
            out.writeInt(item.modSlots().size());
            for (EquipmentModSlot slot : item.modSlots()) {
                out.writeInt(slot.index());
                out.writeBoolean(slot.entry().isPresent());
                if (slot.entry().isPresent()) writeMod(out, slot.entry().orElseThrow());
            }
            out.writeLong(revision);
            out.flush();
            byte[] payload = bytes.toByteArray();
            if (payload.length > StagingEquipmentDocument.MAXIMUM_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("staging equipment payload is oversized");
            }
            return new StagingEquipmentDocument(item, revision, payload);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public StagingEquipmentDocument decode(byte[] payload) {
        if (payload == null || payload.length == 0
                || payload.length > StagingEquipmentDocument.MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("invalid staging equipment payload");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("unknown staging payload");
            int schema = in.readInt();
            String itemId = readText(in);
            EquipmentCategory category = enumValue(EquipmentCategory.class, readText(in));
            EquipmentSlot slot = enumValue(EquipmentSlot.class, readText(in));
            EquipmentTier tier = enumValue(EquipmentTier.class, readText(in));
            int itemLevel = in.readInt();
            EquipmentRarity rarity = enumValue(EquipmentRarity.class, readText(in));
            EquipmentQuality quality = enumValue(EquipmentQuality.class, readText(in));
            int enhancement = in.readInt();
            boolean broken = in.readBoolean();
            BindingPolicy binding = enumValue(BindingPolicy.class, readText(in));
            TradePolicy trade = new TradePolicy(in.readBoolean(), in.readBoolean(), in.readBoolean());
            Optional<UUID> instance = in.readBoolean() ? Optional.of(readUuid(in)) : Optional.empty();
            Optional<CrafterIdentity> crafter = in.readBoolean()
                    ? Optional.of(new CrafterIdentity(readUuid(in), readText(in))) : Optional.empty();
            int rollCount = boundedCount(in.readInt(), 64, "base roll");
            ArrayList<BaseStatRoll> rolls = new ArrayList<>(rollCount);
            for (int index = 0; index < rollCount; index++) {
                rolls.add(new BaseStatRoll(readText(in), in.readDouble()));
            }
            int slotCount = boundedCount(in.readInt(), 4, "MOD slot");
            ArrayList<EquipmentModSlot> slots = new ArrayList<>(slotCount);
            for (int index = 0; index < slotCount; index++) {
                int slotIndex = in.readInt();
                slots.add(new EquipmentModSlot(slotIndex,
                        in.readBoolean() ? Optional.of(readMod(in, slotIndex)) : Optional.empty()));
            }
            long revision = in.readLong();
            if (in.available() != 0) throw new IllegalArgumentException("trailing staging payload");
            EquipmentItemV1 item = new EquipmentItemV1(
                    schema, itemId, category, slot, tier, itemLevel, rarity, quality,
                    List.copyOf(rolls), List.copyOf(slots), crafter, enhancement, broken,
                    binding, trade, instance);
            if (!StagingEconomyCatalog.isStagingItem(item.itemId()) || revision < 0) {
                throw new IllegalArgumentException("non-staging or invalid revision payload");
            }
            return new StagingEquipmentDocument(item, revision, payload);
        } catch (IOException | RuntimeException failure) {
            if (failure instanceof IllegalArgumentException argument) throw argument;
            throw new IllegalArgumentException("malformed staging equipment payload", failure);
        }
    }

    private static void writeMod(DataOutputStream out, ModSlotEntry entry) throws IOException {
        if (entry instanceof ModEntry mod) {
            out.writeByte(1);
            out.writeInt(mod.schemaVersion());
            writeText(out, mod.modId());
            writeText(out, mod.rank().name());
            out.writeDouble(mod.rolledValue());
            out.writeLong(mod.definitionRevision());
            writeText(out, mod.source().definitionPackId());
            writeText(out, mod.source().operationSourceId());
        } else if (entry instanceof UnknownModEntry unknown) {
            out.writeByte(2);
            writeText(out, unknown.schemaId());
            out.writeInt(unknown.schemaVersion());
            writeText(out, unknown.modId());
            byte[] opaque = unknown.payload();
            out.writeInt(opaque.length);
            out.write(opaque);
        } else {
            throw new IllegalArgumentException("unsupported MOD entry implementation");
        }
    }

    private static ModSlotEntry readMod(DataInputStream in, int slotIndex) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 1 -> new ModEntry(in.readInt(), readText(in),
                    enumValue(ModRank.class, readText(in)), in.readDouble(), in.readLong(),
                    new ModSource(readText(in), readText(in)), slotIndex);
            case 2 -> {
                String schemaId = readText(in);
                int schemaVersion = in.readInt();
                String modId = readText(in);
                int length = boundedCount(in.readInt(), UnknownModEntry.MAXIMUM_PAYLOAD_BYTES,
                        "opaque MOD payload");
                byte[] opaque = in.readNBytes(length);
                if (opaque.length != length) throw new IllegalArgumentException("truncated MOD payload");
                yield new UnknownModEntry(slotIndex, schemaId, schemaVersion, modId, opaque);
            }
            default -> throw new IllegalArgumentException("unknown MOD payload kind");
        };
    }

    private static void writeText(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAXIMUM_TEXT_BYTES) throw new IllegalArgumentException("text is oversized");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readText(DataInputStream in) throws IOException {
        int length = boundedCount(in.readInt(), MAXIMUM_TEXT_BYTES, "text");
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new IllegalArgumentException("truncated text");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeUuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static int boundedCount(int value, int maximum, String label) {
        if (value < 0 || value > maximum) throw new IllegalArgumentException(label + " count is invalid");
        return value;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("unknown " + type.getSimpleName(), failure);
        }
    }
}
