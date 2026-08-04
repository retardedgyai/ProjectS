package io.github.gyai.projects.equipment.operation;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.MetadataIds;
import io.github.gyai.projects.transaction.TransactionRequest;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable proposed replacement. It does not authorize or perform a write. */
public record EquipmentMutationProposal(
        UUID requestId,
        UUID playerId,
        String operationId,
        String recipeId,
        String canonicalFamilyId,
        long expectedRevision,
        EquipmentItemV1 proposedItem,
        EquipmentExtensionSnapshot extensions,
        OperationResourcePlan resources,
        List<TransactionRequest.InputRevision> inputs
) {
    public EquipmentMutationProposal {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        operationId = MetadataIds.requireCanonical("operationId", operationId);
        recipeId = MetadataIds.requireCanonical("recipeId", recipeId);
        canonicalFamilyId = MetadataIds.requireCanonical("canonicalFamilyId", canonicalFamilyId);
        if (expectedRevision < 0) throw new IllegalArgumentException("expectedRevision must be non-negative");
        Objects.requireNonNull(proposedItem, "proposedItem");
        extensions = extensions == null ? EquipmentExtensionSnapshot.empty() : extensions;
        resources = resources == null ? OperationResourcePlan.none() : resources;
        inputs = List.copyOf(inputs);
        if (inputs.isEmpty()) throw new IllegalArgumentException("at least one input is required");
    }

    public TransactionRequest transactionRequest() {
        return new TransactionRequest(
                requestId, playerId, operationId, recipeId, expectedRevision, 1, inputs);
    }

    public static String inputId(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        return "projects:item-" + instanceId.toString().replace("-", "");
    }
}
