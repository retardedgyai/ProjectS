package io.github.gyai.projects.combat.damage;

import java.util.Objects;
import java.util.function.Consumer;

/** Production adapter retaining DamageService's calculate/apply semantics. */
public final class DamageServiceShadowRuntime
        implements DamageShadowLegacyRuntime {
    private final DamageService damageService;

    public DamageServiceShadowRuntime(DamageService damageService) {
        this.damageService = Objects.requireNonNull(
                damageService, "damageService");
    }

    @Override
    public DamageApplicationResult apply(DamageRequest request) {
        return damageService.apply(request);
    }

    @Override
    public DamageApplicationResult apply(
            DamageRequest request,
            Consumer<DamageResult> calculationObserver,
            Consumer<RuntimeException> calculationFailureObserver
    ) {
        return damageService.apply(
                request, calculationObserver, calculationFailureObserver);
    }

    @Override
    public DamageCalculationSnapshot resolveSnapshot(
            DamageRequest request,
            boolean critical
    ) {
        return damageService.resolveSnapshot(request, critical);
    }
}
