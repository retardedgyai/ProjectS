package io.github.gyai.projects.beta.activation;

import java.util.Set;

public interface BetaRuntimeModule {
    BetaRuntimeModuleId id();

    Set<BetaRuntimeModuleId> dependencies();

    default BetaRuntimeModuleDescriptor descriptor() {
        return BetaRuntimeModuleDescriptor.testModule(id(), dependencies());
    }

    BetaRuntimeModuleResult prepare(BetaRuntimeModuleContext context);

    BetaRuntimeModuleResult start();

    BetaRuntimeModuleResult stop();

    BetaRuntimeModuleState state();
}
