package io.github.gyai.projects.combat.damage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Thread-safe, bounded in-memory metrics for one validation session. */
public final class DamageShadowValidationTracker {
    public static final int DEFAULT_MISMATCH_DETAIL_LIMIT = 50;

    private final int mismatchDetailLimit;
    private Instant sessionStartedAt;
    private long comparisonCount;
    private long matchCount;
    private long mismatchCount;
    private long legacyFailureCount;
    private long shadowFailureCount;
    private long criticalCount;
    private long nonCriticalCount;
    private long shieldPresentCount;
    private long shieldAbsentCount;
    private double maximumAbsoluteError;
    private double maximumRelativeError;
    private double absoluteErrorSum;
    private long numericValueCount;
    private final EnumMap<DamageType, Long> damageTypeCounts =
            new EnumMap<>(DamageType.class);
    private final EnumMap<DamageKind, Long> damageKindCounts =
            new EnumMap<>(DamageKind.class);
    private final EnumMap<DamageMode, Long> damageModeCounts =
            new EnumMap<>(DamageMode.class);
    private final EnumMap<DamageShadowTargetType, Long> targetTypeCounts =
            new EnumMap<>(DamageShadowTargetType.class);
    private final TreeMap<Integer, Long> enhancementLevelCounts =
            new TreeMap<>();
    private final ArrayList<DamageShadowMismatchDetail> mismatchDetails =
            new ArrayList<>();

    public DamageShadowValidationTracker() {
        this(DEFAULT_MISMATCH_DETAIL_LIMIT);
    }

    public DamageShadowValidationTracker(int mismatchDetailLimit) {
        if (mismatchDetailLimit < 0 || mismatchDetailLimit > 1_000) {
            throw new IllegalArgumentException(
                    "mismatch detail limit must be between 0 and 1000");
        }
        this.mismatchDetailLimit = mismatchDetailLimit;
        reset(Instant.EPOCH);
    }

    public synchronized void reset(Instant startedAt) {
        sessionStartedAt = Objects.requireNonNull(startedAt, "startedAt");
        comparisonCount = 0;
        matchCount = 0;
        mismatchCount = 0;
        legacyFailureCount = 0;
        shadowFailureCount = 0;
        criticalCount = 0;
        nonCriticalCount = 0;
        shieldPresentCount = 0;
        shieldAbsentCount = 0;
        maximumAbsoluteError = 0.0;
        maximumRelativeError = 0.0;
        absoluteErrorSum = 0.0;
        numericValueCount = 0;
        resetEnumMap(damageTypeCounts, DamageType.values());
        resetEnumMap(damageKindCounts, DamageKind.values());
        resetEnumMap(damageModeCounts, DamageMode.values());
        resetEnumMap(targetTypeCounts, DamageShadowTargetType.values());
        enhancementLevelCounts.clear();
        mismatchDetails.clear();
    }

    public synchronized void recordComparison(
            DamageShadowRuntimeContext context,
            DamageShadowComparison comparison
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(comparison, "comparison");
        DamageCalculationSnapshot snapshot = comparison.snapshot();
        DamageShadowNumericReport report =
                DamageShadowNumericReport.from(comparison);
        comparisonCount = increment(comparisonCount);
        if (comparison.matches()) {
            matchCount = increment(matchCount);
        } else {
            mismatchCount = increment(mismatchCount);
        }
        if (comparison.legacyResult().critical()) {
            criticalCount = increment(criticalCount);
        } else {
            nonCriticalCount = increment(nonCriticalCount);
        }
        if (snapshot.defenseSnapshot().shieldAmount() > 0.0) {
            shieldPresentCount = increment(shieldPresentCount);
        } else {
            shieldAbsentCount = increment(shieldAbsentCount);
        }
        increment(damageTypeCounts, snapshot.damageType());
        increment(damageKindCounts, snapshot.damageKind());
        increment(damageModeCounts, snapshot.mode());
        increment(targetTypeCounts, context.targetType());
        enhancementLevelCounts.merge(
                context.enhancementLevel(), 1L,
                DamageShadowValidationTracker::saturatedLongAdd);
        maximumAbsoluteError = Math.max(
                maximumAbsoluteError, report.maximumAbsoluteError());
        maximumRelativeError = Math.max(
                maximumRelativeError, report.maximumRelativeError());
        absoluteErrorSum = saturatedDoubleAdd(
                absoluteErrorSum, report.absoluteErrorSum());
        numericValueCount = saturatedLongAdd(
                numericValueCount, report.valueCount());

        if (!comparison.matches()
                && mismatchDetails.size() < mismatchDetailLimit) {
            mismatchDetails.add(new DamageShadowMismatchDetail(
                    context,
                    comparison.legacyResult().critical(),
                    snapshot,
                    snapshot.attackMetadata(),
                    comparison.legacyResult(),
                    comparison.shadowResult(),
                    report,
                    comparison.contextDifferences()));
        }
    }

    public synchronized void recordLegacyFailure() {
        legacyFailureCount = increment(legacyFailureCount);
    }

    public synchronized void recordShadowFailure() {
        shadowFailureCount = increment(shadowFailureCount);
    }

    public synchronized DamageShadowValidationSnapshot snapshot(
            boolean enabled
    ) {
        double average = numericValueCount == 0
                ? 0.0 : absoluteErrorSum / numericValueCount;
        if (!Double.isFinite(average)) {
            average = Double.MAX_VALUE;
        }
        return new DamageShadowValidationSnapshot(
                enabled,
                sessionStartedAt,
                comparisonCount,
                matchCount,
                mismatchCount,
                legacyFailureCount,
                shadowFailureCount,
                maximumAbsoluteError,
                maximumRelativeError,
                average,
                criticalCount,
                nonCriticalCount,
                shieldPresentCount,
                shieldAbsentCount,
                damageTypeCounts,
                damageKindCounts,
                damageModeCounts,
                targetTypeCounts,
                enhancementLevelCounts,
                mismatchDetails,
                mismatchDetailLimit);
    }

    private static <E extends Enum<E>> void resetEnumMap(
            EnumMap<E, Long> map,
            E[] values
    ) {
        map.clear();
        for (E value : values) {
            map.put(value, 0L);
        }
    }

    private static <E extends Enum<E>> void increment(
            EnumMap<E, Long> map,
            E key
    ) {
        map.merge(key, 1L,
                DamageShadowValidationTracker::saturatedLongAdd);
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1;
    }

    private static long saturatedLongAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static double saturatedDoubleAdd(double left, double right) {
        double result = left + right;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }
}
