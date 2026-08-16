package io.github.gyai.projects.beta.activation;

import io.github.gyai.projects.feature.FeatureFlagSnapshot;

import java.time.Clock;
import java.util.Collection;
import java.util.Set;
import java.util.function.BiConsumer;

public final class BetaRuntimeFactory {
    private BetaRuntimeFactory() {
    }

    public static BetaRuntime empty(
            BetaActivationPolicy policy,
            FeatureFlagSnapshot flags,
            Clock clock,
            BiConsumer<String, RuntimeException> exceptionLogger
    ) {
        return create(policy, flags, java.util.List.of(), Set.of(), clock, exceptionLogger);
    }

    public static BetaRuntime create(
            BetaActivationPolicy policy,
            FeatureFlagSnapshot flags,
            Collection<? extends BetaRuntimeModule> modules,
            Set<String> infrastructure,
            Clock clock,
            BiConsumer<String, RuntimeException> exceptionLogger
    ) {
        BetaRuntimeDependencyResolver.StartupPlan plan =
                new BetaRuntimeDependencyResolver().resolve(modules);
        return new BetaRuntime(plan, policy, flags, infrastructure, clock, exceptionLogger);
    }
}
