package io.github.gyai.projects.combat.damage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class DamageShadowValidationTrackerTest {
    private DamageShadowValidationTrackerTest() {
    }

    public static void main(String[] args) throws InterruptedException {
        recordsMetricsAndBoundsDetails();
        ignoresNonFiniteMetrics();
        remainsConsistentUnderConcurrentAccess();
    }

    private static void recordsMetricsAndBoundsDetails() {
        DamageShadowValidationTracker tracker =
                new DamageShadowValidationTracker(2);
        Instant started = Instant.parse("2026-08-04T01:00:00Z");
        tracker.reset(started);
        tracker.recordComparison(
                DamageShadowTestFixtures.context(
                        DamageShadowTargetType.TRAINING_DUMMY, 0),
                DamageShadowTestFixtures.comparison(false, 0, 0));
        tracker.recordComparison(
                DamageShadowTestFixtures.context(
                        DamageShadowTargetType.ELITE, 5),
                DamageShadowTestFixtures.comparison(true, 20, 2));
        tracker.recordComparison(
                DamageShadowTestFixtures.context(
                        DamageShadowTargetType.BOSS, 30),
                DamageShadowTestFixtures.comparison(false, 0, 3));
        tracker.recordComparison(
                DamageShadowTestFixtures.context(
                        DamageShadowTargetType.OTHER, 15),
                DamageShadowTestFixtures.comparison(false, 0, 4));
        tracker.recordLegacyFailure();
        tracker.recordShadowFailure();

        DamageShadowValidationSnapshot result = tracker.snapshot(true);
        assert result.enabled();
        assert result.sessionStartedAt().equals(started);
        assert result.comparisonCount() == 4;
        assert result.matchCount() == 1;
        assert result.mismatchCount() == 3;
        assert result.legacyFailureCount() == 1;
        assert result.shadowFailureCount() == 1;
        assertClose(4, result.maximumAbsoluteError());
        assertClose(9.0 / 40.0, result.averageAbsoluteError());
        assert result.criticalCount() == 1;
        assert result.nonCriticalCount() == 3;
        assert result.shieldPresentCount() == 1;
        assert result.shieldAbsentCount() == 3;
        assert result.damageTypeCounts().get(DamageType.PHYSICAL) == 4;
        assert result.damageKindCounts().get(DamageKind.NORMAL_ATTACK) == 4;
        assert result.damageModeCounts().get(DamageMode.PVE) == 4;
        assert result.targetTypeCounts().get(
                DamageShadowTargetType.TRAINING_DUMMY) == 1;
        assert result.enhancementLevelCounts().get(30) == 1;
        assert result.mismatchDetails().size() == 2;
        assert result.mismatchDetailLimit() == 2;

        tracker.reset(Instant.parse("2026-08-04T02:00:00Z"));
        DamageShadowValidationSnapshot reset = tracker.snapshot(false);
        assert reset.comparisonCount() == 0;
        assert reset.mismatchDetails().isEmpty();
        assert reset.enhancementLevelCounts().isEmpty();
    }

    private static void ignoresNonFiniteMetrics() {
        DamageShadowValidationTracker tracker =
                new DamageShadowValidationTracker(1);
        DamageShadowComparison base =
                DamageShadowTestFixtures.comparison(false, 0, 1);
        DamageResult nonFinite = DamageShadowTestFixtures.withFinalDamage(
                base.shadowResult(), Double.NaN);
        tracker.recordComparison(
                DamageShadowTestFixtures.context(
                        DamageShadowTargetType.OTHER, 0),
                new DamageShadowComparison(
                        base.legacyResult(), nonFinite, base.snapshot(),
                        java.util.Map.of("finalRoundedDamage", Double.MAX_VALUE),
                        java.util.List.of()));
        DamageShadowValidationSnapshot result = tracker.snapshot(true);
        assert Double.isFinite(result.maximumAbsoluteError());
        assert Double.isFinite(result.maximumRelativeError());
        assert Double.isFinite(result.averageAbsoluteError());
    }

    private static void remainsConsistentUnderConcurrentAccess()
            throws InterruptedException {
        DamageShadowValidationTracker tracker =
                new DamageShadowValidationTracker(5);
        tracker.reset(Instant.now());
        int threads = 8;
        int perThread = 200;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> failures = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                    for (int hit = 0; hit < perThread; hit++) {
                        tracker.recordComparison(
                                DamageShadowTestFixtures.context(
                                        DamageShadowTargetType.NORMAL_MONSTER, 5),
                                DamageShadowTestFixtures.comparison(
                                        false, 0, 0));
                    }
                } catch (Throwable throwable) {
                    synchronized (failures) {
                        failures.add(throwable);
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        assert failures.isEmpty() : failures;
        DamageShadowValidationSnapshot result = tracker.snapshot(true);
        assert result.comparisonCount() == (long) threads * perThread;
        assert result.matchCount() == result.comparisonCount();
        assert result.mismatchCount() == 0;
    }

    private static void assertClose(double expected, double actual) {
        if (!Double.isFinite(actual)
                || Math.abs(expected - actual) > .000_001) {
            throw new AssertionError(
                    "Expected " + expected + " but got " + actual);
        }
    }
}
