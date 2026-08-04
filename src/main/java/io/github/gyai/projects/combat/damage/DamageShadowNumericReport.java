package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.combat.stat.StatCalculator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Finite error metrics for every numeric field compared by the shadow path. */
public record DamageShadowNumericReport(
        Map<String, NumericDelta> deltas,
        double maximumAbsoluteError,
        double maximumRelativeError,
        double absoluteErrorSum,
        long valueCount
) {
    public DamageShadowNumericReport {
        deltas = Collections.unmodifiableMap(new LinkedHashMap<>(deltas));
        maximumAbsoluteError = finiteNonNegative(maximumAbsoluteError);
        maximumRelativeError = finiteNonNegative(maximumRelativeError);
        absoluteErrorSum = finiteNonNegative(absoluteErrorSum);
        valueCount = Math.max(0L, valueCount);
    }

    public static DamageShadowNumericReport from(
            DamageShadowComparison comparison
    ) {
        DamageResult legacy = comparison.legacyResult();
        DamageResult shadow = comparison.shadowResult();
        DamageCalculationSnapshot snapshot = comparison.snapshot();
        Builder builder = new Builder();
        builder.add("preCriticalOffenseDamage",
                preCriticalDamage(legacy),
                snapshot.preCriticalOffenseDamage());
        builder.add("criticalMultiplier",
                legacy.criticalMultiplier(), shadow.criticalMultiplier());
        builder.add("offenseResolvedDamage",
                legacy.offenseResolvedDamage(), shadow.offenseResolvedDamage());
        builder.add("defenseBeforePenetration",
                legacy.defenseBeforePenetration(),
                shadow.defenseBeforePenetration());
        builder.add("effectiveDefense",
                legacy.effectiveDefense(), shadow.effectiveDefense());
        double penetrationCheck = snapshot.damageType() == DamageType.TRUE
                ? 0.0
                : StatCalculator.effectiveDefense(
                legacy.defenseBeforePenetration(),
                snapshot.defenseSnapshot().defenseReductionFor(
                        snapshot.damageType()),
                snapshot.penetrationPercent(),
                snapshot.flatPenetration());
        builder.add("penetrationInputs",
                legacy.effectiveDefense(), penetrationCheck);
        builder.add("damageBeforeShield",
                legacy.damageBeforeShield(), shadow.damageBeforeShield());
        builder.add("shieldDamage",
                legacy.shieldDamage(), shadow.shieldDamage());
        builder.add("healthDamage",
                legacy.healthDamage(), shadow.healthDamage());
        builder.add("finalRoundedDamage",
                legacy.finalRoundedDamage(), shadow.finalRoundedDamage());
        return builder.build();
    }

    public double averageAbsoluteError() {
        return valueCount == 0 ? 0.0 : absoluteErrorSum / valueCount;
    }

    private static double preCriticalDamage(DamageResult result) {
        double multiplier = result.critical()
                ? Math.max(1.0, result.criticalMultiplier()) : 1.0;
        return result.offenseResolvedDamage() / multiplier;
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0 ? value : 0.0;
    }

    public record NumericDelta(
            double legacyValue,
            double shadowValue,
            double absoluteError,
            double relativeError
    ) {
        public NumericDelta {
            if (!Double.isFinite(legacyValue)
                    || !Double.isFinite(shadowValue)
                    || !Double.isFinite(absoluteError)
                    || absoluteError < 0.0
                    || !Double.isFinite(relativeError)
                    || relativeError < 0.0) {
                throw new IllegalArgumentException(
                        "numeric delta values must be finite and non-negative");
            }
        }
    }

    private static final class Builder {
        private final LinkedHashMap<String, NumericDelta> values =
                new LinkedHashMap<>();
        private double maximumAbsolute;
        private double maximumRelative;
        private double sum;

        private void add(String name, double legacy, double shadow) {
            if (!Double.isFinite(legacy) || !Double.isFinite(shadow)) {
                return;
            }
            double absolute = Math.abs(legacy - shadow);
            if (!Double.isFinite(absolute)) {
                absolute = Double.MAX_VALUE;
            }
            double denominator = Math.max(Math.abs(legacy), Math.abs(shadow));
            double relative = denominator == 0.0 ? 0.0 : absolute / denominator;
            if (!Double.isFinite(relative)) {
                relative = Double.MAX_VALUE;
            }
            values.put(name, new NumericDelta(
                    legacy, shadow, absolute, relative));
            maximumAbsolute = Math.max(maximumAbsolute, absolute);
            maximumRelative = Math.max(maximumRelative, relative);
            sum = saturatedAdd(sum, absolute);
        }

        private DamageShadowNumericReport build() {
            return new DamageShadowNumericReport(
                    values, maximumAbsolute, maximumRelative,
                    sum, values.size());
        }

        private static double saturatedAdd(double left, double right) {
            double result = left + right;
            return Double.isFinite(result) ? result : Double.MAX_VALUE;
        }
    }
}
