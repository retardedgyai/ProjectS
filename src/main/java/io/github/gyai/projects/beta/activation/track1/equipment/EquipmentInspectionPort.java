package io.github.gyai.projects.beta.activation.track1.equipment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EquipmentInspectionPort {
    EquipmentInspectionSnapshot inspect(UUID playerId, List<EquipmentScanEntry> entries);

    Optional<EquipmentInspectionSnapshot> latest(UUID playerId);
}
