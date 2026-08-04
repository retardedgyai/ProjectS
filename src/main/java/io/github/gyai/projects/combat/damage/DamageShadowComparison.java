package io.github.gyai.projects.combat.damage;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DamageShadowComparison(
        DamageResult legacyResult,
        DamageResult shadowResult,
        DamageCalculationSnapshot snapshot,
        Map<String, Double> numericDifferences,
        List<String> contextDifferences
) {
    public DamageShadowComparison {
        legacyResult = Objects.requireNonNull(legacyResult, "legacyResult");
        shadowResult = Objects.requireNonNull(shadowResult, "shadowResult");
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        numericDifferences = numericDifferences == null
                ? Map.of() : Map.copyOf(numericDifferences);
        contextDifferences = contextDifferences == null
                ? List.of() : List.copyOf(contextDifferences);
    }

    public boolean matches() {
        return numericDifferences.isEmpty() && contextDifferences.isEmpty();
    }
}
