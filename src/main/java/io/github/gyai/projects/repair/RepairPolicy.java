package io.github.gyai.projects.repair;

import io.github.gyai.projects.equipment.MetadataIds;
import io.github.gyai.projects.equipment.operation.OperationResourcePlan;

public record RepairPolicy(String policyId, long revision, OperationResourcePlan resources) {
    public RepairPolicy {
        policyId = MetadataIds.requireCanonical("policyId", policyId);
        if (revision < 0) throw new IllegalArgumentException("revision must be non-negative");
        resources = resources == null ? OperationResourcePlan.none() : resources;
    }
}
