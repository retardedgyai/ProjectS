package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.*;
import io.github.gyai.projects.feature.FeatureKey;

import java.util.Set;

public final class ClientBetaProtocolRuntimeModule extends AbstractTrack4RuntimeModule {
    private final ClientBetaProtocolRuntime runtime;
    private final ElementSnapshotProtocolPublisher elementPublisher;

    public ClientBetaProtocolRuntimeModule(ClientBetaProtocolRuntime runtime) {
        this(runtime, null);
    }

    public ClientBetaProtocolRuntimeModule(
            ClientBetaProtocolRuntime runtime,
            ElementSnapshotProtocolPublisher elementPublisher
    ) {
        super(new BetaRuntimeModuleDescriptor(BetaRuntimeModuleId.CLIENT_BETA_PROTOCOL,
                Set.of(),
                Set.of(FeatureKey.CLIENT_BETA_UI), BetaMutationPolicy.READ_ONLY,
                true, Set.of("minecraft-plugin-messaging")));
        this.runtime = java.util.Objects.requireNonNull(runtime);
        this.elementPublisher = elementPublisher;
    }

    public ClientBetaProtocolRuntime runtime() { return runtime; }
    @Override protected BetaRuntimeModuleResult startModule(BetaRuntimeModuleContext context) {
        runtime.start();
        if (elementPublisher != null) elementPublisher.start();
        return BetaRuntimeModuleResult.running();
    }
    @Override protected void stopModule() {
        if (elementPublisher != null) elementPublisher.close();
        runtime.close();
    }
}
