package io.github.gyai.projects.beta.activation.track1.module;

import io.github.gyai.projects.beta.activation.BetaMutationPolicy;
import io.github.gyai.projects.beta.activation.BetaRuntimeModule;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleContext;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleDescriptor;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleId;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleResult;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.beta.activation.track1.equipment.EquipmentInspectionService;
import io.github.gyai.projects.feature.FeatureKey;

import java.util.Set;

public final class EquipmentRuntimeModule implements BetaRuntimeModule {
    private final EquipmentInspectionService service;
    private BetaRuntimeModuleState state = BetaRuntimeModuleState.NOT_INSTALLED;

    public EquipmentRuntimeModule(EquipmentInspectionService service) {
        if (service == null) throw new IllegalArgumentException("equipment service is required");
        this.service = service;
    }

    @Override public BetaRuntimeModuleId id() { return BetaRuntimeModuleId.EQUIPMENT; }
    @Override public Set<BetaRuntimeModuleId> dependencies() { return Set.of(); }
    @Override public BetaRuntimeModuleDescriptor descriptor() {
        return new BetaRuntimeModuleDescriptor(id(), dependencies(),
                Set.of(FeatureKey.EQUIPMENT_V2), BetaMutationPolicy.READ_ONLY,
                true, Set.of("track1.inventory-reader"));
    }
    @Override public synchronized BetaRuntimeModuleResult prepare(BetaRuntimeModuleContext context) {
        if (state == BetaRuntimeModuleState.RUNNING) return BetaRuntimeModuleResult.running();
        if (context == null || !context.featureFlags().isEnabled(FeatureKey.EQUIPMENT_V2)
                || context.activationPolicy().audience()
                == io.github.gyai.projects.beta.activation.BetaActivationAudience.OFF) {
            state = BetaRuntimeModuleState.DISABLED;
            return new BetaRuntimeModuleResult(false, state, "equipment activation is disabled");
        }
        state = BetaRuntimeModuleState.READY;
        return BetaRuntimeModuleResult.ready();
    }
    @Override public synchronized BetaRuntimeModuleResult start() {
        if (state == BetaRuntimeModuleState.RUNNING) return BetaRuntimeModuleResult.running();
        if (state != BetaRuntimeModuleState.READY) return BetaRuntimeModuleResult.failure("module is not ready");
        try {
            service.start();
            state = BetaRuntimeModuleState.RUNNING;
            return BetaRuntimeModuleResult.running();
        } catch (RuntimeException exception) {
            state = BetaRuntimeModuleState.FAILED;
            return BetaRuntimeModuleResult.failure("equipment service start failed");
        }
    }
    @Override public synchronized BetaRuntimeModuleResult stop() {
        if (state == BetaRuntimeModuleState.STOPPED) return BetaRuntimeModuleResult.stopped();
        try {
            service.close();
            state = BetaRuntimeModuleState.STOPPED;
            return BetaRuntimeModuleResult.stopped();
        } catch (RuntimeException exception) {
            state = BetaRuntimeModuleState.FAILED;
            return BetaRuntimeModuleResult.failure("equipment cleanup failed");
        }
    }
    @Override public synchronized BetaRuntimeModuleState state() { return state; }
}
