package io.github.gyai.projects.equipment.operation;

import io.github.gyai.projects.equipment.BindingPolicy;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentModSlot;
import io.github.gyai.projects.equipment.EquipmentQuality;
import io.github.gyai.projects.equipment.EquipmentTier;

import java.util.List;

public final class EquipmentItems {
    private EquipmentItems() { }

    public static EquipmentItemV1 replaceMutableState(
            EquipmentItemV1 source,
            EquipmentTier tier,
            int itemLevel,
            EquipmentQuality quality,
            List<EquipmentModSlot> modSlots,
            int enhancementLevel,
            boolean broken,
            BindingPolicy binding
    ) {
        return new EquipmentItemV1(
                source.schemaVersion(), source.itemId(), source.category(), source.slot(),
                tier, itemLevel, source.rarity(), quality, source.baseStatRolls(),
                modSlots, source.crafter(), enhancementLevel, broken, binding,
                source.tradePolicy(), source.instanceId());
    }

    public static EquipmentItemV1 repair(EquipmentItemV1 target) {
        return replaceMutableState(
                target, target.tier(), target.itemLevel(), target.quality(),
                target.modSlots(), target.enhancementLevel(), false, target.binding());
    }
}
