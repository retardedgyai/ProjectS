package io.github.gyai.projects.equipment.operation;

import io.github.gyai.projects.equipment.MetadataIds;

import java.util.LinkedHashMap;
import java.util.Map;

/** Future-safe boundary for display names, engravings, and other bounded text. */
public record EquipmentExtensionSnapshot(Map<String, String> values) {
    public EquipmentExtensionSnapshot {
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        if (values != null) {
            if (values.size() > 16) throw new IllegalArgumentException("too many extension values");
            values.forEach((key, value) -> copy.put(
                    MetadataIds.requireCanonical("extension key", key),
                    MetadataIds.requireBoundedText("extension value", value, 256)));
        }
        values = Map.copyOf(copy);
    }

    public static EquipmentExtensionSnapshot empty() {
        return new EquipmentExtensionSnapshot(Map.of());
    }
}
