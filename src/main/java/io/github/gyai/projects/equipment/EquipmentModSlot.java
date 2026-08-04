package io.github.gyai.projects.equipment;

import io.github.gyai.projects.mod.ModSlotEntry;

import java.util.Optional;

public record EquipmentModSlot(int index, Optional<ModSlotEntry> entry) {
    public EquipmentModSlot {
        if (index < 0 || index > 3) throw new IllegalArgumentException("index must be 0..3");
        entry = entry == null ? Optional.empty() : entry;
        if (entry.isPresent() && entry.get().slotIndex() != index) {
            throw new IllegalArgumentException("entry slot index does not match container");
        }
    }
    public static EquipmentModSlot empty(int index) { return new EquipmentModSlot(index, Optional.empty()); }
}
