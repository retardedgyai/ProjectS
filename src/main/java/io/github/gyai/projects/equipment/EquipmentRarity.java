package io.github.gyai.projects.equipment;

public enum EquipmentRarity {
    COMMON(1), UNCOMMON(2), RARE(3), EPIC(4);
    private final int modCapacity;
    EquipmentRarity(int modCapacity) { this.modCapacity = modCapacity; }
    public int modCapacity() { return modCapacity; }
}
