package io.github.gyai.projects.enhancement.v2;

import io.github.gyai.projects.equipment.operation.EquipmentMutationProposal;

import java.util.Objects;
import java.util.Optional;

public record EnhancementProposal(
        EnhancementOutcome outcome,
        Optional<EquipmentMutationProposal> mutation,
        String reason
) {
    public EnhancementProposal {
        Objects.requireNonNull(outcome, "outcome");
        mutation = mutation == null ? Optional.empty() : mutation;
        reason = reason == null ? "" : reason;
        if (reason.length() > 256) throw new IllegalArgumentException("reason too long");
        if ((outcome == EnhancementOutcome.REJECTED) == mutation.isPresent()) {
            throw new IllegalArgumentException("only non-rejected outcomes have a mutation");
        }
    }

    public static EnhancementProposal rejected(String reason) {
        return new EnhancementProposal(EnhancementOutcome.REJECTED, Optional.empty(), reason);
    }
}
