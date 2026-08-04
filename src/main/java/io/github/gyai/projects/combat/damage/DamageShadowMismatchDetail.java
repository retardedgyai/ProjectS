package io.github.gyai.projects.combat.damage;

import java.util.Objects;

public record DamageShadowMismatchDetail(
        DamageShadowRuntimeContext context,
        boolean criticalDecision,
        DamageCalculationSnapshot calculationSnapshot,
        AttackMetadata attackMetadata,
        DamageResult legacyResult,
        DamageResult shadowResult,
        DamageShadowNumericReport numericReport,
        java.util.List<String> contextDifferences
) {
    public DamageShadowMismatchDetail {
        context = Objects.requireNonNull(context, "context");
        calculationSnapshot = Objects.requireNonNull(
                calculationSnapshot, "calculationSnapshot");
        attackMetadata = Objects.requireNonNull(
                attackMetadata, "attackMetadata");
        legacyResult = Objects.requireNonNull(legacyResult, "legacyResult");
        shadowResult = Objects.requireNonNull(shadowResult, "shadowResult");
        numericReport = Objects.requireNonNull(
                numericReport, "numericReport");
        contextDifferences = contextDifferences == null
                ? java.util.List.of() : java.util.List.copyOf(contextDifferences);
    }
}
