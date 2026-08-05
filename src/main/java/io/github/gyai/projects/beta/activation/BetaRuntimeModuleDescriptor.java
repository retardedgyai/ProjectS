package io.github.gyai.projects.beta.activation;

import io.github.gyai.projects.feature.FeatureKey;

import java.util.Set;

public record BetaRuntimeModuleDescriptor(
        BetaRuntimeModuleId id,
        Set<BetaRuntimeModuleId> dependencies,
        Set<FeatureKey> activationFeatures,
        BetaMutationPolicy minimumMutationPolicy,
        boolean readOnlyCapable,
        Set<String> requiredInfrastructure
) {
    public static final int MAXIMUM_INFRASTRUCTURE_REQUIREMENTS = 16;

    public BetaRuntimeModuleDescriptor {
        if (id == null || minimumMutationPolicy == null) {
            throw new IllegalArgumentException("Invalid module descriptor");
        }
        dependencies = Set.copyOf(dependencies == null ? Set.of() : dependencies);
        activationFeatures = Set.copyOf(
                activationFeatures == null ? Set.of() : activationFeatures);
        requiredInfrastructure = Set.copyOf(
                requiredInfrastructure == null ? Set.of() : requiredInfrastructure);
        if (dependencies.contains(id)) {
            throw new IllegalArgumentException("Module cannot depend on itself");
        }
        if (requiredInfrastructure.size() > MAXIMUM_INFRASTRUCTURE_REQUIREMENTS) {
            throw new IllegalArgumentException("Infrastructure requirements are oversized");
        }
        requiredInfrastructure.forEach(value -> {
            if (value == null || !value.matches("[a-z0-9][a-z0-9._:-]{0,127}")) {
                throw new IllegalArgumentException("Invalid infrastructure requirement");
            }
        });
    }

    public static BetaRuntimeModuleDescriptor testModule(
            BetaRuntimeModuleId id,
            Set<BetaRuntimeModuleId> dependencies
    ) {
        return new BetaRuntimeModuleDescriptor(id, dependencies, Set.of(),
                BetaMutationPolicy.READ_ONLY, true, Set.of());
    }
}
