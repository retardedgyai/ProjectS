package io.github.gyai.projects.equipment;

public enum EquipmentSlot {
    WEAPON("weapon"), HEAD("head"), CHEST("chest"), LEGS("legs"),
    BOOTS("boots"), NECKLACE("necklace"), RING_1("ring_1"), RING_2("ring_2");

    private final String id;
    EquipmentSlot(String id) { this.id = id; }
    public String id() { return id; }
}
