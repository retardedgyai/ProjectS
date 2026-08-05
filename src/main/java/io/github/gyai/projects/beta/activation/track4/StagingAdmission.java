package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.BetaActivationAudience;
import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.beta.activation.BetaActivationTarget;
import io.github.gyai.projects.beta.activation.BetaMutationPolicy;

import java.util.UUID;

public final class StagingAdmission {
    private StagingAdmission() {
    }

    public static Decision read(
            BetaActivationPolicy policy,
            UUID playerId,
            String worldName,
            boolean projectsDev,
            boolean compatibleClient
    ) {
        if (policy == null || policy.audience() == BetaActivationAudience.OFF) {
            return Decision.denied("audience-off");
        }
        if (!projectsDev) return Decision.denied("projects.dev-required");
        if (!policy.allowsAudience(playerId, compatibleClient)) {
            return Decision.denied("audience-denied");
        }
        if (!policy.allowsWorld(worldName)) return Decision.denied("world-denied");
        if (!policy.allowsTarget(BetaActivationTarget.TRAINING_DUMMY)) {
            return Decision.denied("training-dummy-denied");
        }
        return Decision.permitted();
    }

    public static Decision stagingWrite(
            BetaActivationPolicy policy,
            UUID playerId,
            String worldName,
            boolean projectsDev,
            boolean compatibleClient
    ) {
        Decision read = read(policy, playerId, worldName, projectsDev, compatibleClient);
        if (!read.allowed()) return read;
        if (policy.mutationPolicy() != BetaMutationPolicy.STAGING_WRITE) {
            return Decision.denied("staging-write-required");
        }
        return Decision.permitted();
    }

    public record Decision(boolean allowed, String reason) {
        public Decision {
            reason = reason == null ? "" : reason;
        }

        static Decision permitted() {
            return new Decision(true, "");
        }

        static Decision denied(String reason) {
            return new Decision(false, reason);
        }
    }
}
