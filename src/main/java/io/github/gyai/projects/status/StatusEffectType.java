package io.github.gyai.projects.status;

public enum StatusEffectType {
    SLOW("スロウ", 600),
    POISON("毒", 500),
    BLEED("出血", 400),
    BURN("炎上", 300),
    DEFENSE_DOWN("防御低下", 200),
    ATTACK_DOWN("攻撃力低下", 100);

    private final String displayName;
    private final int uiPriority;

    StatusEffectType(String displayName, int uiPriority) {
        this.displayName = displayName;
        this.uiPriority = uiPriority;
    }

    public String displayName() {
        return displayName;
    }

    public int uiPriority() {
        return uiPriority;
    }
}
