package io.github.gyai.projects.beta.activation.track1.module;

import io.github.gyai.projects.beta.activation.BetaRuntimeModule;
import io.github.gyai.projects.beta.activation.track1.spi.BetaRuntimeModuleProvider;

import java.util.List;

/** Provider only; central Runtime registration is deliberately absent. */
public final class Track1RuntimeModuleProvider implements BetaRuntimeModuleProvider {
    private final List<BetaRuntimeModule> modules;

    public Track1RuntimeModuleProvider(PlayerPersistenceRuntimeModule playerPersistence,
                                       EquipmentRuntimeModule equipment) {
        if (playerPersistence == null || equipment == null) {
            throw new IllegalArgumentException("both Track 1 modules are required");
        }
        modules = List.of(playerPersistence, equipment);
    }

    @Override public List<BetaRuntimeModule> modules() { return modules; }
}
