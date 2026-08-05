package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.beta.activation.BetaActivationAudience;
import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.beta.activation.BetaMutationPolicy;

import java.util.UUID;

/** Per-command authority snapshot. Every operation revalidates it. */
public record StagingOperationAccess(
        UUID playerId,
        String worldName,
        boolean projectsDev,
        BetaActivationPolicy activationPolicy
) {
    public StagingOperationAccess {
        if (playerId == null || worldName == null || activationPolicy == null) {
            throw new IllegalArgumentException("operation access input missing");
        }
    }

    public boolean allowed() {
        return projectsDev
                && activationPolicy.audience() == BetaActivationAudience.ALLOWLIST
                && activationPolicy.allowlistedPlayerUuids().contains(playerId)
                && activationPolicy.mutationPolicy() == BetaMutationPolicy.STAGING_WRITE
                && activationPolicy.allowsWorld(worldName);
    }
}
