package io.github.gyai.projects.monster.editor;

public record MobEquipmentEntry(
        SourceType sourceType,
        String referenceId,
        String material,
        String color,
        boolean glint,
        boolean visible,
        boolean visualOnly
) {
    public enum SourceType {
        NONE,
        VANILLA_ITEM,
        PROJECTS_ITEM,
        CUSTOM_HEAD
    }

    public static MobEquipmentEntry empty() {
        return new MobEquipmentEntry(
                SourceType.NONE, "", "", "", false, true, true);
    }
}
