package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.combat.stat.StatCalculator;

import java.util.Map;
import java.util.Set;

public final class GenericDamageShadowComparatorTest {
    private static final DamageShadowExpectation SPIN =
            SpinSlashDamageShadow.EXPECTATION;

    private GenericDamageShadowComparatorTest() {
    }

    public static void main(String[] args) {
        expectationIsImmutable();
        starterCompatibilityDelegatesToGenericComparator();
        spinSlashExactContextMatches();
        everyContextMismatchIsReported();
        epsilonBoundaryIsStable();
        nonFiniteResultsMismatchWithoutThrowing();
        criticalSnapshotAndLifeStealAreCompared();
    }

    private static void expectationIsImmutable() {
        java.util.HashSet<AttackTag> tags = new java.util.HashSet<>(
                SPIN.exactTags());
        DamageShadowExpectation expectation = new DamageShadowExpectation(
                "copy-test", DamageType.PHYSICAL,
                DamageKind.DIRECT_SKILL, DamageMode.PVE,
                tags, ElementProfile.EMPTY);
        tags.add(AttackTag.FIRE);
        assert expectation.exactTags().equals(SPIN.exactTags());
        boolean immutable = false;
        try {
            expectation.exactTags().add(AttackTag.FIRE);
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        assert immutable;
    }

    private static void starterCompatibilityDelegatesToGenericComparator() {
        DamageCalculationSnapshot snapshot = DamageShadowTestFixtures.snapshot(
                true, 10);
        DamageResult legacy = snapshot.calculate();
        assert DamageShadowComparator.compareStarterSword(
                legacy, snapshot).equals(DamageShadowComparator.compare(
                legacy, snapshot,
                DamageShadowExpectation.STARTER_SWORD));
        assert DamageShadowComparator.compareStarterSword(
                legacy, snapshot.calculate(), snapshot).equals(
                DamageShadowComparator.compare(
                        legacy, snapshot.calculate(), snapshot,
                        DamageShadowExpectation.STARTER_SWORD));
    }

    private static void spinSlashExactContextMatches() {
        DamageCalculationSnapshot snapshot = spinSnapshot(
                DamageType.PHYSICAL, DamageKind.DIRECT_SKILL,
                DamageMode.PVE, SPIN.exactTags(), ElementProfile.EMPTY,
                true, 15, .33);
        assert DamageShadowComparator.compare(
                snapshot.calculate(), snapshot, SPIN).matches();
    }

    private static void everyContextMismatchIsReported() {
        assertContext("damageType", spinSnapshot(
                DamageType.MAGICAL, DamageKind.DIRECT_SKILL,
                DamageMode.PVE, SPIN.exactTags(), ElementProfile.EMPTY,
                false, 0, .33));
        assertContext("damageKind", spinSnapshot(
                DamageType.PHYSICAL, DamageKind.NORMAL_ATTACK,
                DamageMode.PVE, SPIN.exactTags(), ElementProfile.EMPTY,
                false, 0, .33));
        assertContext("damageMode", spinSnapshot(
                DamageType.PHYSICAL, DamageKind.DIRECT_SKILL,
                DamageMode.PVP, SPIN.exactTags(), ElementProfile.EMPTY,
                false, 0, .33));
        assertContext("attackTags", spinSnapshot(
                DamageType.PHYSICAL, DamageKind.DIRECT_SKILL,
                DamageMode.PVE, Set.of(AttackTag.SKILL, AttackTag.MELEE),
                ElementProfile.EMPTY, false, 0, .33));
        assertContext("attackTags", spinSnapshot(
                DamageType.PHYSICAL, DamageKind.DIRECT_SKILL,
                DamageMode.PVE, Set.of(AttackTag.SKILL, AttackTag.MELEE,
                        AttackTag.PHYSICAL, AttackTag.FIRE),
                ElementProfile.EMPTY, false, 0, .33));
        ElementProfile fire = new ElementProfile(
                Map.of(DamageElement.FIRE, 1.0), Map.of());
        assertContext("elements", spinSnapshot(
                DamageType.PHYSICAL, DamageKind.DIRECT_SKILL,
                DamageMode.PVE, SPIN.exactTags(), fire,
                false, 0, .33));
    }

    private static void epsilonBoundaryIsStable() {
        DamageCalculationSnapshot snapshot = spinSnapshot(
                DamageType.PHYSICAL, DamageKind.DIRECT_SKILL,
                DamageMode.PVE, SPIN.exactTags(), ElementProfile.EMPTY,
                false, 0, .33);
        DamageResult legacy = snapshot.calculate();
        double epsilon = .000001;
        DamageResult within = withFinal(legacy,
                legacy.finalRoundedDamage() + epsilon * .5);
        assert DamageShadowComparator.compare(
                legacy, within, snapshot, SPIN, epsilon).matches();
        DamageResult outside = withFinal(legacy,
                legacy.finalRoundedDamage() + epsilon * 200);
        assert !DamageShadowComparator.compare(
                legacy, outside, snapshot, SPIN, epsilon).matches();
    }

    private static void nonFiniteResultsMismatchWithoutThrowing() {
        DamageCalculationSnapshot snapshot = spinSnapshot(
                DamageType.PHYSICAL, DamageKind.DIRECT_SKILL,
                DamageMode.PVE, SPIN.exactTags(), ElementProfile.EMPTY,
                false, 0, .33);
        DamageResult legacy = snapshot.calculate();
        DamageShadowComparison nan = DamageShadowComparator.compare(
                legacy, withFinal(legacy, Double.NaN), snapshot, SPIN);
        assert !nan.matches();
        DamageShadowNumericReport nanReport =
                DamageShadowNumericReport.from(nan);
        assert nanReport.maximumAbsoluteError() == Double.MAX_VALUE;
        assert nanReport.maximumRelativeError() == Double.MAX_VALUE;
        DamageShadowComparison infinity = DamageShadowComparator.compare(
                legacy, withFinal(legacy, Double.POSITIVE_INFINITY),
                snapshot, SPIN);
        assert !infinity.matches();
        assert DamageShadowNumericReport.from(infinity)
                .maximumAbsoluteError() == Double.MAX_VALUE;
    }

    private static void criticalSnapshotAndLifeStealAreCompared() {
        DamageCalculationSnapshot snapshot = spinSnapshot(
                DamageType.PHYSICAL, DamageKind.DIRECT_SKILL,
                DamageMode.PVE, SPIN.exactTags(), ElementProfile.EMPTY,
                true, 25, .33);
        DamageResult legacy = snapshot.calculate();
        DamageCalculationSnapshot wrongCritical = spinSnapshot(
                DamageType.PHYSICAL, DamageKind.DIRECT_SKILL,
                DamageMode.PVE, SPIN.exactTags(), ElementProfile.EMPTY,
                false, 25, .33);
        DamageShadowComparison critical = DamageShadowComparator.compare(
                legacy, legacy, wrongCritical, SPIN);
        assert critical.contextDifferences().contains("criticalResult");
        DamageResult wrongLifeSteal = new DamageResult(
                legacy.resolvedAttackPower(), legacy.baseDamage(),
                legacy.damageIncreaseMultiplier(),
                legacy.offenseResolvedDamage(), legacy.critical(),
                legacy.criticalMultiplier(), legacy.defenseBeforePenetration(),
                legacy.effectiveDefense(), legacy.defenseMultiplier(),
                legacy.reductionMultiplier(), legacy.modeMultiplier(),
                legacy.damageBeforeShield(), legacy.shieldDamage(),
                legacy.healthDamage(), legacy.lifeStealHealing() + 1,
                legacy.finalRoundedDamage());
        DamageShadowComparison comparison = DamageShadowComparator.compare(
                legacy, wrongLifeSteal, snapshot, SPIN);
        assert comparison.numericDifferences().containsKey(
                "lifeStealHealing");
        assert DamageShadowNumericReport.from(comparison).deltas()
                .containsKey("lifeStealHealing");
    }

    private static void assertContext(
            String field,
            DamageCalculationSnapshot snapshot
    ) {
        assert DamageShadowComparator.compare(
                snapshot.calculate(), snapshot, SPIN)
                .contextDifferences().contains(field);
    }

    static DamageCalculationSnapshot spinSnapshot(
            DamageType type,
            DamageKind kind,
            DamageMode mode,
            Set<AttackTag> tags,
            ElementProfile elements,
            boolean critical,
            double shield,
            double lifeStealEfficiency
    ) {
        double criticalMultiplier = critical
                ? mode.baseCriticalMultiplier() : 1.0;
        double offense = 100 * criticalMultiplier;
        return new DamageCalculationSnapshot(
                type, mode, kind,
                new AttackMetadata(tags, elements),
                0, 100, 0, 0, critical, criticalMultiplier,
                new DamageOffenseSnapshot(
                        offense, critical, criticalMultiplier),
                new DamageDefenseSnapshot(
                        0, 0, 0, 0, 1, 0,
                        shield, 1_000, 1),
                0, 0, StatCalculator.DEFAULT_DEFENSE_CONSTANT,
                new double[0], 1, .10,
                lifeStealEfficiency, 0);
    }

    private static DamageResult withFinal(
            DamageResult source,
            double finalDamage
    ) {
        return DamageShadowTestFixtures.withFinalDamage(source, finalDamage);
    }
}
