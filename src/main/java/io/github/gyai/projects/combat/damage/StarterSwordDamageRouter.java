package io.github.gyai.projects.combat.damage;

import java.util.Objects;
import java.util.Optional;

/** Selects exactly one application path after all new-route work is complete. */
public final class StarterSwordDamageRouter {
    private final StarterSwordDamageRuntime runtime;
    private final StarterSwordShadowRuntime shadow;
    private final StarterSwordRouteController controller;
    private final StarterSwordDamageRoutePolicy policy;

    public StarterSwordDamageRouter(
            StarterSwordDamageRuntime runtime,
            StarterSwordShadowRuntime shadow,
            StarterSwordRouteController controller,
            StarterSwordDamageRoutePolicy policy
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.shadow = Objects.requireNonNull(shadow, "shadow");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public DamageApplicationResult apply(DamageRequest request) {
        if (!controller.enabled()) {
            safeRecordDecision(StarterSwordRouteDecision.LEGACY_DISABLED);
            DamageApplicationResult result = shadow.apply(request);
            safeRecordApplication(false, result.attempted());
            return result;
        }

        DamageShadowRuntimeContext context;
        try {
            context = shadow.resolveContext(request);
        } catch (RuntimeException exception) {
            return applyLegacy(
                    request,
                    null,
                    null,
                    StarterSwordRouteDecision.LEGACY_INVALID_SNAPSHOT,
                    false);
        }

        DamageResult legacy;
        try {
            legacy = runtime.calculateLegacy(request);
        } catch (RuntimeException exception) {
            safeRecordDecision(
                    StarterSwordRouteDecision.LEGACY_CALCULATION_FAILURE);
            throw exception;
        }

        StarterSwordRouteDecision initialDecision;
        try {
            initialDecision = policy.decide(routeInput(
                    request,
                    context,
                    legacy.critical(),
                    request.target().getAbsorptionAmount()));
        } catch (RuntimeException exception) {
            return applyLegacy(
                    request,
                    context,
                    legacy,
                    StarterSwordRouteDecision.LEGACY_ROUTE_FAILURE,
                    false);
        }
        if (!initialDecision.authoritative()) {
            return applyLegacy(
                    request, context, legacy, initialDecision, true);
        }

        DamageCalculationSnapshot snapshot;
        try {
            snapshot = runtime.resolveSnapshot(
                    request, legacy.critical());
            StarterSwordRouteDecision snapshotDecision = policy.decide(
                    routeInput(
                            request,
                            context,
                            snapshot.critical(),
                            snapshot.defenseSnapshot().shieldAmount()));
            if (!snapshotDecision.authoritative()) {
                return applyLegacy(
                        request, context, legacy, snapshotDecision, false);
            }
        } catch (RuntimeException exception) {
            return applyLegacy(
                    request,
                    context,
                    legacy,
                    StarterSwordRouteDecision.LEGACY_INVALID_SNAPSHOT,
                    false);
        }

        DamageResult authoritative;
        try {
            authoritative = runtime.calculateAuthoritative(snapshot);
        } catch (RuntimeException exception) {
            return applyLegacy(
                    request,
                    context,
                    legacy,
                    StarterSwordRouteDecision.LEGACY_CALCULATION_FAILURE,
                    false);
        }
        if (!StarterSwordDamageResultValidator.isSafe(authoritative)
                || authoritative.critical()
                || authoritative.shieldDamage() > 0.0) {
            return applyLegacy(
                    request,
                    context,
                    legacy,
                    StarterSwordRouteDecision.LEGACY_INVALID_RESULT,
                    false);
        }

        if (shadow.enabled()) {
            try {
                Optional<DamageShadowComparison> comparison =
                        shadow.comparePrecalculatedSafely(
                                context,
                                request,
                                legacy,
                                authoritative,
                                snapshot);
                comparison.ifPresent(value ->
                        safeRecordAuthoritativeShadow(value.matches()));
            } catch (RuntimeException ignored) {
                // The route was already validated; observer failure is fail-open.
            }
        }

        safeRecordDecision(StarterSwordRouteDecision.NEW_AUTHORITATIVE);
        DamageApplicationResult applied =
                runtime.applyAuthoritative(request, authoritative);
        safeRecordApplication(true, applied.attempted());
        return applied;
    }

    public StarterSwordRouteController controller() {
        return controller;
    }

    private DamageApplicationResult applyLegacy(
            DamageRequest request,
            DamageShadowRuntimeContext context,
            DamageResult legacy,
            StarterSwordRouteDecision decision,
            boolean observeShadow
    ) {
        safeRecordDecision(decision);
        if (legacy == null) {
            DamageResult calculated = runtime.calculateLegacy(request);
            DamageApplicationResult applied =
                    runtime.applyLegacy(request, calculated);
            safeRecordApplication(false, applied.attempted());
            return applied;
        }
        if (observeShadow && shadow.enabled() && context != null) {
            try {
                shadow.compareLegacySafely(context, request, legacy);
            } catch (RuntimeException ignored) {
                // Observational failure cannot cancel legacy fallback.
            }
        }
        DamageApplicationResult applied =
                runtime.applyLegacy(request, legacy);
        safeRecordApplication(false, applied.attempted());
        return applied;
    }

    private StarterSwordRouteInput routeInput(
            DamageRequest request,
            DamageShadowRuntimeContext context,
            boolean critical,
            double shieldAmount
    ) {
        return new StarterSwordRouteInput(
                controller.enabled(),
                context.itemId(),
                request.damageType(),
                request.damageKind(),
                request.mode(),
                request.attackMetadata(),
                critical,
                shieldAmount,
                hasUnsupportedSpecialState(request));
    }

    private static boolean hasUnsupportedSpecialState(DamageRequest request) {
        return request.areaDamage()
                || request.offenseSnapshot() != null
                || !"normal_attack".equals(request.skillId());
    }

    private void safeRecordDecision(StarterSwordRouteDecision decision) {
        try {
            controller.recordDecision(decision);
        } catch (RuntimeException ignored) {
            // Metrics cannot change the selected combat route.
        }
    }

    private void safeRecordApplication(boolean authoritative, boolean attempted) {
        try {
            controller.recordApplication(authoritative, attempted);
        } catch (RuntimeException ignored) {
            // Metrics cannot change an already completed application.
        }
    }

    private void safeRecordAuthoritativeShadow(boolean matches) {
        try {
            controller.recordAuthoritativeShadow(matches);
        } catch (RuntimeException ignored) {
            // Observer metrics are fail-open.
        }
    }
}
