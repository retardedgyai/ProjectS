package io.github.gyai.projects.combat.resource;

public enum ResourceType {
    FIGHTING_SPIRIT("闘気"), MANA("マナ"), ENERGY("エネルギー"), RAGE("怒気"), NONE("なし");

    private final String displayName;
    ResourceType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
