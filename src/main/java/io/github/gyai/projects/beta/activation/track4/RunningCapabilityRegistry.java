package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.BetaRuntimeModule;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.network.beta.BetaCapabilityAvailability;
import io.github.gyai.projects.network.beta.BetaCapabilityId;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class RunningCapabilityRegistry implements BetaCapabilityAvailability {
    private final Map<BetaCapabilityId, BetaRuntimeModule> producers;

    public RunningCapabilityRegistry(Map<BetaCapabilityId, BetaRuntimeModule> producers) {
        EnumMap<BetaCapabilityId, BetaRuntimeModule> copy = new EnumMap<>(BetaCapabilityId.class);
        if (producers != null) copy.putAll(producers);
        this.producers = Map.copyOf(copy);
    }

    @Override public boolean isAvailable(UUID playerId, BetaCapabilityId capabilityId) {
        return java.util.Optional.ofNullable(producers.get(capabilityId))
                .map(module -> module.state() == BetaRuntimeModuleState.RUNNING)
                .orElse(false);
    }
}
