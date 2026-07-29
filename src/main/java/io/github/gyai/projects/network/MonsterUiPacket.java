package io.github.gyai.projects.network;

import io.github.gyai.projects.combat.skill.HardControlType;
import io.github.gyai.projects.monster.MonsterRank;
import io.github.gyai.projects.status.StatusEffectType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MonsterUiPacket(
        Operation operation,
        long sequence,
        long snapshotTick,
        List<Entry> entries
) {
    public static final String CHANNEL = "projects:monster_ui_v1";
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_MONSTERS_PER_PACKET = 16;
    public static final int MAX_STATUS_EFFECTS = 6;
    public static final int MAX_MONSTER_ID_BYTES = 64;
    public static final int MAX_DISPLAY_NAME_BYTES = 128;

    public MonsterUiPacket {
        Objects.requireNonNull(operation, "operation");
        entries = List.copyOf(entries);
        validateCount(operation, entries.size());
        entries.forEach(entry -> entry.validate(operation));
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(PROTOCOL_VERSION);
            output.writeByte(operation.ordinal());
            output.writeLong(sequence);
            output.writeLong(snapshotTick);
            output.writeByte(entries.size());
            for (Entry entry : entries) {
                output.writeInt(entry.networkEntityId());
                output.writeLong(entry.entityId().getMostSignificantBits());
                output.writeLong(entry.entityId().getLeastSignificantBits());
                if (operation == Operation.UPSERT) {
                    writeString(output, entry.monsterId(), MAX_MONSTER_ID_BYTES);
                    writeString(output, entry.displayName(), MAX_DISPLAY_NAME_BYTES);
                    output.writeByte(entry.rank().ordinal());
                    output.writeShort(entry.monsterLevel());
                    output.writeByte(entry.threatBand().ordinal());
                    output.writeDouble(entry.currentHealth());
                    output.writeDouble(entry.maximumHealth());
                    output.writeDouble(entry.displayRange());
                    output.writeBoolean(entry.hardControl() != null);
                    if (entry.hardControl() != null) {
                        output.writeByte(entry.hardControl().type().ordinal());
                        output.writeInt(entry.hardControl().totalTicks());
                        output.writeInt(entry.hardControl().remainingTicks());
                    }
                    output.writeByte(entry.statusEffects().size());
                    for (Status status : entry.statusEffects()) {
                        output.writeByte(status.type().ordinal());
                        output.writeFloat((float) status.strength());
                        output.writeInt(status.totalTicks());
                        output.writeInt(status.remainingTicks());
                    }
                }
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not encode monster UI packet", exception);
        }
    }

    public static MonsterUiPacket clear(long sequence, long snapshotTick) {
        return new MonsterUiPacket(
                Operation.CLEAR, sequence, snapshotTick, List.of());
    }

    public static void validateCount(Operation operation, int count) {
        if (count < 0 || count > MAX_MONSTERS_PER_PACKET) {
            throw new IllegalArgumentException("Too many monster UI entries");
        }
        if (operation == Operation.CLEAR && count != 0) {
            throw new IllegalArgumentException("Clear packets cannot have entries");
        }
    }

    private static void writeString(
            DataOutputStream output,
            String value,
            int maximumBytes
    ) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maximumBytes) {
            throw new IllegalArgumentException("Monster UI text is too long");
        }
        output.writeShort(encoded.length);
        output.write(encoded);
    }

    public enum Operation {
        UPSERT,
        REMOVE,
        CLEAR
    }

    public record Entry(
            int networkEntityId,
            UUID entityId,
            String monsterId,
            String displayName,
            MonsterRank rank,
            int monsterLevel,
            MonsterUiMath.ThreatBand threatBand,
            double currentHealth,
            double maximumHealth,
            double displayRange,
            HardControl hardControl,
            List<Status> statusEffects
    ) {
        public Entry {
            statusEffects = statusEffects == null
                    ? List.of()
                    : List.copyOf(statusEffects);
        }

        private void validate(Operation operation) {
            Objects.requireNonNull(entityId, "entityId");
            if (networkEntityId < 0) {
                throw new IllegalArgumentException("Invalid network entity id");
            }
            if (operation == Operation.REMOVE) {
                return;
            }
            Objects.requireNonNull(monsterId, "monsterId");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(rank, "rank");
            Objects.requireNonNull(threatBand, "threatBand");
            if (monsterId.getBytes(StandardCharsets.UTF_8).length
                    > MAX_MONSTER_ID_BYTES
                    || displayName.getBytes(StandardCharsets.UTF_8).length
                    > MAX_DISPLAY_NAME_BYTES
                    || monsterLevel < 1
                    || monsterLevel > 999
                    || !Double.isFinite(maximumHealth)
                    || maximumHealth <= 0.0
                    || !Double.isFinite(currentHealth)
                    || currentHealth < 0.0
                    || currentHealth > maximumHealth
                    || !Double.isFinite(displayRange)
                    || displayRange < 8.0
                    || displayRange > 128.0
                    || statusEffects.size() > MAX_STATUS_EFFECTS) {
                throw new IllegalArgumentException("Invalid monster UI entry");
            }
            if (hardControl != null) {
                hardControl.validate();
            }
            statusEffects.forEach(Status::validate);
        }

        public static Entry remove(int networkEntityId, UUID entityId) {
            return new Entry(
                    networkEntityId, entityId, "", "",
                    MonsterRank.NORMAL, 1,
                    MonsterUiMath.ThreatBand.WHITE,
                    0.0, 1.0, 48.0, null, List.of());
        }
    }

    public record HardControl(
            HardControlType type,
            int totalTicks,
            int remainingTicks
    ) {
        private void validate() {
            Objects.requireNonNull(type, "type");
            if (totalTicks <= 0
                    || remainingTicks < 0
                    || remainingTicks > totalTicks) {
                throw new IllegalArgumentException("Invalid hard control snapshot");
            }
        }
    }

    public record Status(
            StatusEffectType type,
            double strength,
            int totalTicks,
            int remainingTicks
    ) {
        private void validate() {
            Objects.requireNonNull(type, "type");
            if (!Double.isFinite(strength)
                    || strength < 0.0
                    || totalTicks <= 0
                    || remainingTicks < 0
                    || remainingTicks > totalTicks) {
                throw new IllegalArgumentException("Invalid status snapshot");
            }
        }
    }
}
