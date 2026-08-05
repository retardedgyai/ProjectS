package io.github.gyai.projects.beta.activation.track1.equipment;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.item.compatibility.LegacyPdcSource;
import io.github.gyai.projects.mod.UnknownModEntry;

import java.util.List;
import java.util.Optional;

/** Callback-scoped source. Inspection never retains this object. */
public record EquipmentScanEntry(
        String slot,
        LegacyPdcSource legacySource,
        Optional<EquipmentItemV1> equipmentV1,
        List<UnknownModEntry> unknownMods,
        byte[] serializedBefore
) {
    public EquipmentScanEntry {
        if (slot == null || slot.isBlank() || slot.length() > 64 || legacySource == null) {
            throw new IllegalArgumentException("invalid scan entry");
        }
        equipmentV1 = equipmentV1 == null ? Optional.empty() : equipmentV1;
        unknownMods = List.copyOf(unknownMods == null ? List.of() : unknownMods);
        if (unknownMods.size() > 4) throw new IllegalArgumentException("too many unknown MODs");
        serializedBefore = serializedBefore == null ? new byte[0] : serializedBefore.clone();
        if (serializedBefore.length > 1_048_576) throw new IllegalArgumentException("item bytes oversized");
    }

    @Override public byte[] serializedBefore() { return serializedBefore.clone(); }
}
