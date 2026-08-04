package io.github.gyai.projects.combat.damage;

import java.util.Optional;

public interface StarterSwordShadowRuntime {
    boolean enabled();

    DamageApplicationResult apply(DamageRequest request);

    DamageShadowRuntimeContext resolveContext(DamageRequest request);

    void compareLegacySafely(
            DamageShadowRuntimeContext context,
            DamageRequest request,
            DamageResult legacyResult
    );

    Optional<DamageShadowComparison> comparePrecalculatedSafely(
            DamageShadowRuntimeContext context,
            DamageRequest request,
            DamageResult legacyResult,
            DamageResult shadowResult,
            DamageCalculationSnapshot snapshot
    );
}
