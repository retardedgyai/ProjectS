package io.github.gyai.projects.equipment;

import io.github.gyai.projects.schema.SchemaVersions;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record EquipmentItemV1(
        int schemaVersion, String itemId, EquipmentCategory category,
        EquipmentSlot slot, EquipmentTier tier, int itemLevel,
        EquipmentRarity rarity, EquipmentQuality quality,
        List<BaseStatRoll> baseStatRolls, List<EquipmentModSlot> modSlots,
        Optional<CrafterIdentity> crafter, int enhancementLevel,
        boolean broken, BindingPolicy binding, TradePolicy tradePolicy,
        Optional<UUID> instanceId
) implements EquipmentView {
    public EquipmentItemV1 {
        itemId = MetadataIds.requireBoundedText("itemId", itemId, 128);
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(quality, "quality");
        baseStatRolls = List.copyOf(baseStatRolls);
        modSlots = List.copyOf(modSlots);
        crafter = crafter == null ? Optional.empty() : crafter;
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(tradePolicy, "tradePolicy");
        instanceId = instanceId == null ? Optional.empty() : instanceId;
        EquipmentValidation validation = EquipmentValidation.validate(new UncheckedView(
                schemaVersion, itemId, category, slot, tier, itemLevel, rarity, quality,
                baseStatRolls, modSlots, crafter, enhancementLevel, broken, binding,
                tradePolicy, instanceId));
        if (!validation.valid()) throw new IllegalArgumentException("invalid equipment: " + validation.issues());
    }
    public static int currentSchemaVersion() { return SchemaVersions.EQUIPMENT_ITEM; }

    private record UncheckedView(int schemaVersion, String itemId, EquipmentCategory category,
            EquipmentSlot slot, EquipmentTier tier, int itemLevel, EquipmentRarity rarity,
            EquipmentQuality quality, List<BaseStatRoll> baseStatRolls,
            List<EquipmentModSlot> modSlots, Optional<CrafterIdentity> crafter,
            int enhancementLevel, boolean broken, BindingPolicy binding,
            TradePolicy tradePolicy, Optional<UUID> instanceId) implements EquipmentView { }
}
