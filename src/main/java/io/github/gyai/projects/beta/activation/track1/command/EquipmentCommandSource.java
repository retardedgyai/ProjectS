package io.github.gyai.projects.beta.activation.track1.command;

import io.github.gyai.projects.beta.activation.track1.equipment.EquipmentScanEntry;

import java.util.List;
import java.util.UUID;

/** Callback supplied by the future command integration; implementations scan synchronously. */
public interface EquipmentCommandSource {
    List<EquipmentScanEntry> scan(UUID playerId);
}
