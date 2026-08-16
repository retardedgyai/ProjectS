package io.github.gyai.projects.enhancement.v2;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.MetadataIds;
import io.github.gyai.projects.equipment.operation.EquipmentExtensionSnapshot;

import java.util.Objects;
import java.util.UUID;

public record EnhancementAttempt(
        UUID requestId,
        UUID playerId,
        String canonicalFamilyId,
        EquipmentItemV1 source,
        long expectedRevision,
        EquipmentExtensionSnapshot extensions
) {
    public EnhancementAttempt {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        canonicalFamilyId = MetadataIds.requireCanonical("canonicalFamilyId", canonicalFamilyId);
        Objects.requireNonNull(source, "source");
        if (expectedRevision < 0) throw new IllegalArgumentException("expectedRevision must be non-negative");
        extensions = extensions == null ? EquipmentExtensionSnapshot.empty() : extensions;
    }
}
