package io.github.gyai.projects.combat.damage;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DamageShadowValidationSnapshot(
        boolean enabled,
        Instant sessionStartedAt,
        long comparisonCount,
        long matchCount,
        long mismatchCount,
        long legacyFailureCount,
        long shadowFailureCount,
        double maximumAbsoluteError,
        double maximumRelativeError,
        double averageAbsoluteError,
        long criticalCount,
        long nonCriticalCount,
        long shieldPresentCount,
        long shieldAbsentCount,
        Map<DamageType, Long> damageTypeCounts,
        Map<DamageKind, Long> damageKindCounts,
        Map<DamageMode, Long> damageModeCounts,
        Map<DamageShadowTargetType, Long> targetTypeCounts,
        Map<Integer, Long> enhancementLevelCounts,
        List<DamageShadowMismatchDetail> mismatchDetails,
        int mismatchDetailLimit
) {
    public DamageShadowValidationSnapshot {
        damageTypeCounts = Map.copyOf(damageTypeCounts);
        damageKindCounts = Map.copyOf(damageKindCounts);
        damageModeCounts = Map.copyOf(damageModeCounts);
        targetTypeCounts = Map.copyOf(targetTypeCounts);
        enhancementLevelCounts = Map.copyOf(enhancementLevelCounts);
        mismatchDetails = List.copyOf(mismatchDetails);
    }
}
