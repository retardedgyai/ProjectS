package io.github.gyai.projects.equipment.operation;

import io.github.gyai.projects.equipment.MetadataIds;

import java.util.HashSet;
import java.util.List;

public record OperationResourcePlan(List<MaterialCost> materials, long currencyCost) {
    public OperationResourcePlan {
        materials = materials == null ? List.of() : List.copyOf(materials);
        if (currencyCost < 0) throw new IllegalArgumentException("currencyCost must be non-negative");
        HashSet<String> ids = new HashSet<>();
        for (MaterialCost material : materials) {
            if (material == null || !ids.add(material.materialId())) {
                throw new IllegalArgumentException("materials must be non-null and unique");
            }
        }
    }

    public static OperationResourcePlan none() {
        return new OperationResourcePlan(List.of(), 0);
    }

    public record MaterialCost(String materialId, long quantity) {
        public MaterialCost {
            materialId = MetadataIds.requireCanonical("materialId", materialId);
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
