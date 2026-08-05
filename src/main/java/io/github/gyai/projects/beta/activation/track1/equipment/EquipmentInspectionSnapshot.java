package io.github.gyai.projects.beta.activation.track1.equipment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EquipmentInspectionSnapshot(
        UUID playerId,
        Instant inspectedAt,
        List<EquipmentItemInspection> items,
        int readableLegacyItems,
        int validV1Items,
        int isolatedUnknownMods
) {
    public static final int MAXIMUM_ITEMS = 64;

    public EquipmentInspectionSnapshot {
        if (playerId == null || inspectedAt == null) throw new IllegalArgumentException("identity required");
        items = List.copyOf(items == null ? List.of() : items);
        if (items.size() > MAXIMUM_ITEMS || readableLegacyItems < 0
                || validV1Items < 0 || isolatedUnknownMods < 0) {
            throw new IllegalArgumentException("invalid inspection counts");
        }
    }
}
