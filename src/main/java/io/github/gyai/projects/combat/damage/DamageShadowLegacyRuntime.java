package io.github.gyai.projects.combat.damage;

import java.util.function.Consumer;

/** Narrow legacy boundary used by observational damage-shadow routes. */
public interface DamageShadowLegacyRuntime {
    DamageApplicationResult apply(DamageRequest request);

    DamageApplicationResult apply(
            DamageRequest request,
            Consumer<DamageResult> calculationObserver,
            Consumer<RuntimeException> calculationFailureObserver);

    DamageCalculationSnapshot resolveSnapshot(
            DamageRequest request,
            boolean critical);
}
