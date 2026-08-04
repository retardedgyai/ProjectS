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
        if (!controller.enabled()) {
            return Optional.empty();
        }
        try {
            DamageCalculationSnapshot snapshot = snapshotSupplier.get();
            DamageShadowComparison comparison =
                    DamageShadowComparator.compareStarterSword(
                            legacyResult, snapshot);
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
}
