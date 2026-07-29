package io.github.gyai.projects.combat.skill;

public enum HardControlType {
    STUN("スタン", 300),
    FEAR("恐怖", 200),
    CHARM("魅了", 200),
    ROOT("ルート", 100);

    private final String displayName;
    private final int priority;

    HardControlType(String displayName, int priority) {
        this.displayName = displayName;
        this.priority = priority;
    }

    public String displayName() {
        return displayName;
    }

    public int priority() {
        return priority;
    }

    public boolean blocksAllActions() {
        return this == STUN;
    }

    public boolean forcesBehavior() {
        return this == FEAR || this == CHARM;
    }

    public boolean blocksMovementSkills() {
        return this == STUN || this == FEAR || this == CHARM || this == ROOT;
    }
}
