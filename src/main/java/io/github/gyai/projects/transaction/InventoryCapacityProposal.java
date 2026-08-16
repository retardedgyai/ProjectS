package io.github.gyai.projects.transaction;

import java.util.Objects;

public record InventoryCapacityProposal(
        long requiredUnits,
        DeliveryMode deliveryMode
) {
    public InventoryCapacityProposal {
        requiredUnits = QuantityMath.requirePositive(
                requiredUnits, "required output capacity");
        Objects.requireNonNull(deliveryMode, "deliveryMode");
    }

    public static InventoryCapacityProposal reservedInventory(long requiredUnits) {
        return new InventoryCapacityProposal(
                requiredUnits, DeliveryMode.RESERVED_INVENTORY);
    }

    public static InventoryCapacityProposal durableClaim(long requiredUnits) {
        return new InventoryCapacityProposal(
                requiredUnits, DeliveryMode.DURABLE_CLAIM);
    }

    public enum DeliveryMode {
        RESERVED_INVENTORY,
        DURABLE_CLAIM
    }
}
