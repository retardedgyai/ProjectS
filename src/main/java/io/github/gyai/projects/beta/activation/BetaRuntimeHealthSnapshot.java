package io.github.gyai.projects.beta.activation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record BetaRuntimeHealthSnapshot(
        Instant timestamp,
        BetaRuntimeHealthStatus status,
        Map<BetaRuntimeModuleId, BetaRuntimeModuleState> moduleStates,
        Map<BetaRuntimeModuleId, Set<BetaRuntimeModuleId>> blockedDependencies,
        List<BetaRuntimeDiagnostic> diagnostics,
        long startCount,
        long stopCount,
        String lastFailure,
        boolean restartRequired
) {
    public BetaRuntimeHealthSnapshot {
        if (timestamp == null || status == null || startCount < 0 || stopCount < 0) {
            throw new IllegalArgumentException("Invalid runtime health snapshot");
        }
        moduleStates = Map.copyOf(moduleStates == null ? Map.of() : moduleStates);
        blockedDependencies = immutableSets(blockedDependencies);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        lastFailure = BetaRuntimeModuleResult.bounded(lastFailure);
    }

    private static Map<BetaRuntimeModuleId, Set<BetaRuntimeModuleId>> immutableSets(
            Map<BetaRuntimeModuleId, Set<BetaRuntimeModuleId>> source
    ) {
        if (source == null || source.isEmpty()) return Map.of();
        java.util.EnumMap<BetaRuntimeModuleId, Set<BetaRuntimeModuleId>> result =
                new java.util.EnumMap<>(BetaRuntimeModuleId.class);
        source.forEach((key, value) -> result.put(key,
                Set.copyOf(value == null ? Set.of() : value)));
        return Map.copyOf(result);
    }
}
