package io.github.gyai.projects.beta.activation.track1.equipment;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentValidation;
import io.github.gyai.projects.item.compatibility.LegacyItemReadResult;

import java.util.List;
import java.util.Optional;

public record EquipmentItemInspection(
        String slot,
        LegacyItemReadResult legacy,
        Optional<EquipmentItemV1> projection,
        EquipmentValidation validation,
        List<String> isolatedUnknownModIds,
        String sourceFingerprint
) {
    public EquipmentItemInspection {
        if (slot == null || legacy == null || validation == null) {
            throw new IllegalArgumentException("invalid item inspection");
        }
        projection = projection == null ? Optional.empty() : projection;
        isolatedUnknownModIds = List.copyOf(
                isolatedUnknownModIds == null ? List.of() : isolatedUnknownModIds);
        sourceFingerprint = sourceFingerprint == null ? "" : sourceFingerprint;
    }
}
