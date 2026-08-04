package io.github.gyai.projects.equipment;

public enum EquipmentTier {
    T1(1, 15), T2(16, 30), T3(31, 45);
    private final int minimumItemLevel;
    private final int maximumItemLevel;
    EquipmentTier(int minimumItemLevel, int maximumItemLevel) {
        this.minimumItemLevel = minimumItemLevel;
        this.maximumItemLevel = maximumItemLevel;
    }
    public int minimumItemLevel() { return minimumItemLevel; }
    public int maximumItemLevel() { return maximumItemLevel; }
    public boolean contains(int itemLevel) {
        return itemLevel >= minimumItemLevel && itemLevel <= maximumItemLevel;
    }
}
