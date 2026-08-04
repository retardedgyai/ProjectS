package io.github.gyai.projects.combat.damage;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Objects;

/** Bounded route metrics without per-player or per-entity maps. */
public final class StarterSwordRouteTracker {
    private Instant sessionStartedAt = Instant.EPOCH;
    private long totalHits;
    private long newAuthoritativeCount;
    private long legacyFallbackCount;
    private long newRouteFailureCount;
    private long newRouteAppliedCount;
    private long legacyAppliedCount;
    private long applicationBoundaryCompletedCount;
    private long authoritativeShadowMatchCount;
    private long authoritativeShadowMismatchCount;
    private final EnumMap<StarterSwordRouteDecision, Long> decisionCounts =
            new EnumMap<>(StarterSwordRouteDecision.class);

    public StarterSwordRouteTracker() {
        reset(Instant.EPOCH);
    }

    public synchronized void reset(Instant startedAt) {
        sessionStartedAt = Objects.requireNonNull(startedAt, "startedAt");
        totalHits = 0;
        newAuthoritativeCount = 0;
        legacyFallbackCount = 0;
        newRouteFailureCount = 0;
        newRouteAppliedCount = 0;
        legacyAppliedCount = 0;
        applicationBoundaryCompletedCount = 0;
        authoritativeShadowMatchCount = 0;
        authoritativeShadowMismatchCount = 0;
        decisionCounts.clear();
        for (StarterSwordRouteDecision decision
                : StarterSwordRouteDecision.values()) {
            decisionCounts.put(decision, 0L);
        }
    }

    public synchronized void recordDecision(
            StarterSwordRouteDecision decision
    ) {
        totalHits = increment(totalHits);
        decisionCounts.merge(decision, 1L,
                StarterSwordRouteTracker::saturatedAdd);
        if (decision.authoritative()) {
            newAuthoritativeCount = increment(newAuthoritativeCount);
        } else {
            legacyFallbackCount = increment(legacyFallbackCount);
        }
        if (decision == StarterSwordRouteDecision.LEGACY_ROUTE_FAILURE
                || decision == StarterSwordRouteDecision.LEGACY_INVALID_SNAPSHOT
                || decision == StarterSwordRouteDecision.LEGACY_CALCULATION_FAILURE
                || decision == StarterSwordRouteDecision.LEGACY_INVALID_RESULT) {
            newRouteFailureCount = increment(newRouteFailureCount);
        }
    }

    public synchronized void recordApplication(
            boolean authoritative,
            boolean attempted
    ) {
        applicationBoundaryCompletedCount = increment(
                applicationBoundaryCompletedCount);
        if (!attempted) {
            return;
        }
        if (authoritative) {
            newRouteAppliedCount = increment(newRouteAppliedCount);
        } else {
            legacyAppliedCount = increment(legacyAppliedCount);
        }
    }

    public synchronized void recordAuthoritativeShadow(
            boolean matches
    ) {
        if (matches) {
            authoritativeShadowMatchCount = increment(
                    authoritativeShadowMatchCount);
        } else {
            authoritativeShadowMismatchCount = increment(
                    authoritativeShadowMismatchCount);
        }
    }

    public synchronized StarterSwordRouteSnapshot snapshot(boolean enabled) {
        return new StarterSwordRouteSnapshot(
                enabled,
                sessionStartedAt,
                totalHits,
                newAuthoritativeCount,
                legacyFallbackCount,
                decisionCounts,
                newRouteFailureCount,
                newRouteAppliedCount,
                legacyAppliedCount,
                applicationBoundaryCompletedCount,
                authoritativeShadowMatchCount,
                authoritativeShadowMismatchCount,
                decisionCounts.get(StarterSwordRouteDecision.LEGACY_CRITICAL),
                decisionCounts.get(StarterSwordRouteDecision.LEGACY_SHIELD));
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? value : value + 1;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
