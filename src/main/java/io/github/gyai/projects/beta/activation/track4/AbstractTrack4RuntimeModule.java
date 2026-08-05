package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.BetaActivationAudience;
import io.github.gyai.projects.beta.activation.BetaRuntimeModule;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleContext;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleDescriptor;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleId;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleResult;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.feature.FeatureKey;

import java.util.Set;

abstract class AbstractTrack4RuntimeModule implements BetaRuntimeModule {
    private final BetaRuntimeModuleDescriptor descriptor;
    private BetaRuntimeModuleState state = BetaRuntimeModuleState.NOT_INSTALLED;
    private BetaRuntimeModuleContext context;

    AbstractTrack4RuntimeModule(BetaRuntimeModuleDescriptor descriptor) {
        this.descriptor = java.util.Objects.requireNonNull(descriptor);
    }

    @Override
    public final BetaRuntimeModuleId id() {
        return descriptor.id();
    }

    @Override
    public final Set<BetaRuntimeModuleId> dependencies() {
        return descriptor.dependencies();
    }

    @Override
    public final BetaRuntimeModuleDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public final synchronized BetaRuntimeModuleResult prepare(BetaRuntimeModuleContext value) {
        if (state == BetaRuntimeModuleState.READY || state == BetaRuntimeModuleState.RUNNING) {
            return BetaRuntimeModuleResult.ready();
        }
        if (state == BetaRuntimeModuleState.STOPPED || state == BetaRuntimeModuleState.FAILED) {
            state = BetaRuntimeModuleState.FAILED;
            return BetaRuntimeModuleResult.failure("module cannot be prepared after terminal state");
        }
        if (value == null || value.activationPolicy().audience() == BetaActivationAudience.OFF) {
            state = BetaRuntimeModuleState.FAILED;
            return BetaRuntimeModuleResult.failure("activation audience is OFF");
        }
        for (FeatureKey feature : descriptor.activationFeatures()) {
            if (!value.featureFlags().isEnabled(feature)) {
                state = BetaRuntimeModuleState.FAILED;
                return BetaRuntimeModuleResult.failure("required feature is disabled");
            }
        }
        if (value.activationPolicy().allowedWorlds().isEmpty()) {
            state = BetaRuntimeModuleState.FAILED;
            return BetaRuntimeModuleResult.failure("staging world allowlist is empty");
        }
        context = value;
        BetaRuntimeModuleResult prepared = prepareModule(value);
        if (prepared == null || !prepared.success()
                || prepared.state() != BetaRuntimeModuleState.READY) {
            state = BetaRuntimeModuleState.FAILED;
            return prepared == null ? BetaRuntimeModuleResult.failure("prepare unavailable")
                    : prepared;
        }
        state = BetaRuntimeModuleState.READY;
        return prepared;
    }

    @Override
    public final synchronized BetaRuntimeModuleResult start() {
        if (state == BetaRuntimeModuleState.RUNNING) return BetaRuntimeModuleResult.running();
        if (state != BetaRuntimeModuleState.READY || context == null) {
            state = BetaRuntimeModuleState.FAILED;
            return BetaRuntimeModuleResult.failure("module is not prepared");
        }
        state = BetaRuntimeModuleState.STARTING;
        try {
            BetaRuntimeModuleResult started = startModule(context);
            if (started == null || !started.success()
                    || started.state() != BetaRuntimeModuleState.RUNNING) {
                cleanupAfterFailedStart();
                state = BetaRuntimeModuleState.FAILED;
                return started == null ? BetaRuntimeModuleResult.failure("start unavailable")
                        : started;
            }
            state = BetaRuntimeModuleState.RUNNING;
            return started;
        } catch (RuntimeException failure) {
            cleanupAfterFailedStart();
            state = BetaRuntimeModuleState.FAILED;
            return BetaRuntimeModuleResult.failure(
                    "start failed: " + failure.getClass().getSimpleName());
        }
    }

    @Override
    public final synchronized BetaRuntimeModuleResult stop() {
        if (state == BetaRuntimeModuleState.STOPPED) return BetaRuntimeModuleResult.stopped();
        state = BetaRuntimeModuleState.STOPPING;
        try {
            stopModule();
            state = BetaRuntimeModuleState.STOPPED;
            context = null;
            return BetaRuntimeModuleResult.stopped();
        } catch (RuntimeException failure) {
            state = BetaRuntimeModuleState.FAILED;
            return BetaRuntimeModuleResult.failure(
                    "stop failed: " + failure.getClass().getSimpleName());
        }
    }

    @Override
    public final synchronized BetaRuntimeModuleState state() {
        return state;
    }

    protected BetaRuntimeModuleResult prepareModule(BetaRuntimeModuleContext context) {
        return BetaRuntimeModuleResult.ready();
    }

    protected abstract BetaRuntimeModuleResult startModule(BetaRuntimeModuleContext context);

    protected abstract void stopModule();

    protected void cleanupAfterFailedStart() {
        try {
            stopModule();
        } catch (RuntimeException ignored) {
            // The kernel records the start failure; cleanup remains best effort.
        }
    }
}
