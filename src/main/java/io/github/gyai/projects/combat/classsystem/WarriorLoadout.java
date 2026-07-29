package io.github.gyai.projects.combat.classsystem;

public record WarriorLoadout(String q, String e, String r, String f) {
    public static WarriorLoadout defaults() {
        return new WarriorLoadout(
                "spin_slash",
                "warrior_charge",
                "indomitable_spirit",
                "fighting_spirit_release");
    }

    public String skill(WarriorLoadoutSlot slot) {
        return switch (slot) {
            case Q -> q;
            case E -> e;
            case R -> r;
            case F -> f;
        };
    }

    public WarriorLoadout with(WarriorLoadoutSlot slot, String skillId) {
        return switch (slot) {
            case Q -> new WarriorLoadout(skillId, e, r, f);
            case E -> new WarriorLoadout(q, skillId, r, f);
            case R -> new WarriorLoadout(q, e, skillId, f);
            case F -> new WarriorLoadout(q, e, r, skillId);
        };
    }
}
