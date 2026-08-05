package io.github.gyai.projects.beta.activation.track1.module;

import io.github.gyai.projects.beta.activation.BetaMutationPolicy;
import io.github.gyai.projects.beta.activation.BetaRuntimeModule;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleContext;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleDescriptor;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleId;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleResult;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.beta.activation.track1.bukkit.Track1ListenerRegistrar;
import io.github.gyai.projects.beta.activation.track1.player.StagingPlayerProgressService;
import io.github.gyai.projects.feature.FeatureKey;
import org.bukkit.event.Listener;

import java.util.Set;

public final class PlayerPersistenceRuntimeModule implements BetaRuntimeModule {
    public static final String LISTENER_KEY = "beta-track1-player-persistence";
    private final StagingPlayerProgressService service;
    private final Track1ListenerRegistrar registrar;
    private final Listener listener;
    private BetaRuntimeModuleState state = BetaRuntimeModuleState.NOT_INSTALLED;
    private boolean registered;

    public PlayerPersistenceRuntimeModule(StagingPlayerProgressService service,
                                          Track1ListenerRegistrar registrar,
                                          Listener listener) {
        if (service == null || registrar == null || listener == null) {
            throw new IllegalArgumentException("player module infrastructure is required");
        }
        this.service = service;
        this.registrar = registrar;
        this.listener = listener;
    }

    @Override public BetaRuntimeModuleId id() { return BetaRuntimeModuleId.PLAYER_PERSISTENCE; }
    @Override public Set<BetaRuntimeModuleId> dependencies() { return Set.of(); }
    @Override public BetaRuntimeModuleDescriptor descriptor() {
        return new BetaRuntimeModuleDescriptor(id(), dependencies(),
                Set.of(FeatureKey.PLAYER_PERSISTENCE), BetaMutationPolicy.READ_ONLY,
                true, Set.of("track1.bukkit-listener", "track1.staging-player-store"));
    }

    @Override public synchronized BetaRuntimeModuleResult prepare(BetaRuntimeModuleContext context) {
        if (state == BetaRuntimeModuleState.RUNNING) return BetaRuntimeModuleResult.running();
        if (context == null || !context.featureFlags().isEnabled(FeatureKey.PLAYER_PERSISTENCE)
                || context.activationPolicy().audience()
                == io.github.gyai.projects.beta.activation.BetaActivationAudience.OFF) {
            state = BetaRuntimeModuleState.DISABLED;
            return new BetaRuntimeModuleResult(false, state, "player persistence activation is disabled");
        }
        state = BetaRuntimeModuleState.READY;
        return BetaRuntimeModuleResult.ready();
    }

    @Override public synchronized BetaRuntimeModuleResult start() {
        if (state == BetaRuntimeModuleState.RUNNING) return BetaRuntimeModuleResult.running();
        if (state != BetaRuntimeModuleState.READY) return BetaRuntimeModuleResult.failure("module is not ready");
        try {
            service.start();
            registrar.register(LISTENER_KEY, listener);
            registered = true;
            state = BetaRuntimeModuleState.RUNNING;
            return BetaRuntimeModuleResult.running();
        } catch (RuntimeException exception) {
            if (registered) registrar.unregister(LISTENER_KEY, listener);
            registered = false;
            service.close();
            state = BetaRuntimeModuleState.FAILED;
            return BetaRuntimeModuleResult.failure("listener registration failed");
        }
    }

    @Override public synchronized BetaRuntimeModuleResult stop() {
        if (state == BetaRuntimeModuleState.STOPPED) return BetaRuntimeModuleResult.stopped();
        state = BetaRuntimeModuleState.STOPPING;
        RuntimeException failure = null;
        if (registered) {
            try { registrar.unregister(LISTENER_KEY, listener); }
            catch (RuntimeException exception) { failure = exception; }
            registered = false;
        }
        try { service.close(); }
        catch (RuntimeException exception) { if (failure == null) failure = exception; }
        state = failure == null ? BetaRuntimeModuleState.STOPPED : BetaRuntimeModuleState.FAILED;
        return failure == null ? BetaRuntimeModuleResult.stopped()
                : BetaRuntimeModuleResult.failure("player module cleanup failed");
    }

    @Override public synchronized BetaRuntimeModuleState state() { return state; }
}
