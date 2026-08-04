package io.github.gyai.projects.mod;

import io.github.gyai.projects.equipment.MetadataIds;

public record ModSource(String definitionPackId, String operationSourceId) {
    public ModSource {
        definitionPackId = MetadataIds.requireCanonical("definitionPackId", definitionPackId);
        operationSourceId = MetadataIds.requireCanonical("operationSourceId", operationSourceId);
    }
}
