package io.github.gyai.projects.mod;

import io.github.gyai.projects.equipment.MetadataIds;

public record ModDisplayMetadata(String localizationKey, String template) {
    public ModDisplayMetadata {
        localizationKey = MetadataIds.requireBoundedText("localizationKey", localizationKey, 128);
        template = MetadataIds.requireBoundedText("template", template, 512);
    }
}
