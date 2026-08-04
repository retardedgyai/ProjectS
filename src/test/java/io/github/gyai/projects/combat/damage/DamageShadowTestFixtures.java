package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.combat.stat.StatCalculator;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class DamageShadowTestFixtures {
    private DamageShadowTestFixtures() {
    }

    static DamageCalculationSnapshot snapshot(
            boolean critical,
            double shield
    ) {
        double criticalMultiplier = critical ? 1.75 : 1.0;
        double offense = 100 * criticalMultiplier;
        return new DamageCalculationSnapshot(
                DamageType.PHYSICAL,
                DamageMode.PVE,
                DamageKind.NORMAL_ATTACK,
                new AttackMetadata(Set.of(
                        AttackTag.NORMAL_ATTACK,
                        AttackTag.MELEE,
                        AttackTag.PHYSICAL), ElementProfile.EMPTY),
                0,
                100,
                0,
                0,
                critical,
                criticalMultiplier,
                new DamageOffenseSnapshot(
                        offense, critical, criticalMultiplier),
                new DamageDefenseSnapshot(
                        0, 0, 0, 0,
                        1, 0, shield, 1_000, 1),
                0,
                0,
                StatCalculator.DEFAULT_DEFENSE_CONSTANT,
                new double[0],
                1,
                0,
                1,
                0);
    }

    static DamageShadowComparison comparison(
            boolean critical,
            double shield,
            double finalDamageOffset
    ) {
        DamageCalculationSnapshot snapshot = snapshot(critical, shield);
        DamageResult legacy = snapshot.calculate();
        DamageResult shadow = finalDamageOffset == 0.0
                ? snapshot.calculate()
                : withFinalDamage(
                        snapshot.calculate(),
                        snapshot.calculate().finalRoundedDamage()
                                + finalDamageOffset);
        return new DamageShadowComparison(
                legacy,
                shadow,
                snapshot,
                finalDamageOffset == 0.0
                        ? Map.of()
                        : Map.of("finalRoundedDamage",
                        Math.abs(finalDamageOffset)),
                java.util.List.of());
    }

    static DamageShadowRuntimeContext context(
            DamageShadowTargetType type,
            int enhancement
    ) {
        return new DamageShadowRuntimeContext(
                Instant.parse("2026-08-04T00:00:00Z"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                type,
                StarterSwordDamageShadow.ITEM_ID,
                enhancement);
    }

    static DamageResult withFinalDamage(
            DamageResult source,
            double finalDamage
    ) {
        return new DamageResult(
                source.resolvedAttackPower(),
                source.baseDamage(),
                source.damageIncreaseMultiplier(),
                source.offenseResolvedDamage(),
                source.critical(),
                source.criticalMultiplier(),
                source.defenseBeforePenetration(),
                source.effectiveDefense(),
                source.defenseMultiplier(),
                source.reductionMultiplier(),
                source.modeMultiplier(),
                source.damageBeforeShield(),
                source.shieldDamage(),
                source.healthDamage(),
                source.lifeStealHealing(),
                finalDamage);
    }
}
