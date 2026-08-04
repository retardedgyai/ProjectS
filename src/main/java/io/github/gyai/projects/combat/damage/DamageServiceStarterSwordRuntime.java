package io.github.gyai.projects.combat.damage;

import java.util.Objects;

public final class DamageServiceStarterSwordRuntime
        implements StarterSwordDamageRuntime {
    private final DamageService damageService;

    public DamageServiceStarterSwordRuntime(DamageService damageService) {
        this.damageService = Objects.requireNonNull(
                damageService, "damageService");
    }

    @Override
    public DamageResult calculateLegacy(DamageRequest request) {
        return damageService.calculate(request);
    }

    @Override
    public DamageCalculationSnapshot resolveSnapshot(
            DamageRequest request,
            boolean criticalDecision
    ) {
        return damageService.resolveSnapshot(request, criticalDecision);
    }

    @Override
    public DamageApplicationResult applyLegacy(
            DamageRequest request,
            DamageResult legacyResult
    ) {
        return damageService.applyResolved(request, legacyResult);
    }

    @Override
    public DamageApplicationResult applyAuthoritative(
            DamageRequest request,
            DamageResult authoritativeResult
    ) {
        return damageService.applyResolved(request, authoritativeResult);
    }
}
