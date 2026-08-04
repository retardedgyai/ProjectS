package io.github.gyai.projects.combat.damage;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class CombatDamageFoundationTest {
    private CombatDamageFoundationTest() {
    }

    public static void main(String[] args) {
        assert DamageType.PHYSICAL != DamageType.MAGICAL;
        assert DamageType.MAGICAL != DamageType.TRUE;
        assert DamageKind.NORMAL_ATTACK != DamageKind.DIRECT_SKILL;

        EnumSet<AttackTag> mutableTags = EnumSet.of(
                AttackTag.SKILL,
                AttackTag.PROJECTILE,
                AttackTag.MAGIC,
                AttackTag.FIRE);
        AttackMetadata metadata = new AttackMetadata(
                mutableTags,
                new ElementProfile(
                        Map.of(
                                DamageElement.FIRE, 40.0,
                                DamageElement.ICE, 15.0,
                                DamageElement.LIGHTNING, 5.0),
                        Map.of(
                                DamageElement.FIRE, .50,
                                DamageElement.ICE, .25)));
        mutableTags.clear();
        assert metadata.tags().size() == 4;
        assert metadata.hasTag(AttackTag.SKILL);
        assert metadata.hasTag(AttackTag.PROJECTILE);
        assert metadata.hasTag(AttackTag.MAGIC);
        assert metadata.hasTag(AttackTag.FIRE);

        assertClose(40.0, metadata.elements().value(DamageElement.FIRE));
        assertClose(15.0, metadata.elements().value(DamageElement.ICE));
        assertClose(5.0, metadata.elements().value(DamageElement.LIGHTNING));
        assertClose(.50, metadata.elements().scalingRate(DamageElement.FIRE));
        assertClose(.25, metadata.elements().scalingRate(DamageElement.ICE));
        assertClose(0.0, metadata.elements().scalingRate(DamageElement.LIGHTNING));

        expectUnsupported(() -> metadata.tags().add(AttackTag.SHATTER));
        expectUnsupported(() -> metadata.elements().values()
                .put(DamageElement.FIRE, 999.0));
        expectUnsupported(() -> metadata.elements().scalingRates()
                .put(DamageElement.ICE, 999.0));

        EnumMap<DamageElement, Double> mutableValues =
                new EnumMap<>(DamageElement.class);
        mutableValues.put(DamageElement.FIRE, 10.0);
        ElementProfile copied = new ElementProfile(mutableValues, Map.of());
        mutableValues.put(DamageElement.FIRE, 99.0);
        assertClose(10.0, copied.value(DamageElement.FIRE));

        expectIllegal(() -> new ElementProfile(
                Map.of(DamageElement.FIRE, Double.NaN), Map.of()));
        expectIllegal(() -> new ElementProfile(
                Map.of(DamageElement.ICE, Double.POSITIVE_INFINITY), Map.of()));
        expectIllegal(() -> new ElementProfile(
                Map.of(DamageElement.LIGHTNING, -1.0), Map.of()));
        expectIllegal(() -> new ElementProfile(
                Map.of(), Map.of(DamageElement.FIRE, Double.NEGATIVE_INFINITY)));
        expectIllegal(() -> new DamageOffenseSnapshot(
                Double.NaN, false, 1.5));

        AttackMetadata defaults = new AttackMetadata(null, null);
        assert defaults.tags().isEmpty();
        assert defaults.elements().equals(ElementProfile.EMPTY);
        for (DamageElement element : DamageElement.values()) {
            assertClose(0.0, defaults.elements().value(element));
            assertClose(0.0, defaults.elements().scalingRate(element));
        }

        DamageCalculator.Input physicalInput = input(
                DamageType.PHYSICAL, 100, 300, new double[0]);
        DamageCalculator.Input magicalInput = input(
                DamageType.MAGICAL, 100, 0, new double[0]);
        DamageCalculator.Input trueInput = input(
                DamageType.TRUE, 100, 1_000_000, new double[0]);
        assertClose(50.0,
                DamageCalculator.calculate(physicalInput).finalRoundedDamage());
        assertClose(100.0,
                DamageCalculator.calculate(magicalInput).finalRoundedDamage());
        DamageResult trueResult = DamageCalculator.calculate(trueInput);
        assertClose(100.0, trueResult.finalRoundedDamage());
        assertClose(0.0, trueResult.effectiveDefense());

        DamageResult first = DamageCalculator.calculate(physicalInput);
        DamageResult second = DamageCalculator.calculate(physicalInput);
        assert first.equals(second);

        double[] reductions = {.50};
        DamageCalculator.Input copiedInput = input(
                DamageType.PHYSICAL, 100, 0, reductions);
        reductions[0] = 0.0;
        double[] exposedCopy = copiedInput.damageReductions();
        exposedCopy[0] = 0.0;
        assertClose(50.0,
                DamageCalculator.calculate(copiedInput).finalRoundedDamage());

        DamageResult normalizedNegative = DamageCalculator.calculate(
                input(DamageType.PHYSICAL, -100, -300, new double[0]));
        assertClose(0.0, normalizedNegative.finalRoundedDamage());
        DamageResult normalizedNonFinite = DamageCalculator.calculate(
                new DamageCalculator.Input(
                        DamageType.PHYSICAL,
                        DamageMode.PVE,
                        DamageKind.DIRECT_SKILL,
                        Double.NaN,
                        Double.POSITIVE_INFINITY,
                        Double.NaN,
                        0, 0, false, 1.5,
                        Double.NaN, 0, 0, 0, 300,
                        new double[]{Double.NaN}, 1,
                        0, 100, 0, 0, 0));
        assert Double.isFinite(normalizedNonFinite.finalRoundedDamage());
        assert normalizedNonFinite.finalRoundedDamage() >= 0.0;

        assert Set.of(DamageElement.values()).equals(Set.of(
                DamageElement.FIRE,
                DamageElement.ICE,
                DamageElement.LIGHTNING));
    }

    private static DamageCalculator.Input input(
            DamageType type,
            double fixedDamage,
            double defense,
            double[] reductions
    ) {
        return new DamageCalculator.Input(
                type,
                DamageMode.PVE,
                DamageKind.DIRECT_SKILL,
                0,
                fixedDamage,
                0,
                0,
                0,
                false,
                1.5,
                defense,
                0,
                0,
                0,
                300,
                reductions,
                1,
                0,
                1_000,
                0,
                0,
                0);
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void assertClose(double expected, double actual) {
        if (!Double.isFinite(actual)
                || Math.abs(expected - actual) > 0.000_001) {
            throw new AssertionError(
                    "Expected " + expected + " but got " + actual);
        }
    }
}
