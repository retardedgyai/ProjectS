package io.github.gyai.projects.monster.editor;

import java.util.EnumMap;
import java.util.Map;

public record MobAppearanceDefinition(
        double scale,
        Age age,
        boolean glowing,
        String glowingColor,
        Map<String, String> variants,
        Map<Slot, MobEquipmentEntry> equipment
) {
    public enum Age {
        ADULT,
        BABY
    }

    public enum Slot {
        HEAD,
        CHEST,
        LEGS,
        FEET,
        MAIN_HAND,
        OFF_HAND
    }

    public MobAppearanceDefinition {
        variants = variants == null ? Map.of() : Map.copyOf(variants);
        EnumMap<Slot, MobEquipmentEntry> safe = new EnumMap<>(Slot.class);
        for (Slot slot : Slot.values()) {
            safe.put(slot, equipment == null
                    ? MobEquipmentEntry.empty()
                    : equipment.getOrDefault(slot, MobEquipmentEntry.empty()));
        }
        equipment = Map.copyOf(safe);
    }

    public static MobAppearanceDefinition defaults() {
        return new MobAppearanceDefinition(
                1, Age.ADULT, false, "WHITE", Map.of(), Map.of());
    }
}
