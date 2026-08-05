package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.EquipmentItemV1;

import java.util.Arrays;

/** Bounded deterministic equipment payload used only by the staging writer. */
public final class StagingEquipmentDocument {
    public static final int MAXIMUM_PAYLOAD_BYTES = 32_768;

    private final EquipmentItemV1 item;
    private final long revision;
    private final byte[] payload;

    public StagingEquipmentDocument(EquipmentItemV1 item, long revision, byte[] payload) {
        if (item == null || revision < 0 || payload == null
                || payload.length == 0 || payload.length > MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("invalid staging equipment document");
        }
        this.item = item;
        this.revision = revision;
        this.payload = payload.clone();
    }

    public EquipmentItemV1 item() {
        return item;
    }

    public long revision() {
        return revision;
    }

    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StagingEquipmentDocument value
                && revision == value.revision
                && item.equals(value.item)
                && Arrays.equals(payload, value.payload);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * item.hashCode() + Long.hashCode(revision))
                + Arrays.hashCode(payload);
    }
}
