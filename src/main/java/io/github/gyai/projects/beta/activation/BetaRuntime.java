package io.github.gyai.projects.beta.activation;

import io.github.gyai.projects.feature.FeatureFlagSnapshot;
import io.github.gyai.projects.feature.FeatureKey;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public final class BetaRuntime implements AutoCloseable {
    private final BetaRuntimeDependencyResolver.StartupPlan plan;
    private final BetaActivationPolicy policy;
    private final FeatureFlagSnapshot featureFlags;
    private final Set<String> infrastructure;
    private final Clock clock;
    private final BetaRuntimeHealthService health;
    private final BiConsumer<String, RuntimeException> exceptionLogger;
    private final List<BetaRuntimeModule> startedModules = new ArrayList<>();
    private boolean startAttempted;
    private boolean closed;

    BetaRuntime(
            BetaRuntimeDependencyResolver.StartupPlan plan,
            BetaActivationPolicy policy,
            FeatureFlagSnapshot featureFlags,
            Set<String> infrastructure,
            Clock clock,
            BiConsumer<String, RuntimeException> exceptionLogger
    ) {
        this.plan = java.util.Objects.requireNonNull(plan, "plan");
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
        this.featureFlags = java.util.Objects.requireNonNull(featureFlags, "featureFlags");
        this.infrastructure = Set.copyOf(infrastructure == null ? Set.of() : infrastructure);
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.health = new BetaRuntimeHealthService(clock);
        this.exceptionLogger = exceptionLogger == null ? (message, exception) -> { }
                : exceptionLogger;
    }

    public synchronized BetaRuntimeHealthSnapshot start() {
        if (startAttempted) return healthSnapshot();
        startAttempted = true;
        health.started();
        if (closed) {
            health.diagnostic(null, BetaRuntimeDiagnosticCode.START_REJECTED_CLOSED,
                    "runtime is closed", true);
            health.status(BetaRuntimeHealthStatus.STOPPED);
            return healthSnapshot();
        }
        if (policy.audience() == BetaActivationAudience.OFF) {
            for (BetaRuntimeModule module : plan.orderedModules()) {
                setState(module, BetaRuntimeModuleState.DISABLED);
            }
            health.diagnostic(null, BetaRuntimeDiagnosticCode.POLICY_DISABLED,
                    "activation audience is OFF", false);
            health.status(BetaRuntimeHealthStatus.DISABLED);
            return healthSnapshot();
        }

        boolean failed = false;
        for (BetaRuntimeModule module : plan.orderedModules()) {
            BetaRuntimeModuleDescriptor descriptor = module.descriptor();
            if (!featuresEnabled(descriptor.activationFeatures())) {
                setState(module, BetaRuntimeModuleState.DISABLED);
                health.diagnostic(module.id(), BetaRuntimeDiagnosticCode.FEATURE_DISABLED,
                        "activation feature is disabled", false);
                continue;
            }
            Set<BetaRuntimeModuleId> unavailable = unavailableDependencies(descriptor);
            if (!unavailable.isEmpty()) {
                setState(module, BetaRuntimeModuleState.BLOCKED);
                health.blocked(module.id(), unavailable);
                health.diagnostic(module.id(), BetaRuntimeDiagnosticCode.DEPENDENCY_BLOCKED,
                        "required module is not running", false);
                continue;
            }
            if (!infrastructure.containsAll(descriptor.requiredInfrastructure())) {
                setState(module, BetaRuntimeModuleState.BLOCKED);
                health.diagnostic(module.id(), BetaRuntimeDiagnosticCode.INFRASTRUCTURE_MISSING,
                        "required infrastructure is unavailable", false);
                continue;
            }
            boolean mutationAllowed = policy.allowsMutation(descriptor.minimumMutationPolicy());
            if (!mutationAllowed && !descriptor.readOnlyCapable()) {
                setState(module, BetaRuntimeModuleState.BLOCKED);
                health.diagnostic(module.id(), BetaRuntimeDiagnosticCode.MUTATION_POLICY_BLOCKED,
                        "mutation policy is insufficient", false);
                continue;
            }
            BetaRuntimeModuleContext context = new BetaRuntimeModuleContext(
                    policy, featureFlags, infrastructure, clock, !mutationAllowed);
            if (!invokePrepare(module, context) || !invokeStart(module)) {
                failed = true;
                if (policy.failClosed()) {
                    rollbackStarted();
                    break;
                }
            }
        }
        refreshStatus(failed);
        return healthSnapshot();
    }

    private boolean invokePrepare(BetaRuntimeModule module, BetaRuntimeModuleContext context) {
        setState(module, BetaRuntimeModuleState.STARTING);
        try {
            BetaRuntimeModuleResult result = module.prepare(context);
            if (result == null || !result.success()
                    || result.state() != BetaRuntimeModuleState.READY) {
                fail(module, BetaRuntimeDiagnosticCode.PREPARE_FAILED,
                        result == null ? "prepare returned no result" : result.detail(), null);
                return false;
            }
            setState(module, BetaRuntimeModuleState.READY);
            return true;
        } catch (RuntimeException exception) {
            fail(module, BetaRuntimeDiagnosticCode.PREPARE_FAILED,
                    "prepare threw " + exception.getClass().getSimpleName(), exception);
            return false;
        }
    }

    private boolean invokeStart(BetaRuntimeModule module) {
        setState(module, BetaRuntimeModuleState.STARTING);
        try {
            BetaRuntimeModuleResult result = module.start();
            if (result == null || !result.success()
                    || result.state() != BetaRuntimeModuleState.RUNNING) {
                fail(module, BetaRuntimeDiagnosticCode.START_FAILED,
                        result == null ? "start returned no result" : result.detail(), null);
                return false;
            }
            setState(module, BetaRuntimeModuleState.RUNNING);
            startedModules.add(module);
            return true;
        } catch (RuntimeException exception) {
            fail(module, BetaRuntimeDiagnosticCode.START_FAILED,
                    "start threw " + exception.getClass().getSimpleName(), exception);
            return false;
        }
    }

    private void fail(
            BetaRuntimeModule module,
            BetaRuntimeDiagnosticCode code,
            String detail,
            RuntimeException exception
    ) {
        setState(module, BetaRuntimeModuleState.FAILED);
        health.diagnostic(module.id(), code, detail, true);
        if (exception != null) log(module.id() + " " + code, exception);
    }

    private Set<BetaRuntimeModuleId> unavailableDependencies(
            BetaRuntimeModuleDescriptor descriptor
    ) {
        EnumSet<BetaRuntimeModuleId> unavailable = EnumSet.noneOf(BetaRuntimeModuleId.class);
        for (BetaRuntimeModuleId dependency : descriptor.dependencies()) {
            if (healthSnapshot().moduleStates().get(dependency)
                    != BetaRuntimeModuleState.RUNNING) unavailable.add(dependency);
        }
        return Set.copyOf(unavailable);
    }

    private boolean featuresEnabled(Set<FeatureKey> required) {
        for (FeatureKey key : required) if (!featureFlags.isEnabled(key)) return false;
        return true;
    }

    private void rollbackStarted() {
        for (int index = startedModules.size() - 1; index >= 0; index--) {
            stopModule(startedModules.get(index), BetaRuntimeDiagnosticCode.ROLLBACK_FAILED);
        }
        startedModules.clear();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        health.stopped();
        for (int index = startedModules.size() - 1; index >= 0; index--) {
            stopModule(startedModules.get(index), BetaRuntimeDiagnosticCode.STOP_FAILED);
        }
        startedModules.clear();
        health.status(BetaRuntimeHealthStatus.STOPPED);
    }

    private void stopModule(BetaRuntimeModule module, BetaRuntimeDiagnosticCode failureCode) {
        setState(module, BetaRuntimeModuleState.STOPPING);
        try {
            BetaRuntimeModuleResult result = module.stop();
            if (result == null || !result.success()
                    || result.state() != BetaRuntimeModuleState.STOPPED) {
                fail(module, failureCode,
                        result == null ? "stop returned no result" : result.detail(), null);
                return;
            }
            setState(module, BetaRuntimeModuleState.STOPPED);
        } catch (RuntimeException exception) {
            fail(module, failureCode,
                    "stop threw " + exception.getClass().getSimpleName(), exception);
        }
    }

    private void refreshStatus(boolean failed) {
        BetaRuntimeHealthSnapshot snapshot = healthSnapshot();
        boolean anyRunning = snapshot.moduleStates().containsValue(BetaRuntimeModuleState.RUNNING);
        boolean anyBlocked = snapshot.moduleStates().containsValue(BetaRuntimeModuleState.BLOCKED);
        boolean anyFailed = snapshot.moduleStates().containsValue(BetaRuntimeModuleState.FAILED);
        if (failed || anyFailed) health.status(BetaRuntimeHealthStatus.FAILED);
        else if (anyRunning && anyBlocked) health.status(BetaRuntimeHealthStatus.DEGRADED);
        else if (anyRunning) health.status(BetaRuntimeHealthStatus.HEALTHY);
        else if (anyBlocked) health.status(BetaRuntimeHealthStatus.DEGRADED);
        else health.status(BetaRuntimeHealthStatus.DISABLED);
    }

    private void setState(BetaRuntimeModule module, BetaRuntimeModuleState state) {
        health.state(module.id(), state);
    }

    private void log(String message, RuntimeException exception) {
        try {
            exceptionLogger.accept(BetaRuntimeModuleResult.bounded(message), exception);
        } catch (RuntimeException ignored) {
            // Diagnostics must not prevent remaining modules from stopping.
        }
    }

    public BetaActivationPolicy policy() {
        return policy;
    }

    public FeatureFlagSnapshot featureFlags() {
        return featureFlags;
    }

    public BetaRuntimeDependencyResolver.StartupPlan startupPlan() {
        return plan;
    }

    public BetaRuntimeHealthSnapshot healthSnapshot() {
        return health.snapshot(policy.restartRequired());
    }

    public List<BetaRuntimeHealthSnapshot> healthHistory() {
        return health.history();
    }
}
