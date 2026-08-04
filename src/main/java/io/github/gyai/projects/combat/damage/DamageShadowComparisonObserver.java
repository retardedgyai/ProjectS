package io.github.gyai.projects.combat.damage;

import java.util.Optional;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Pure fail-open observer: it records validation but never applies damage. */
public final class DamageShadowComparisonObserver {
    private final DamageShadowValidationController controller;

    public DamageShadowComparisonObserver(
            DamageShadowValidationController controller
    ) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    public Optional<DamageShadowComparison> observeStarterSword(
            DamageShadowRuntimeContext context,
            DamageResult legacyResult,
            Supplier<DamageCalculationSnapshot> snapshotSupplier,
            Consumer<RuntimeException> failureObserver
    ) {
        return observe(
                context, legacyResult, snapshotSupplier,
                DamageShadowExpectation.STARTER_SWORD, failureObserver);
    }

    public Optional<DamageShadowComparison> observe(
            DamageShadowRuntimeContext context,
            DamageResult legacyResult,
            Supplier<DamageCalculationSnapshot> snapshotSupplier,
            DamageShadowExpectation expectation,
            Consumer<RuntimeException> failureObserver
    ) {
        if (!controller.enabled()) {
            return Optional.empty();
        }
        try {
            DamageCalculationSnapshot snapshot = snapshotSupplier.get();
            DamageShadowComparison comparison =
                    DamageShadowComparator.compare(
                            legacyResult, snapshot, expectation,
                            DamageShadowComparator.DEFAULT_EPSILON);
            controller.recordComparison(context, comparison);
            return Optional.of(comparison);
        } catch (RuntimeException exception) {
            controller.recordShadowFailure();
            if (failureObserver != null) {
                try {
                    failureObserver.accept(exception);
                } catch (RuntimeException ignored) {
                    // Reporting must remain fail-open.
                }
            }
            return Optional.empty();
        }
    }

    public Optional<DamageShadowComparison> observePrecalculatedStarterSword(
            DamageShadowRuntimeContext context,
            DamageResult legacyResult,
            DamageResult shadowResult,
            DamageCalculationSnapshot snapshot,
            Consumer<RuntimeException> failureObserver
    ) {
        return observePrecalculated(
                context, legacyResult, shadowResult, snapshot,
                DamageShadowExpectation.STARTER_SWORD, failureObserver);
    }

    public Optional<DamageShadowComparison> observePrecalculated(
            DamageShadowRuntimeContext context,
            DamageResult legacyResult,
            DamageResult shadowResult,
            DamageCalculationSnapshot snapshot,
            DamageShadowExpectation expectation,
            Consumer<RuntimeException> failureObserver
    ) {
        if (!controller.enabled()) {
            return Optional.empty();
        }
        try {
            DamageShadowComparison comparison =
                    DamageShadowComparator.compare(
                            legacyResult, shadowResult, snapshot,
                            expectation, DamageShadowComparator.DEFAULT_EPSILON);
            controller.recordComparison(context, comparison);
            return Optional.of(comparison);
        } catch (RuntimeException exception) {
            controller.recordShadowFailure();
            notifyFailure(failureObserver, exception);
            return Optional.empty();
        }
    }

    private static void notifyFailure(
            Consumer<RuntimeException> failureObserver,
            RuntimeException exception
    ) {
        if (failureObserver == null) {
            return;
        }
        try {
            failureObserver.accept(exception);
        } catch (RuntimeException ignored) {
            // Reporting must remain fail-open.
        }
    }
}
