package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.*;
import io.github.gyai.projects.feature.FeatureKey;

import java.util.Set;

public final class ClientBetaProtocolRuntimeModule extends AbstractTrack4RuntimeModule {
    private final ClientBetaProtocolRuntime runtime;

    public ClientBetaProtocolRuntimeModule(ClientBetaProtocolRuntime runtime) {
        super(new BetaRuntimeModuleDescriptor(BetaRuntimeModuleId.CLIENT_BETA_PROTOCOL,
                Set.of(BetaRuntimeModuleId.PARTY_QUEST_REWARD,
                        BetaRuntimeModuleId.MOB_EDITOR_V2),
                Set.of(FeatureKey.CLIENT_BETA_UI), BetaMutationPolicy.READ_ONLY,
                true, Set.of("minecraft-plugin-messaging")));
        this.runtime = java.util.Objects.requireNonNull(runtime);
    }

    public ClientBetaProtocolRuntime runtime() { return runtime; }
    @Override protected BetaRuntimeModuleResult startModule(BetaRuntimeModuleContext context) {
        runtime.start();
        return BetaRuntimeModuleResult.running();
    }
    @Override protected void stopModule() { runtime.close(); }
}
