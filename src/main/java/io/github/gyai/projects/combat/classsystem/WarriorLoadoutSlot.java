package io.github.gyai.projects.combat.classsystem;

public enum WarriorLoadoutSlot {
    Q("Q"),
    E("E"),
    R("R"),
    F("F");

    private final String displayKey;

    WarriorLoadoutSlot(String displayKey) {
        this.displayKey = displayKey;
    }

    public String displayKey() {
        return displayKey;
    }

    public static WarriorLoadoutSlot fromInternalSlot(SkillSlot slot) {
        return switch (slot) {
            case SKILL_Q -> Q;
            case SKILL_W -> E;
            case SKILL_E -> R;
            case SKILL_R -> F;
        };
    }
}
