package io.github.gyai.projects.mod;

import io.github.gyai.projects.equipment.EquipmentTier;

public enum ModRank {
    RANK_1(1, EquipmentTier.T1), RANK_2(2, EquipmentTier.T2), RANK_3(3, EquipmentTier.T3);
    private final int value;
    private final EquipmentTier tier;
    ModRank(int value, EquipmentTier tier) { this.value = value; this.tier = tier; }
    public int value() { return value; }
    public EquipmentTier tier() { return tier; }
}
