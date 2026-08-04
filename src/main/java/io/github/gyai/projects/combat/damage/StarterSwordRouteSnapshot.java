package io.github.gyai.projects.combat.damage;

import java.time.Instant;
import java.util.Map;

public record StarterSwordRouteSnapshot(
        boolean enabled,
        Instant sessionStartedAt,
        long totalHits,
        long newAuthoritativeCount,
        long legacyFallbackCount,
        Map<StarterSwordRouteDecision, Long> decisionCounts,
        long newRouteFailureCount,
        long newRouteAppliedCount,
        long legacyAppliedCount,
        long applicationBoundaryCompletedCount,
        long authoritativeShadowMatchCount,
        long authoritativeShadowMismatchCount,
        long criticalFallbackCount,
        long shieldFallbackCount
) {
    public StarterSwordRouteSnapshot {
        decisionCounts = Map.copyOf(decisionCounts);
    }
}
