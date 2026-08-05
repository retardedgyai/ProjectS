package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.*;
import io.github.gyai.projects.feature.FeatureKey;

import java.util.Set;

public final class MobEditorV2RuntimeModule extends AbstractTrack4RuntimeModule {
    private final StagingMobEditorRuntime runtime;

    public MobEditorV2RuntimeModule(StagingMobEditorRuntime runtime) {
        super(new BetaRuntimeModuleDescriptor(BetaRuntimeModuleId.MOB_EDITOR_V2,
                Set.of(), Set.of(FeatureKey.MOB_EDITOR_V2),
                BetaMutationPolicy.READ_ONLY, true,
                Set.of("beta-staging-mob-repository")));
        this.runtime = java.util.Objects.requireNonNull(runtime);
    }

    public StagingMobEditorRuntime runtime() { return runtime; }
    @Override protected BetaRuntimeModuleResult startModule(BetaRuntimeModuleContext context) {
        return BetaRuntimeModuleResult.running();
    }
    @Override protected void stopModule() { runtime.close(); }
}
