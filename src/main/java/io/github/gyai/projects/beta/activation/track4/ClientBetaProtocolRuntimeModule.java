package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.*;
import io.github.gyai.projects.feature.FeatureKey;

import java.util.Set;

public final class ClientBetaProtocolRuntimeModule extends AbstractTrack4RuntimeModule {
    private final ClientBetaProtocolRuntime runtime;
    private final BetaCapabilityAdvertisementPublisher advertisementPublisher;
    private final ElementSnapshotProtocolPublisher elementPublisher;

    public ClientBetaProtocolRuntimeModule(ClientBetaProtocolRuntime runtime) {
        this(runtime, null, null);
    }

    public ClientBetaProtocolRuntimeModule(
            ClientBetaProtocolRuntime runtime,
            ElementSnapshotProtocolPublisher elementPublisher
    ) {
        this(runtime, null, elementPublisher);
    }

    public ClientBetaProtocolRuntimeModule(
            ClientBetaProtocolRuntime runtime,
            BetaCapabilityAdvertisementPublisher advertisementPublisher,
            ElementSnapshotProtocolPublisher elementPublisher
    ) {
        super(new BetaRuntimeModuleDescriptor(BetaRuntimeModuleId.CLIENT_BETA_PROTOCOL,
                Set.of(),
                Set.of(FeatureKey.CLIENT_BETA_UI), BetaMutationPolicy.READ_ONLY,
                true, Set.of("minecraft-plugin-messaging")));
        this.runtime = java.util.Objects.requireNonNull(runtime);
        this.advertisementPublisher = advertisementPublisher;
        this.elementPublisher = elementPublisher;
    }

    public ClientBetaProtocolRuntime runtime() { return runtime; }
    @Override protected BetaRuntimeModuleResult startModule(BetaRuntimeModuleContext context) {
        try {
            runtime.start();
            if (advertisementPublisher != null) advertisementPublisher.start(
                    context.activationPolicy(),
                    context.featureFlags().isEnabled(FeatureKey.CLIENT_BETA_UI));
            if (elementPublisher != null) elementPublisher.start();
            return BetaRuntimeModuleResult.running();
        } catch (RuntimeException failure) {
            if (elementPublisher != null) try { elementPublisher.close(); }
            catch (RuntimeException ignored) { }
            if (advertisementPublisher != null) try { advertisementPublisher.close(); }
            catch (RuntimeException ignored) { }
            try { runtime.close(); } catch (RuntimeException ignored) { }
            throw failure;
        }
    }
    @Override protected void stopModule() {
        RuntimeException first = null;
        if (elementPublisher != null) try { elementPublisher.close(); }
        catch (RuntimeException failure) { first = failure; }
        if (advertisementPublisher != null) try { advertisementPublisher.close(); }
        catch (RuntimeException failure) { if (first == null) first = failure; }
        try { runtime.close(); }
        catch (RuntimeException failure) { if (first == null) first = failure; }
        if (first != null) throw first;
    }
}
