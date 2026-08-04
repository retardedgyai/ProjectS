package io.github.gyai.projects.combat.damage;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Controls whether an observational shadow calculation is evaluated. */
public final class DamageShadowEvaluator {
    private final boolean enabled;
    private final AtomicLong evaluationCount = new AtomicLong();

    public DamageShadowEvaluator(boolean enabled) {
        this.enabled = enabled;
    }

    public Optional<DamageShadowComparison> evaluateStarterSword(
            DamageResult legacy,
            Supplier<DamageCalculationSnapshot> snapshotSupplier
    ) {
        if (!enabled) {
            return Optional.empty();
        }
        if (snapshotSupplier == null) {
            throw new IllegalArgumentException("snapshotSupplier must not be null");
        }
        evaluationCount.incrementAndGet();
        return Optional.of(DamageShadowComparator.compareStarterSword(
                legacy, snapshotSupplier.get()));
    }

    public boolean enabled() {
        return enabled;
    }

    public long evaluationCount() {
        return evaluationCount.get();
    }
}
