package io.github.gyai.projects.mod;

import java.util.Arrays;

/** Opaque unsupported data is retained but can never contribute an effect. */
public final class UnknownModEntry implements ModSlotEntry {
    public static final int MAXIMUM_PAYLOAD_BYTES = 16_384;
    private final int slotIndex;
    private final String schemaId;
    private final int schemaVersion;
    private final String modId;
    private final byte[] payload;

    public UnknownModEntry(int slotIndex, int schemaVersion, byte[] payload) {
        this(slotIndex, "mod-definition", schemaVersion, "unknown", payload);
    }

    public UnknownModEntry(int slotIndex, String schemaId, int schemaVersion,
                           String modId, byte[] payload) {
        if (slotIndex < 0 || slotIndex > 3) throw new IllegalArgumentException("slotIndex must be 0..3");
        if (schemaId == null || schemaId.isBlank() || schemaId.length() > 64) {
            throw new IllegalArgumentException("schemaId is missing or oversized");
        }
        if (schemaVersion <= 0) throw new IllegalArgumentException("schemaVersion must be positive");
        if (modId == null || modId.isBlank() || modId.length() > 128) {
            throw new IllegalArgumentException("modId is missing or oversized");
        }
        if (payload == null || payload.length > MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("opaque payload is missing or oversized");
        }
        this.slotIndex = slotIndex;
        this.schemaId = schemaId;
        this.schemaVersion = schemaVersion;
        this.modId = modId;
        this.payload = payload.clone();
    }
    @Override public int slotIndex() { return slotIndex; }
    public String schemaId() { return schemaId; }
    public int schemaVersion() { return schemaVersion; }
    public String modId() { return modId; }
    public byte[] payload() { return payload.clone(); }
    @Override public boolean effectEnabled() { return false; }
    @Override public boolean equals(Object other) {
        return other instanceof UnknownModEntry value && slotIndex == value.slotIndex
                && schemaVersion == value.schemaVersion && schemaId.equals(value.schemaId)
                && modId.equals(value.modId) && Arrays.equals(payload, value.payload);
    }
    @Override public int hashCode() {
        int result = 31 * (31 * slotIndex + schemaVersion) + schemaId.hashCode();
        return 31 * (31 * result + modId.hashCode()) + Arrays.hashCode(payload);
    }
}
