package io.github.gyai.projects.beta.activation.track2;

import io.github.gyai.projects.beta.activation.BetaActivationAudience;
import io.github.gyai.projects.beta.activation.BetaActivationTarget;
import io.github.gyai.projects.beta.activation.BetaMutationPolicy;
import io.github.gyai.projects.beta.activation.BetaRuntimeModule;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleContext;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleDescriptor;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleId;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleResult;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.feature.FeatureKey;

import java.util.List;
import java.util.Set;

/** COMBAT_ELEMENTS provider module; intentionally absent from the central module list. */
public final class CombatElementsRuntimeModule implements BetaRuntimeModule, AutoCloseable {
    public static final String TRAINING_DUMMY_INFRASTRUCTURE = "training-dummy-boundary";
    public static final String DAMAGE_SERVICE_INFRASTRUCTURE = "damage-service-secondary";

    private final TrainingDummyElementRuntime runtime;
    private BetaRuntimeModuleState state = BetaRuntimeModuleState.NOT_INSTALLED;
    private String healthDetail = "not prepared";

    public CombatElementsRuntimeModule(TrainingDummyElementRuntime runtime) {
        if (runtime == null) throw new IllegalArgumentException("runtime is required");
        this.runtime = runtime;
    }

    @Override
    public BetaRuntimeModuleId id() {
        return BetaRuntimeModuleId.COMBAT_ELEMENTS;
    }

    @Override
    public Set<BetaRuntimeModuleId> dependencies() {
        return Set.of();
    }

    @Override
    public BetaRuntimeModuleDescriptor descriptor() {
        return new BetaRuntimeModuleDescriptor(
                id(), dependencies(), Set.of(FeatureKey.FIRE_SYSTEM, FeatureKey.ICE_SYSTEM),
                BetaMutationPolicy.READ_ONLY, true,
                Set.of(TRAINING_DUMMY_INFRASTRUCTURE, DAMAGE_SERVICE_INFRASTRUCTURE));
    }

    @Override
    public synchronized BetaRuntimeModuleResult prepare(BetaRuntimeModuleContext context) {
        if (state == BetaRuntimeModuleState.READY || state == BetaRuntimeModuleState.RUNNING) {
            return new BetaRuntimeModuleResult(true, state, healthDetail);
        }
        if (context == null) return fail("missing runtime context");
        if (!context.featureFlags().isEnabled(FeatureKey.FIRE_SYSTEM)
                || !context.featureFlags().isEnabled(FeatureKey.ICE_SYSTEM)
                || context.activationPolicy().audience() == BetaActivationAudience.OFF) {
            state = BetaRuntimeModuleState.DISABLED;
            healthDetail = "element flags or activation audience disabled";
            return new BetaRuntimeModuleResult(true, state, healthDetail);
        }
        if (!context.activationPolicy().allowsTarget(BetaActivationTarget.TRAINING_DUMMY)
                || !context.activationPolicy().allowsMutation(BetaMutationPolicy.READ_ONLY)) {
            state = BetaRuntimeModuleState.BLOCKED;
            healthDetail = "Training Dummy READ_ONLY policy denied";
            return new BetaRuntimeModuleResult(false, state, healthDetail);
        }
        if (!context.availableInfrastructure().containsAll(descriptor().requiredInfrastructure())) {
            state = BetaRuntimeModuleState.BLOCKED;
            healthDetail = "required element infrastructure unavailable";
            return new BetaRuntimeModuleResult(false, state, healthDetail);
        }
        if (!runtime.configure(context.activationPolicy())) {
            return fail("element runtime policy snapshot refused");
        }
        state = BetaRuntimeModuleState.READY;
        healthDetail = "ready; no gameplay registration performed";
        return BetaRuntimeModuleResult.ready();
    }

    @Override
    public synchronized BetaRuntimeModuleResult start() {
        if (state == BetaRuntimeModuleState.RUNNING) return BetaRuntimeModuleResult.running();
        if (state != BetaRuntimeModuleState.READY) return fail("module was not ready");
        try {
            if (!runtime.start()) return fail("element runtime refused start");
            state = BetaRuntimeModuleState.RUNNING;
            healthDetail = "running staging Training Dummy adapter";
            return BetaRuntimeModuleResult.running();
        } catch (RuntimeException exception) {
            runtime.close();
            return fail("element runtime start failed");
        }
    }

    @Override
    public synchronized BetaRuntimeModuleResult stop() {
        if (state == BetaRuntimeModuleState.STOPPED) return BetaRuntimeModuleResult.stopped();
        try {
            runtime.close();
            state = BetaRuntimeModuleState.STOPPED;
            healthDetail = "stopped and cleared";
            return BetaRuntimeModuleResult.stopped();
        } catch (RuntimeException exception) {
            return fail("element runtime stop failed");
        }
    }

    @Override
    public synchronized BetaRuntimeModuleState state() {
        return state;
    }

    public synchronized Health health() {
        List<String> diagnostics = runtime.diagnostics();
        return new Health(state, healthDetail,
                runtime.running(), runtime.profileCount(), diagnostics);
    }

    @Override
    public void close() {
        stop();
    }

    private BetaRuntimeModuleResult fail(String detail) {
        state = BetaRuntimeModuleState.FAILED;
        healthDetail = detail;
        return BetaRuntimeModuleResult.failure(detail);
    }

    public record Health(
            BetaRuntimeModuleState state,
            String detail,
            boolean schedulerRunning,
            int profileCount,
            List<String> diagnostics
    ) {
        public Health {
            if (state == null || detail == null || detail.length() > 256
                    || profileCount < 0 || diagnostics == null || diagnostics.size() > 64) {
                throw new IllegalArgumentException("Invalid combat element health");
            }
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
