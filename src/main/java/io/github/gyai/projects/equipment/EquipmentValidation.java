package io.github.gyai.projects.equipment;

import io.github.gyai.projects.schema.SchemaId;
import io.github.gyai.projects.schema.SchemaVersions;
import io.github.gyai.projects.mod.ModEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public record EquipmentValidation(boolean valid, List<String> issues) {
    public EquipmentValidation { issues = List.copyOf(issues); }

    public static EquipmentValidation validate(EquipmentView view) {
        ArrayList<String> issues = new ArrayList<>();
        if (view == null) return new EquipmentValidation(false, List.of("view"));
        if (!SchemaVersions.isSupported(SchemaId.EQUIPMENT_ITEM, view.schemaVersion())) issues.add("schemaVersion");
        if (view.itemId() == null || view.itemId().isBlank() || view.itemId().length() > 128) issues.add("itemId");
        if (view.category() == null || view.slot() == null || !view.category().accepts(view.slot())) issues.add("categorySlot");
        if (view.tier() == null || !view.tier().contains(view.itemLevel())) issues.add("tierItemLevel");
        if (view.rarity() == null || view.modSlots() == null
                || view.modSlots().size() != (view.rarity() == null ? -1 : view.rarity().modCapacity())) issues.add("modCapacity");
        if (view.quality() != EquipmentQuality.UNSPECIFIED) issues.add("quality");
        if (view.enhancementLevel() < 0 || view.enhancementLevel() > 30) issues.add("enhancementLevel");
        if (view.binding() == null || view.tradePolicy() == null) issues.add("policy");
        HashSet<Integer> indexes = new HashSet<>();
        if (view.modSlots() != null) for (EquipmentModSlot slot : view.modSlots()) {
            if (slot == null || !indexes.add(slot.index())) issues.add("duplicateModSlot");
            else if (slot.entry().orElse(null) instanceof ModEntry entry
                    && entry.rank().tier() != view.tier()) issues.add("modRankTier");
        }
        HashSet<String> stats = new HashSet<>();
        if (view.baseStatRolls() == null) issues.add("baseStatRolls");
        else for (BaseStatRoll roll : view.baseStatRolls()) {
            if (roll == null || !stats.add(roll.statId())) issues.add("duplicateBaseStat");
        }
        return new EquipmentValidation(issues.isEmpty(), issues);
    }
}
