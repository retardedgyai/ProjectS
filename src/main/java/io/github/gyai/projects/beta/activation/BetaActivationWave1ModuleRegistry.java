package io.github.gyai.projects.beta.activation;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;

/** Immutable central registration point for the eight Activation Wave 1 modules. */
public final class BetaActivationWave1ModuleRegistry {
    private final List<BetaRuntimeModule> modules;

    public BetaActivationWave1ModuleRegistry(Collection<? extends BetaRuntimeModule> source) {
        if (source == null) throw new IllegalArgumentException("modules are required");
        EnumMap<BetaRuntimeModuleId, BetaRuntimeModule> values =
                new EnumMap<>(BetaRuntimeModuleId.class);
        for (BetaRuntimeModule module : source) {
            if (module == null || values.put(module.id(), module) != null) {
                throw new IllegalArgumentException("duplicate or null module");
            }
        }
        if (!values.keySet().equals(Set.of(BetaRuntimeModuleId.values()))) {
            throw new IllegalArgumentException("all eight Beta modules must be registered");
        }
        modules = java.util.Arrays.stream(BetaRuntimeModuleId.values())
                .map(values::get).toList();
    }

    public List<BetaRuntimeModule> modules() { return modules; }
    public int size() { return modules.size(); }
}
