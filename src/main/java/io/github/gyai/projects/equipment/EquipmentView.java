package io.github.gyai.projects.equipment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EquipmentView {
    int schemaVersion();
    String itemId();
    EquipmentCategory category();
    EquipmentSlot slot();
    EquipmentTier tier();
    int itemLevel();
    EquipmentRarity rarity();
    EquipmentQuality quality();
    List<BaseStatRoll> baseStatRolls();
    List<EquipmentModSlot> modSlots();
    Optional<CrafterIdentity> crafter();
    int enhancementLevel();
    boolean broken();
    BindingPolicy binding();
    TradePolicy tradePolicy();
    Optional<UUID> instanceId();
}
