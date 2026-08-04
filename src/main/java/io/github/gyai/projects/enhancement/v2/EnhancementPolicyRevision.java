package io.github.gyai.projects.enhancement.v2;

import io.github.gyai.projects.equipment.MetadataIds;

public record EnhancementPolicyRevision(String policyId, long revision) {
    public EnhancementPolicyRevision {
        policyId = MetadataIds.requireCanonical("policyId", policyId);
        if (revision < 0) throw new IllegalArgumentException("revision must be non-negative");
    }
}
