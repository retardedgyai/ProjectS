package io.github.gyai.projects.equipment.operation;

import java.util.Optional;
import java.util.Objects;

public record TierPromotionProposal(
        Status status,
        Optional<EquipmentMutationProposal> mutation,
        String reason
) {
    public TierPromotionProposal {
        Objects.requireNonNull(status, "status");
        mutation = mutation == null ? Optional.empty() : mutation;
        reason = reason == null ? "" : reason;
        if ((status == Status.ACCEPTED) != mutation.isPresent()) {
            throw new IllegalArgumentException("accepted proposal requires mutation");
        }
    }

    public static TierPromotionProposal rejected(String reason) {
        return new TierPromotionProposal(Status.REJECTED, Optional.empty(), reason);
    }

    public enum Status { ACCEPTED, REJECTED }
}
