package io.github.gyai.projects.mod;

import io.github.gyai.projects.equipment.MetadataIds;
import io.github.gyai.projects.schema.SchemaId;
import io.github.gyai.projects.schema.SchemaVersions;

import java.util.Objects;

public record ModEntry(
        int schemaVersion,
        String modId,
        ModRank rank,
        double rolledValue,
        long definitionRevision,
        ModSource source,
        int slotIndex
) implements ModSlotEntry {
    public ModEntry {
        if (!SchemaVersions.isSupported(SchemaId.MOD_DEFINITION, schemaVersion)) {
            throw new IllegalArgumentException("unsupported MOD schema version");
        }
        modId = MetadataIds.requireCanonical("modId", modId);
        Objects.requireNonNull(rank, "rank");
        if (!Double.isFinite(rolledValue)) throw new IllegalArgumentException("rolledValue must be finite");
        if (definitionRevision < 0) throw new IllegalArgumentException("definitionRevision must be non-negative");
        Objects.requireNonNull(source, "source");
        if (slotIndex < 0 || slotIndex > 3) throw new IllegalArgumentException("slotIndex must be 0..3");
    }
    @Override public boolean effectEnabled() { return true; }
}
