package io.github.gyai.projects.beta.activation;

import io.github.gyai.projects.feature.FeatureFlagSnapshot;

import java.time.Clock;
import java.util.Set;

public record BetaRuntimeModuleContext(
        BetaActivationPolicy activationPolicy,
        FeatureFlagSnapshot featureFlags,
        Set<String> availableInfrastructure,
        Clock clock,
        boolean readOnlyMode
) {
    public BetaRuntimeModuleContext {
        if (activationPolicy == null || featureFlags == null || clock == null) {
            throw new IllegalArgumentException("Invalid runtime module context");
        }
        availableInfrastructure = Set.copyOf(
                availableInfrastructure == null ? Set.of() : availableInfrastructure);
    }
}
