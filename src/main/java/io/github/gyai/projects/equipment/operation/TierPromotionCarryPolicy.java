package io.github.gyai.projects.equipment.operation;

import io.github.gyai.projects.equipment.MetadataIds;

import java.util.EnumMap;
import java.util.Map;

public record TierPromotionCarryPolicy(
        String policyId,
        long revision,
        Map<CarryField, FieldDecision> decisions,
        OperationResourcePlan resources
) {
    public TierPromotionCarryPolicy {
        policyId = MetadataIds.requireCanonical("policyId", policyId);
        if (revision < 0) throw new IllegalArgumentException("revision must be non-negative");
        EnumMap<CarryField, FieldDecision> copy = new EnumMap<>(CarryField.class);
        if (decisions != null) copy.putAll(decisions);
        decisions = Map.copyOf(copy);
        resources = resources == null ? OperationResourcePlan.none() : resources;
    }

    public boolean complete() {
        return decisions.keySet().containsAll(java.util.Set.of(CarryField.values()));
    }

    public enum CarryField { QUALITY, MODS, ENHANCEMENT, BINDING }

    /** USE_DESTINATION_VALUE is an explicit versioned reset/transform decision. */
    public enum FieldDecision { CARRY_SOURCE, USE_DESTINATION_VALUE }
}
