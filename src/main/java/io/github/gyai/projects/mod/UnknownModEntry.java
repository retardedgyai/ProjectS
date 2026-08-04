package io.github.gyai.projects.mod;

import java.util.Arrays;

/** Opaque unsupported data is retained but can never contribute an effect. */
public final class UnknownModEntry implements ModSlotEntry {
    public static final int MAXIMUM_PAYLOAD_BYTES = 16_384;
    private final int slotIndex;
    private final int schemaVersion;
    private final byte[] payload;

    public UnknownModEntry(int slotIndex, int schemaVersion, byte[] payload) {
        if (slotIndex < 0 || slotIndex > 3) throw new IllegalArgumentException("slotIndex must be 0..3");
        if (schemaVersion <= 0) throw new IllegalArgumentException("schemaVersion must be positive");
        if (payload == null || payload.length > MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("opaque payload is missing or oversized");
        }
        this.slotIndex = slotIndex;
        this.schemaVersion = schemaVersion;
        this.payload = payload.clone();
    }
    @Override public int slotIndex() { return slotIndex; }
    public int schemaVersion() { return schemaVersion; }
    public byte[] payload() { return payload.clone(); }
    @Override public boolean effectEnabled() { return false; }
    @Override public boolean equals(Object other) {
        return other instanceof UnknownModEntry value && slotIndex == value.slotIndex
                && schemaVersion == value.schemaVersion && Arrays.equals(payload, value.payload);
    }
    @Override public int hashCode() { return 31 * (31 * slotIndex + schemaVersion) + Arrays.hashCode(payload); }
}
