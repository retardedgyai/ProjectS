package io.github.gyai.projects.beta.activation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public final class BetaRuntimeDependencyResolver {
    public StartupPlan resolve(Collection<? extends BetaRuntimeModule> modules) {
        EnumMap<BetaRuntimeModuleId, BetaRuntimeModule> byId = new EnumMap<>(
                BetaRuntimeModuleId.class);
        if (modules != null) {
            for (BetaRuntimeModule module : modules) {
                if (module == null || module.id() == null) {
                    throw new IllegalArgumentException("Module and ID are required");
                }
                if (byId.putIfAbsent(module.id(), module) != null) {
                    throw new IllegalArgumentException("Duplicate module ID: " + module.id());
                }
                BetaRuntimeModuleDescriptor descriptor = module.descriptor();
                if (descriptor == null || descriptor.id() != module.id()
                        || !descriptor.dependencies().equals(SetSupport.copy(module.dependencies()))) {
                    throw new IllegalArgumentException("Module descriptor does not match contract");
                }
            }
        }
        ArrayList<BetaRuntimeModule> ordered = new ArrayList<>();
        EnumSet<BetaRuntimeModuleId> visiting = EnumSet.noneOf(BetaRuntimeModuleId.class);
        EnumSet<BetaRuntimeModuleId> visited = EnumSet.noneOf(BetaRuntimeModuleId.class);
        for (BetaRuntimeModuleId id : BetaRuntimeModuleId.values()) {
            if (byId.containsKey(id)) visit(id, byId, visiting, visited, ordered);
        }
        return new StartupPlan(ordered, byId);
    }

    private void visit(
            BetaRuntimeModuleId id,
            Map<BetaRuntimeModuleId, BetaRuntimeModule> modules,
            EnumSet<BetaRuntimeModuleId> visiting,
            EnumSet<BetaRuntimeModuleId> visited,
            List<BetaRuntimeModule> ordered
    ) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) {
            throw new IllegalArgumentException("Cyclic module dependency: " + id);
        }
        BetaRuntimeModule module = modules.get(id);
        module.descriptor().dependencies().stream().sorted().forEach(dependency -> {
            if (modules.containsKey(dependency)) {
                visit(dependency, modules, visiting, visited, ordered);
            }
        });
        visiting.remove(id);
        visited.add(id);
        ordered.add(module);
    }

    public record StartupPlan(
            List<BetaRuntimeModule> orderedModules,
            Map<BetaRuntimeModuleId, BetaRuntimeModule> modulesById
    ) {
        public StartupPlan {
            orderedModules = List.copyOf(orderedModules);
            modulesById = Map.copyOf(modulesById);
        }

        public List<BetaRuntimeModule> reverseOrder() {
            ArrayList<BetaRuntimeModule> reverse = new ArrayList<>(orderedModules);
            java.util.Collections.reverse(reverse);
            return List.copyOf(reverse);
        }
    }

    private static final class SetSupport {
        private SetSupport() {
        }

        private static <T> java.util.Set<T> copy(java.util.Set<T> values) {
            return java.util.Set.copyOf(values == null ? java.util.Set.of() : values);
        }
    }
}
