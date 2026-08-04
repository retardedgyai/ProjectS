package io.github.gyai.projects.repair;

import io.github.gyai.projects.equipment.operation.EquipmentMutationProposal;

import java.util.Optional;
import java.util.Objects;

public record RepairProposal(
        Status status,
        Optional<EquipmentMutationProposal> mutation,
        Optional<String> donorConsumptionInputId,
        String reason
) {
    public RepairProposal {
        Objects.requireNonNull(status, "status");
        mutation = mutation == null ? Optional.empty() : mutation;
        donorConsumptionInputId = donorConsumptionInputId == null
                ? Optional.empty() : donorConsumptionInputId;
        reason = reason == null ? "" : reason;
        if ((status == Status.ACCEPTED)
                != (mutation.isPresent() && donorConsumptionInputId.isPresent())) {
            throw new IllegalArgumentException("accepted repair requires mutation and donor consumption");
        }
    }

    public static RepairProposal rejected(String reason) {
        return new RepairProposal(Status.REJECTED, Optional.empty(), Optional.empty(), reason);
    }

    public enum Status { ACCEPTED, REJECTED }
}
