package io.github.gyai.projects.combat.damage;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public final class DamageShadowRuntimeSafetyTest {
    private DamageShadowRuntimeSafetyTest() {
    }

    public static void main(String[] args) {
        disabledSkipsAllComparisonWork();
        sharesCriticalAndKeepsCalculationsIndependent();
        appliesEveryLegacySideEffectExactlyOnce();
        shadowFailureRemainsFailOpen();
    }

    private static void disabledSkipsAllComparisonWork() {
        DamageShadowValidationController controller = controller(false);
        DamageShadowComparisonObserver observer =
                new DamageShadowComparisonObserver(controller);
        AtomicInteger snapshots = new AtomicInteger();
        var result = observer.observeStarterSword(
                DamageShadowTestFixtures.context(
                        DamageShadowTargetType.TRAINING_DUMMY, 0),
                DamageShadowTestFixtures.snapshot(false, 0).calculate(),
                () -> {
                    snapshots.incrementAndGet();
                    return DamageShadowTestFixtures.snapshot(false, 0);
                },
                ignored -> {
                    throw new AssertionError("failure callback must not run");
                });
        assert result.isEmpty();
        assert snapshots.get() == 0;
        assert controller.snapshot().comparisonCount() == 0;
    }

    private static void sharesCriticalAndKeepsCalculationsIndependent() {
        DamageShadowValidationController controller = controller(true);
        DamageShadowComparisonObserver observer =
                new DamageShadowComparisonObserver(controller);
        AtomicInteger criticalRolls = new AtomicInteger();
        boolean criticalDecision = decideCriticalOnce(criticalRolls);
        DamageCalculationSnapshot legacyInput =
                DamageShadowTestFixtures.snapshot(criticalDecision, 0);
        DamageResult legacy = legacyInput.calculate();
        AtomicReference<DamageResult> shadow = new AtomicReference<>();
        DamageShadowComparison comparison = observer.observeStarterSword(
                DamageShadowTestFixtures.context(
                        DamageShadowTargetType.NORMAL_MONSTER, 5),
                legacy,
                () -> DamageShadowTestFixtures.snapshot(
                        legacy.critical(), 0),
                ignored -> {
                    throw new AssertionError("comparison must not fail");
                }).orElseThrow();
        shadow.set(comparison.shadowResult());
        assert criticalRolls.get() == 1;
        assert legacy.critical() == shadow.get().critical();
        assert legacy != shadow.get();
        assert legacy.equals(shadow.get());
        assert legacy.equals(legacyInput.calculate());
    }

    private static void appliesEveryLegacySideEffectExactlyOnce() {
        DamageResult legacy =
                DamageShadowTestFixtures.snapshot(true, 25).calculate();
        AtomicInteger applications = new AtomicInteger();
        AtomicInteger healthChanges = new AtomicInteger();
        AtomicInteger shieldConsumptions = new AtomicInteger();
        AtomicInteger lifeStealApplications = new AtomicInteger();
        AtomicReference<DamageResult> applied = new AtomicReference<>();

        DamageResult returned = DamageService.observeThenApply(
                legacy,
                observed -> DamageShadowComparator.compareStarterSword(
                        observed,
                        DamageShadowTestFixtures.snapshot(
                                observed.critical(), 25)),
                result -> {
                    applications.incrementAndGet();
                    healthChanges.incrementAndGet();
                    shieldConsumptions.incrementAndGet();
                    lifeStealApplications.incrementAndGet();
                    applied.set(result);
                    return result;
                });
        assert returned == legacy;
        assert applied.get() == legacy;
        assert applications.get() == 1;
        assert healthChanges.get() == 1;
        assert shieldConsumptions.get() == 1;
        assert lifeStealApplications.get() == 1;
    }

    private static void shadowFailureRemainsFailOpen() {
        DamageShadowValidationController controller = controller(true);
        DamageShadowComparisonObserver observer =
                new DamageShadowComparisonObserver(controller);
        DamageResult legacy =
                DamageShadowTestFixtures.snapshot(false, 0).calculate();
        AtomicInteger applications = new AtomicInteger();
        AtomicInteger failureCallbacks = new AtomicInteger();
        DamageResult returned = DamageService.observeThenApply(
                legacy,
                result -> {
                    assert observer.observeStarterSword(
                            DamageShadowTestFixtures.context(
                                    DamageShadowTargetType.OTHER, 0),
                            result,
                            () -> {
                                throw new IllegalStateException("shadow failure");
                            },
                            ignored -> failureCallbacks.incrementAndGet())
                            .isEmpty();
                },
                result -> {
                    applications.incrementAndGet();
                    return result;
                });
        assert returned == legacy;
        assert applications.get() == 1;
        assert failureCallbacks.get() == 1;
        assert controller.snapshot().shadowFailureCount() == 1;

        DamageResult stillReturned = DamageService.observeThenApply(
                legacy,
                ignored -> {
                    throw new IllegalArgumentException("unexpected observer failure");
                },
                result -> result);
        assert stillReturned == legacy;
    }

    private static boolean decideCriticalOnce(AtomicInteger rolls) {
        rolls.incrementAndGet();
        return true;
    }

    private static DamageShadowValidationController controller(
            boolean enabled
    ) {
        return new DamageShadowValidationController(
                enabled,
                new DamageShadowValidationTracker(5),
                new DamageShadowValidationExporter(),
                Path.of("unused-test-export"),
                Clock.fixed(
                        Instant.parse("2026-08-04T00:00:00Z"),
                        ZoneOffset.UTC),
                Logger.getAnonymousLogger());
    }
}
