package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.combat.stat.StatCalculator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DamageShadowComparator {
    public static final double DEFAULT_EPSILON = 0.000_001;
    private DamageShadowComparator() {
    }

    public static DamageShadowComparison compareStarterSword(
            DamageResult legacy,
            DamageCalculationSnapshot snapshot
    ) {
        return compareStarterSword(legacy, snapshot, DEFAULT_EPSILON);
    }

    public static DamageShadowComparison compareStarterSword(
            DamageResult legacy,
            DamageCalculationSnapshot snapshot,
            double epsilon
    ) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        return compare(
                legacy, snapshot.calculate(), snapshot,
                DamageShadowExpectation.STARTER_SWORD, epsilon);
    }

    public static DamageShadowComparison compareStarterSword(
            DamageResult legacy,
            DamageResult shadow,
            DamageCalculationSnapshot snapshot
    ) {
        return compareStarterSword(
                legacy, shadow, snapshot, DEFAULT_EPSILON);
    }

    public static DamageShadowComparison compareStarterSword(
            DamageResult legacy,
            DamageResult shadow,
            DamageCalculationSnapshot snapshot,
            double epsilon
    ) {
        return compare(
                legacy, shadow, snapshot,
                DamageShadowExpectation.STARTER_SWORD, epsilon);
    }

    public static DamageShadowComparison compare(
            DamageResult legacy,
            DamageCalculationSnapshot snapshot,
            DamageShadowExpectation expectation
    ) {
        return compare(legacy, snapshot, expectation, DEFAULT_EPSILON);
    }

    public static DamageShadowComparison compare(
            DamageResult legacy,
            DamageCalculationSnapshot snapshot,
            DamageShadowExpectation expectation,
            double epsilon
    ) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        return compare(
                legacy, snapshot.calculate(), snapshot, expectation, epsilon);
    }

    public static DamageShadowComparison compare(
            DamageResult legacy,
            DamageResult shadow,
            DamageCalculationSnapshot snapshot,
            DamageShadowExpectation expectation
    ) {
        return compare(
                legacy, shadow, snapshot, expectation, DEFAULT_EPSILON);
    }

    public static DamageShadowComparison compare(
            DamageResult legacy,
            DamageResult shadow,
            DamageCalculationSnapshot snapshot,
            DamageShadowExpectation expectation,
            double epsilon
    ) {
        if (legacy == null || shadow == null || snapshot == null
                || expectation == null) {
            throw new IllegalArgumentException(
                    "legacy, shadow, snapshot, and expectation must not be null");
        }
        if (!Double.isFinite(epsilon) || epsilon <= 0.0) {
            throw new IllegalArgumentException("epsilon must be positive and finite");
        }
        LinkedHashMap<String, Double> numeric = new LinkedHashMap<>();
        ArrayList<String> context = new ArrayList<>();

        compare(numeric, "preCriticalOffenseDamage",
                preCriticalDamage(legacy),
                snapshot.preCriticalOffenseDamage(), epsilon);
        if (legacy.critical() != shadow.critical()
                || legacy.critical() != snapshot.critical()) {
            context.add("criticalResult");
        }
        compare(numeric, "criticalMultiplier",
                legacy.criticalMultiplier(), shadow.criticalMultiplier(), epsilon);
        compare(numeric, "offenseResolvedDamage",
                legacy.offenseResolvedDamage(),
                shadow.offenseResolvedDamage(), epsilon);
        compare(numeric, "defenseBeforePenetration",
                legacy.defenseBeforePenetration(),
                shadow.defenseBeforePenetration(), epsilon);
        compare(numeric, "effectiveDefense",
                legacy.effectiveDefense(), shadow.effectiveDefense(), epsilon);
        double penetrationCheck = snapshot.damageType() == DamageType.TRUE
                ? 0.0
                : StatCalculator.effectiveDefense(
                legacy.defenseBeforePenetration(),
                snapshot.defenseSnapshot().defenseReductionFor(
                        snapshot.damageType()),
                snapshot.penetrationPercent(),
                snapshot.flatPenetration());
        compare(numeric, "penetrationInputs",
                legacy.effectiveDefense(), penetrationCheck, epsilon);
        compare(numeric, "damageBeforeShield",
                legacy.damageBeforeShield(), shadow.damageBeforeShield(), epsilon);
        compare(numeric, "shieldDamage",
                legacy.shieldDamage(), shadow.shieldDamage(), epsilon);
        compare(numeric, "healthDamage",
                legacy.healthDamage(), shadow.healthDamage(), epsilon);
        compare(numeric, "lifeStealHealing",
                legacy.lifeStealHealing(), shadow.lifeStealHealing(), epsilon);
        compare(numeric, "finalRoundedDamage",
                legacy.finalRoundedDamage(), shadow.finalRoundedDamage(), epsilon);

        if (snapshot.damageType() != expectation.damageType()) {
            context.add("damageType");
        }
        if (snapshot.damageKind() != expectation.damageKind()) {
            context.add("damageKind");
        }
        if (snapshot.mode() != expectation.damageMode()) {
            context.add("damageMode");
        }
        if (!snapshot.attackMetadata().tags().equals(expectation.exactTags())) {
            context.add("attackTags");
        }
        if (!snapshot.attackMetadata().elements().equals(expectation.elements())) {
            context.add("elements");
        }
        return new DamageShadowComparison(
                legacy, shadow, snapshot, numeric, context);
    }

    private static double preCriticalDamage(DamageResult result) {
        double multiplier = result.critical()
                ? Math.max(1.0, result.criticalMultiplier())
                : 1.0;
        return result.offenseResolvedDamage() / multiplier;
    }

    private static void compare(
            Map<String, Double> differences,
            String field,
            double legacy,
            double shadow,
            double epsilon
    ) {
        double difference = Math.abs(legacy - shadow);
        double scale = Math.max(1.0, Math.max(
                Math.abs(legacy), Math.abs(shadow)));
        if (!Double.isFinite(difference) || difference > epsilon * scale) {
            differences.put(field, difference);
        }
    }
}
