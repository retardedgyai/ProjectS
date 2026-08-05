package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.beta.activation.BetaActivationAudience;
import io.github.gyai.projects.beta.activation.BetaMutationPolicy;
import io.github.gyai.projects.beta.activation.BetaRuntimeModule;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleContext;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleDescriptor;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleId;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleResult;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.feature.FeatureKey;

import java.util.Set;

/** Idempotent lifecycle wrapper for one Track 3 operation group. */
public final class Track3RuntimeModule implements BetaRuntimeModule {
    private static final Set<String> INFRASTRUCTURE = Set.of(
            "track3.staging-inventory", "track3.staging-transaction-journal");

    private final BetaRuntimeModuleId id;
    private final StagingEconomyService.OperationGroup group;
    private final StagingEconomyService service;
    private final BetaRuntimeModuleDescriptor descriptor;
    private final StagingTransactionRecoveryService recovery;
    private BetaRuntimeModuleState state = BetaRuntimeModuleState.NOT_INSTALLED;
    private long startCount;
    private long stopCount;
    private String lastFailure = "";

    Track3RuntimeModule(
            BetaRuntimeModuleId id,
            StagingEconomyService.OperationGroup group,
            StagingEconomyService service
    ) {
        this(id, group, service, null);
    }

    Track3RuntimeModule(
            BetaRuntimeModuleId id,
            StagingEconomyService.OperationGroup group,
            StagingEconomyService service,
            StagingTransactionRecoveryService recovery
    ) {
        this.id = id;
        this.group = group;
        this.service = service;
        this.recovery = recovery;
        Set<FeatureKey> features;
        Set<BetaRuntimeModuleId> dependencies;
        if (id == BetaRuntimeModuleId.GATHERING_CRAFTING) {
            features = Set.of(FeatureKey.GATHERING, FeatureKey.REFINING, FeatureKey.CRAFTING);
            dependencies = Set.of();
        } else if (id == BetaRuntimeModuleId.ENHANCEMENT_REPAIR) {
            features = Set.of(FeatureKey.TIER_PROMOTION,
                    FeatureKey.ENHANCEMENT_V2, FeatureKey.REPAIR_V2);
            dependencies = Set.of(BetaRuntimeModuleId.GATHERING_CRAFTING);
        } else {
            throw new IllegalArgumentException("unsupported Track 3 module ID");
        }
        descriptor = new BetaRuntimeModuleDescriptor(
                id, dependencies, features, BetaMutationPolicy.STAGING_WRITE,
                false, INFRASTRUCTURE);
    }

    @Override
    public BetaRuntimeModuleId id() {
        return id;
    }

    @Override
    public Set<BetaRuntimeModuleId> dependencies() {
        return descriptor.dependencies();
    }

    @Override
    public BetaRuntimeModuleDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public synchronized BetaRuntimeModuleResult prepare(BetaRuntimeModuleContext context) {
        if (state == BetaRuntimeModuleState.READY) return BetaRuntimeModuleResult.ready();
        if (state == BetaRuntimeModuleState.RUNNING) return BetaRuntimeModuleResult.running();
        if (context == null
                || context.activationPolicy().audience() != BetaActivationAudience.ALLOWLIST
                || context.activationPolicy().mutationPolicy() != BetaMutationPolicy.STAGING_WRITE
                || context.activationPolicy().allowedWorlds().isEmpty()
                || context.readOnlyMode()
                || !context.availableInfrastructure().containsAll(INFRASTRUCTURE)
                || !descriptor.activationFeatures().stream()
                .allMatch(context.featureFlags()::isEnabled)) {
            return fail("Track 3 staging gates or features are closed");
        }
        if (recovery != null) {
            StagingTransactionRecoveryResult recovered;
            try { recovered = recovery.recoverOnce(); }
            catch (RuntimeException failure) { return block("durable recovery scan failed"); }
            if (recovered.recoveryRequired() > 0 || recovered.quarantined() > 0) {
                return block("unresolved durable staging transactions");
            }
        }
        state = BetaRuntimeModuleState.READY;
        lastFailure = "";
        return BetaRuntimeModuleResult.ready();
    }

    @Override
    public synchronized BetaRuntimeModuleResult start() {
        if (state == BetaRuntimeModuleState.RUNNING) return BetaRuntimeModuleResult.running();
        if (state != BetaRuntimeModuleState.READY) return fail("module was not prepared");
        try {
            service.setGroupRunning(group, true);
            state = BetaRuntimeModuleState.RUNNING;
            startCount = Math.addExact(startCount, 1);
            return BetaRuntimeModuleResult.running();
        } catch (RuntimeException failure) {
            service.setGroupRunning(group, false);
            return fail(failure.getClass().getSimpleName());
        }
    }

    @Override
    public synchronized BetaRuntimeModuleResult stop() {
        if (state == BetaRuntimeModuleState.STOPPED
                || state == BetaRuntimeModuleState.NOT_INSTALLED) {
            state = BetaRuntimeModuleState.STOPPED;
            return BetaRuntimeModuleResult.stopped();
        }
        try {
            service.setGroupRunning(group, false);
            state = BetaRuntimeModuleState.STOPPED;
            stopCount = Math.addExact(stopCount, 1);
            return BetaRuntimeModuleResult.stopped();
        } catch (RuntimeException failure) {
            return fail(failure.getClass().getSimpleName());
        }
    }

    @Override
    public synchronized BetaRuntimeModuleState state() {
        return state;
    }

    public synchronized Health health() {
        return new Health(id, state, startCount, stopCount, lastFailure);
    }

    private BetaRuntimeModuleResult fail(String detail) {
        state = BetaRuntimeModuleState.FAILED;
        lastFailure = detail == null ? "" : detail;
        if (lastFailure.length() > 256) lastFailure = lastFailure.substring(0, 256);
        return BetaRuntimeModuleResult.failure(lastFailure);
    }

    private BetaRuntimeModuleResult block(String detail) {
        state = BetaRuntimeModuleState.BLOCKED;
        lastFailure = detail;
        return new BetaRuntimeModuleResult(false, state, detail);
    }

    public record Health(
            BetaRuntimeModuleId moduleId,
            BetaRuntimeModuleState state,
            long startCount,
            long stopCount,
            String lastFailure
    ) {
    }
}
