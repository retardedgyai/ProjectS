package io.github.gyai.projects.combat.damage;

/** Testable boundary around legacy calculation, snapshot, and Bukkit application. */
public interface StarterSwordDamageRuntime {
    DamageResult calculateLegacy(DamageRequest request);

    DamageCalculationSnapshot resolveSnapshot(
            DamageRequest request,
            boolean criticalDecision
    );

    default DamageResult calculateAuthoritative(
            DamageCalculationSnapshot snapshot
    ) {
        return snapshot.calculate();
    }

    DamageApplicationResult applyLegacy(
            DamageRequest request,
            DamageResult legacyResult
    );

    DamageApplicationResult applyAuthoritative(
            DamageRequest request,
            DamageResult authoritativeResult
    );
}
