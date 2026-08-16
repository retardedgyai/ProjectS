package io.github.gyai.projects.repair;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.MetadataIds;
import io.github.gyai.projects.equipment.operation.EquipmentExtensionSnapshot;

import java.util.Objects;
import java.util.UUID;

public record RepairRequest(
        UUID requestId,
        UUID playerId,
        String targetFamilyId,
        String donorFamilyId,
        EquipmentItemV1 target,
        long targetRevision,
        EquipmentItemV1 donor,
        long donorRevision,
        EquipmentExtensionSnapshot targetExtensions
) {
    public RepairRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        targetFamilyId = MetadataIds.requireCanonical("targetFamilyId", targetFamilyId);
        donorFamilyId = MetadataIds.requireCanonical("donorFamilyId", donorFamilyId);
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(donor, "donor");
        if (targetRevision < 0 || donorRevision < 0) {
            throw new IllegalArgumentException("revisions must be non-negative");
        }
        targetExtensions = targetExtensions == null
                ? EquipmentExtensionSnapshot.empty() : targetExtensions;
    }
}
