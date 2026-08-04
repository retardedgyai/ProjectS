package io.github.gyai.projects.network.beta;

import java.util.UUID;

public record BetaCommandContext(
        UUID playerId,
        BetaCapabilitySnapshot capabilitySession,
        long currentPlayerSessionRevision,
        long currentTargetContentRevision,
        boolean permissionGranted,
        boolean producerFeatureEnabled,
        boolean currentStateValid,
        boolean transactionAdmitted,
        CommandClass commandClass
) {
    public enum CommandClass {
        READ,
        PERSISTENT_MUTATION,
        MOB_EDITOR_SAVE_APPLY
    }

    public BetaCommandContext {
        if (playerId == null || capabilitySession == null || commandClass == null
                || currentPlayerSessionRevision < 0 || currentTargetContentRevision < 0) {
            throw new IllegalArgumentException("Invalid command context");
        }
    }
}
