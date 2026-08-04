package io.github.gyai.projects.equipment.operation;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.MetadataIds;

import java.util.Objects;
import java.util.UUID;

public record TierPromotionRequest(
        UUID requestId,
        UUID playerId,
        String sourceFamilyId,
        String destinationFamilyId,
        EquipmentItemV1 source,
        EquipmentItemV1 destinationTemplate,
        long expectedRevision,
        EquipmentExtensionSnapshot extensions
) {
    public TierPromotionRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        sourceFamilyId = MetadataIds.requireCanonical("sourceFamilyId", sourceFamilyId);
        destinationFamilyId = MetadataIds.requireCanonical("destinationFamilyId", destinationFamilyId);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destinationTemplate, "destinationTemplate");
        if (expectedRevision < 0) throw new IllegalArgumentException("expectedRevision must be non-negative");
        extensions = extensions == null ? EquipmentExtensionSnapshot.empty() : extensions;
    }
}
