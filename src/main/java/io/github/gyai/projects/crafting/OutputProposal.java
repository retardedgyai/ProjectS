package io.github.gyai.projects.crafting;

import io.github.gyai.projects.transaction.DomainId;
import io.github.gyai.projects.transaction.QuantityMath;

public record OutputProposal(
        String outputId,
        long quantity,
        boolean equipmentBase
) {
    public OutputProposal {
        outputId = DomainId.requireNamespaced(outputId, "output ID");
        quantity = QuantityMath.requirePositive(quantity, "output quantity");
    }
}
