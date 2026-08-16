package io.github.gyai.projects.network.beta;

import java.util.UUID;

public record BetaCommandEnvelope(
        BetaMessageEnvelope message,
        long playerSessionRevision,
        long targetContentRevision,
        UUID idempotencyRequestId
) {
    public BetaCommandEnvelope {
        if (message == null || message.kind() != BetaMessageKind.COMMAND
                || playerSessionRevision < 0 || targetContentRevision < 0
                || idempotencyRequestId == null) {
            throw new IllegalArgumentException("Invalid command envelope");
        }
    }
}
