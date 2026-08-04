package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.combat.stat.StatCalculator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class DamageShadowComparator {
    public static final double DEFAULT_EPSILON = 0.000_001;
    private static final Set<AttackTag> STARTER_SWORD_TAGS = Set.of(
            AttackTag.NORMAL_ATTACK,
            AttackTag.MELEE,
            AttackTag.PHYSICAL);

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
        return compareStarterSword(
                legacy, snapshot.calculate(), snapshot, epsilon);
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
        if (legacy == null || shadow == null || snapshot == null) {
            throw new IllegalArgumentException(
                    "legacy, shadow, and snapshot must not be null");
        }
        if (!Double.isFinite(epsilon) || epsilon <= 0.0) {
            throw new IllegalArgumentException("epsilon must be positive and finite");
        }
        LinkedHashMap<String, Double> numeric = new LinkedHashMap<>();
        ArrayList<String> context = new ArrayList<>();

        compare(numeric, "preCriticalOffenseDamage",
                preCriticalDamage(legacy),
                snapshot.preCriticalOffenseDamage(), epsilon);
        if (legacy.critical() != shadow.critical()) {
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
        compare(numeric, "finalRoundedDamage",
                legacy.finalRoundedDamage(), shadow.finalRoundedDamage(), epsilon);

        if (snapshot.damageType() != DamageType.PHYSICAL) {
            context.add("damageType");
        }
        if (snapshot.damageKind() != DamageKind.NORMAL_ATTACK) {
            context.add("damageKind");
        }
        if (snapshot.mode() != DamageMode.PVE) {
            context.add("damageMode");
        }
        if (!snapshot.attackMetadata().tags().containsAll(STARTER_SWORD_TAGS)) {
            context.add("attackTags");
        }
        if (!snapshot.attackMetadata().elements().equals(ElementProfile.EMPTY)) {
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
