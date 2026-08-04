package io.github.gyai.projects.equipment;

public enum EquipmentCategory {
    WEAPON, ARMOR, ACCESSORY;

    public boolean accepts(EquipmentSlot slot) {
        return switch (this) {
            case WEAPON -> slot == EquipmentSlot.WEAPON;
            case ARMOR -> slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
                    || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.BOOTS;
            case ACCESSORY -> slot == EquipmentSlot.NECKLACE
                    || slot == EquipmentSlot.RING_1 || slot == EquipmentSlot.RING_2;
        };
    }
}
