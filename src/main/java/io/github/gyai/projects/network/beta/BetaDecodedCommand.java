package io.github.gyai.projects.network.beta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BetaDecodedCommand(
        BetaCapabilityId capabilityId,
        String operationId,
        UUID requestId,
        long playerSessionRevision,
        long targetContentRevision,
        Map<String, String> arguments,
        List<String> selections
) {
    public BetaDecodedCommand {
        if (capabilityId == null || requestId == null || playerSessionRevision < 0
                || targetContentRevision < 0) {
            throw new IllegalArgumentException("Invalid decoded command identity");
        }
        operationId = BetaDisplayValidation.id(operationId, "operationId");
        arguments = BetaDisplayValidation.map(arguments, 64, "command arguments");
        arguments.forEach((key, value) -> {
            BetaDisplayValidation.id(key, "argumentId");
            BetaDisplayValidation.string(value, "argumentValue");
        });
        selections = BetaDisplayValidation.list(selections, 128, "command selections");
        selections.forEach(value -> BetaDisplayValidation.id(value, "selectionId"));
    }
}
