package io.github.gyai.projects.beta.activation.track1.equipment;

import io.github.gyai.projects.equipment.BaseStatRoll;
import io.github.gyai.projects.equipment.BindingPolicy;
import io.github.gyai.projects.equipment.EquipmentCategory;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentModSlot;
import io.github.gyai.projects.equipment.EquipmentQuality;
import io.github.gyai.projects.equipment.EquipmentRarity;
import io.github.gyai.projects.equipment.EquipmentSlot;
import io.github.gyai.projects.equipment.EquipmentTier;
import io.github.gyai.projects.equipment.TradePolicy;
import io.github.gyai.projects.item.compatibility.LegacyItemView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Conservative read projection; it never invents an item instance UUID. */
public final class LegacyEquipmentV1Projector {
    public EquipmentItemV1 project(LegacyItemView legacy) {
        if (legacy == null) throw new IllegalArgumentException("legacy view is required");
        Slot slot = slot(legacy.itemId());
        ArrayList<BaseStatRoll> rolls = new ArrayList<>();
        legacy.attackPowerBonus().ifPresent(value -> rolls.add(
                new BaseStatRoll("projects:attack-power", value)));
        legacy.attackSpeedBonus().ifPresent(value -> rolls.add(
                new BaseStatRoll("projects:attack-speed", value)));
        return new EquipmentItemV1(
                EquipmentItemV1.currentSchemaVersion(), legacy.itemId(), slot.category(),
                slot.slot(), EquipmentTier.T1, 1, EquipmentRarity.COMMON,
                EquipmentQuality.UNSPECIFIED, rolls, List.of(EquipmentModSlot.empty(0)),
                Optional.empty(), legacy.enhancementLevel().orElse(0), legacy.broken(),
                BindingPolicy.UNBOUND, TradePolicy.DENY_ALL, Optional.empty());
    }

    private Slot slot(String itemId) {
        String id = itemId.toLowerCase(Locale.ROOT);
        if (id.contains("helmet") || id.contains("head")) return armor(EquipmentSlot.HEAD);
        if (id.contains("chest")) return armor(EquipmentSlot.CHEST);
        if (id.contains("leggings") || id.contains("legs")) return armor(EquipmentSlot.LEGS);
        if (id.contains("boots")) return armor(EquipmentSlot.BOOTS);
        return new Slot(EquipmentCategory.WEAPON, EquipmentSlot.WEAPON);
    }

    private Slot armor(EquipmentSlot slot) { return new Slot(EquipmentCategory.ARMOR, slot); }

    private record Slot(EquipmentCategory category, EquipmentSlot slot) { }
}
