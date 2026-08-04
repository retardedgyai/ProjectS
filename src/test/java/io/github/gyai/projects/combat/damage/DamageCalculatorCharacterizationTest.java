package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.combat.stat.StatCalculator;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Locks current behavior; these values are not approval of the final design. */
public final class DamageCalculatorCharacterizationTest {
    private DamageCalculatorCharacterizationTest() {
    }

    public static void main(String[] args) {
        characterizeDamageKindsAndTypes();
        characterizeOffenseLayers();
        characterizeDefenseLayers();
        characterizeCriticals();
        characterizeShieldAndLifeSteal();
        characterizeSafetyAndDeterminism();
        characterizeReentryTracking();
    }

    private static void characterizeDamageKindsAndTypes() {
        DamageResult physicalNormal = DamageCalculator.calculate(input(
                DamageType.PHYSICAL,
                DamageMode.PVE,
                DamageKind.NORMAL_ATTACK,
                100, 0, 1,
                .30, false, 1.75,
                300, 0, 0, 0,
                new double[0], 1,
                0, 1_000, 0, 1));
        assertClose(100, physicalNormal.baseDamage());
        assertClose(130, physicalNormal.offenseResolvedDamage());
        assertClose(65, physicalNormal.finalRoundedDamage());

        DamageResult magicalSkill = DamageCalculator.calculate(input(
                DamageType.MAGICAL,
                DamageMode.PVE,
                DamageKind.DIRECT_SKILL,
                80, 20, 1.5,
                .60, false, 1.75,
                150, 0, 0, 0,
                new double[0], 1,
                0, 1_000, 0, 1));
        assertClose(140, magicalSkill.baseDamage());
        assertClose(224, magicalSkill.offenseResolvedDamage());
        assertClose(149.333, magicalSkill.finalRoundedDamage());

        DamageResult trueDamage = DamageCalculator.calculate(input(
                DamageType.TRUE,
                DamageMode.PVE,
                DamageKind.DIRECT_SKILL,
                0, 100, 0,
                0, false, 1.75,
                1_000_000, 1, 1, 1_000_000,
                new double[0], 1,
                0, 1_000, 0, 1));
        assertClose(0, trueDamage.defenseBeforePenetration());
        assertClose(0, trueDamage.effectiveDefense());
        assertClose(1, trueDamage.defenseMultiplier());
        assertClose(100, trueDamage.finalRoundedDamage());

        assert DamageKind.NORMAL_ATTACK.criticalAllowed();
        assert DamageKind.DIRECT_SKILL.criticalAllowed();
        assert !DamageKind.DAMAGE_OVER_TIME.criticalAllowed();
        assert !DamageKind.REFLECTED.criticalAllowed();
        assert !DamageKind.PERCENT_HEALTH.criticalAllowed();
    }

    private static void characterizeOffenseLayers() {
        // DamageService currently adds general + typed + normal/skill bonuses.
        double normalIncrease = .10 + .20 + .30;
        double skillIncrease = .10 + .20 + .30;
        DamageResult normal = DamageCalculator.calculate(input(
                DamageType.PHYSICAL, DamageMode.PVE,
                DamageKind.NORMAL_ATTACK,
                100, 10, 1.5,
                normalIncrease, false, 1.75,
                0, 0, 0, 0,
                new double[0], 1,
                0, 1_000, 0, 1));
        DamageResult skill = DamageCalculator.calculate(input(
                DamageType.MAGICAL, DamageMode.PVE,
                DamageKind.DIRECT_SKILL,
                100, 10, 1.5,
                skillIncrease, false, 1.75,
                0, 0, 0, 0,
                new double[0], 1,
                0, 1_000, 0, 1));
        assertClose(160, normal.baseDamage());
        assertClose(256, normal.offenseResolvedDamage());
        assertClose(256, normal.finalRoundedDamage());
        assertClose(normal.finalRoundedDamage(), skill.finalRoundedDamage());

        DamageResult modeScaled = DamageCalculator.calculate(input(
                DamageType.PHYSICAL, DamageMode.PVE,
                DamageKind.DIRECT_SKILL,
                0, 100, 0,
                0, false, 1.75,
                0, 0, 0, 0,
                new double[0], 1.25,
                0, 1_000, 0, 1));
        assertClose(125, modeScaled.offenseResolvedDamage());
        assertClose(1.25, modeScaled.modeMultiplier());
    }

    private static void characterizeDefenseLayers() {
        DamageResult penetrated = DamageCalculator.calculate(input(
                DamageType.PHYSICAL, DamageMode.PVE,
                DamageKind.DIRECT_SKILL,
                0, 100, 0,
                0, false, 1.75,
                600, .20, .25, 60,
                new double[0], 1,
                0, 1_000, 0, 1));
        assertClose(600, penetrated.defenseBeforePenetration());
        assertClose(300, penetrated.effectiveDefense());
        assertClose(.5, penetrated.defenseMultiplier());
        assertClose(50, penetrated.finalRoundedDamage());

        DamageResult incomingAndReduced = DamageCalculator.calculate(
                new DamageCalculator.Input(
                        DamageType.MAGICAL,
                        DamageMode.PVE,
                        DamageKind.DIRECT_SKILL,
                        0, 100, 0,
                        0, .25,
                        false, 1.75,
                        0, 0, 0, 0,
                        StatCalculator.DEFAULT_DEFENSE_CONSTANT,
                        new double[]{.20, .50},
                        1,
                        0, 1_000,
                        0, 1, 0));
        assertClose(1.25, incomingAndReduced.damageIncreaseMultiplier());
        assertClose(.40, incomingAndReduced.reductionMultiplier());
        assertClose(50, incomingAndReduced.finalRoundedDamage());

        DamageResult pveReductionCap = DamageCalculator.calculate(input(
                DamageType.PHYSICAL, DamageMode.PVE,
                DamageKind.DIRECT_SKILL,
                0, 100, 0,
                0, false, 1.75,
                0, 0, 0, 0,
                new double[]{.99}, 1,
                0, 1_000, 0, 1));
        DamageResult pvpReductionCap = DamageCalculator.calculate(input(
                DamageType.PHYSICAL, DamageMode.PVP,
                DamageKind.DIRECT_SKILL,
                0, 100, 0,
                0, false, 1.50,
                0, 0, 0, 0,
                new double[]{.99}, 1,
                0, 1_000, 0, 1));
        assertClose(.20, pveReductionCap.reductionMultiplier());
        assertClose(.25, pvpReductionCap.reductionMultiplier());
    }

    private static void characterizeCriticals() {
        // Design says 150%, but current PvE gameplay is intentionally locked at 175%.
        assertClose(1.75, DamageMode.PVE.baseCriticalMultiplier());
        assertClose(1.50, DamageMode.PVP.baseCriticalMultiplier());
        DamageResult currentPveCritical = DamageCalculator.calculate(input(
                DamageType.PHYSICAL, DamageMode.PVE,
                DamageKind.NORMAL_ATTACK,
                0, 100, 0,
                0, true, DamageMode.PVE.baseCriticalMultiplier(),
                0, 0, 0, 0,
                new double[0], 1,
                0, 1_000, 0, 1));
        assert currentPveCritical.critical();
        assertClose(1.75, currentPveCritical.criticalMultiplier());
        assertClose(175, currentPveCritical.finalRoundedDamage());

        DamageResult criticalBonus = DamageCalculator.calculate(input(
                DamageType.MAGICAL, DamageMode.PVE,
                DamageKind.DIRECT_SKILL,
                0, 100, 0,
                0, true, 2.10,
                0, 0, 0, 0,
                new double[0], 1,
                0, 1_000, 0, 1));
        assertClose(210, criticalBonus.finalRoundedDamage());

        CriticalHitResolver resolver = new CriticalHitResolver(8);
        UUID attacker = UUID.randomUUID();
        UUID cast = UUID.randomUUID();
        AtomicInteger rolls = new AtomicInteger();
        assert resolver.resolve(attacker, cast, .25, () -> {
            rolls.incrementAndGet();
            return .20;
        });
        assert resolver.resolve(attacker, cast, .25, () -> {
            rolls.incrementAndGet();
            return .90;
        });
        assert rolls.get() == 1;
        assertClose(1, StatCalculator.criticalChanceForRoll(10));
        assertClose(0, StatCalculator.criticalChanceForRoll(-1));
    }

    private static void characterizeShieldAndLifeSteal() {
        DamageResult shielded = DamageCalculator.calculate(input(
                DamageType.PHYSICAL, DamageMode.PVE,
                DamageKind.NORMAL_ATTACK,
                0, 100, 0,
                0, false, 1.75,
                0, 0, 0, 0,
                new double[0], 1,
                30, 50, .20, 1));
        assertClose(100, shielded.damageBeforeShield());
        assertClose(30, shielded.shieldDamage());
        assertClose(50, shielded.healthDamage());
        assertClose(10, shielded.lifeStealHealing());
        assertClose(100, shielded.finalRoundedDamage());

        assertClose(0, DamageCalculator.calculate(input(
                DamageType.MAGICAL, DamageMode.PVE,
                DamageKind.DAMAGE_OVER_TIME,
                0, 100, 0,
                0, false, 1.75,
                0, 0, 0, 0,
                new double[0], 1,
                0, 100, .50, 1)).lifeStealHealing());
        assertClose(0, DamageCalculator.calculate(input(
                DamageType.PHYSICAL, DamageMode.PVE,
                DamageKind.REFLECTED,
                0, 100, 0,
                0, false, 1.75,
                0, 0, 0, 0,
                new double[0], 1,
                0, 100, .50, 1)).lifeStealHealing());
    }

    private static void characterizeSafetyAndDeterminism() {
        DamageCalculator.Input deterministic = input(
                DamageType.PHYSICAL, DamageMode.PVE,
                DamageKind.DIRECT_SKILL,
                10, 20, 2,
                .10, true, 1.75,
                75, .10, .20, 5,
                new double[]{.10}, 1,
                5, 100, .10, .33);
        DamageResult first = DamageCalculator.calculate(deterministic);
        DamageResult second = DamageCalculator.calculate(deterministic);
        assert first.equals(second);

        DamageResult negative = DamageCalculator.calculate(input(
                DamageType.PHYSICAL, DamageMode.PVE,
                DamageKind.DIRECT_SKILL,
                -10, -20, -2,
                -10, false, 1.75,
                -75, -1, -1, -5,
                new double[]{-1}, -1,
                -5, -100, -1, -1));
        assertClose(0, negative.finalRoundedDamage());
        assertClose(0, negative.effectiveDefense());

        DamageResult nonFinite = DamageCalculator.calculate(
                new DamageCalculator.Input(
                        DamageType.PHYSICAL, DamageMode.PVE,
                        DamageKind.DIRECT_SKILL,
                        Double.NaN, Double.POSITIVE_INFINITY,
                        Double.NEGATIVE_INFINITY,
                        Double.NaN, Double.POSITIVE_INFINITY,
                        true, Double.POSITIVE_INFINITY,
                        Double.NaN, Double.NaN,
                        Double.POSITIVE_INFINITY,
                        Double.NEGATIVE_INFINITY,
                        Double.NaN,
                        new double[]{Double.NaN, Double.POSITIVE_INFINITY},
                        Double.POSITIVE_INFINITY,
                        Double.NaN, Double.POSITIVE_INFINITY,
                        Double.NaN, Double.POSITIVE_INFINITY,
                        Double.NEGATIVE_INFINITY));
        assertFiniteAndNonNegative(nonFinite.finalRoundedDamage());
        assertFiniteAndNonNegative(nonFinite.damageBeforeShield());
        assertFiniteAndNonNegative(nonFinite.effectiveDefense());

        assert DamageEventApplicationPolicy.replacesModifier("ARMOR");
        assert DamageEventApplicationPolicy.replacesModifier("RESISTANCE");
        assert !DamageEventApplicationPolicy.replacesModifier("ABSORPTION");
    }

    @SuppressWarnings("unchecked")
    private static void characterizeReentryTracking() {
        try {
            DamageService service = allocateWithoutConstructor(DamageService.class);
            UUID attackerId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            Player attacker = entityProxy(Player.class, attackerId);
            LivingEntity target = entityProxy(LivingEntity.class, targetId);

            Class<?> keyType = Class.forName(
                    DamageService.class.getName() + "$DamageKey");
            Constructor<?> keyConstructor = keyType.getDeclaredConstructor(
                    UUID.class, UUID.class);
            keyConstructor.setAccessible(true);
            Object key = keyConstructor.newInstance(attackerId, targetId);

            Field applyingField = DamageService.class.getDeclaredField("applying");
            applyingField.setAccessible(true);
            applyingField.set(service, new HashMap<>());
            Map<Object, Deque<DamageResult>> applying =
                    (Map<Object, Deque<DamageResult>>) applyingField.get(service);
            DamageResult outer = DamageCalculator.calculate(input(
                    DamageType.PHYSICAL, DamageMode.PVE,
                    DamageKind.NORMAL_ATTACK,
                    0, 10, 0, 0, false, 1.75,
                    0, 0, 0, 0, new double[0], 1,
                    0, 100, 0, 1));
            DamageResult nested = DamageCalculator.calculate(input(
                    DamageType.PHYSICAL, DamageMode.PVE,
                    DamageKind.NORMAL_ATTACK,
                    0, 20, 0, 0, false, 1.75,
                    0, 0, 0, 0, new double[0], 1,
                    0, 100, 0, 1));
            Deque<DamageResult> stack = new ArrayDeque<>();
            applying.put(key, stack);

            stack.push(outer);
            assert service.isApplying(attacker, target);
            assert service.currentCalculation(attacker, target).equals(outer);
            stack.push(nested);
            assert service.currentCalculation(attacker, target).equals(nested);
            stack.pop();
            assert service.currentCalculation(attacker, target).equals(outer);
            stack.pop();
            applying.remove(key);
            assert !service.isApplying(attacker, target);
            assert service.currentCalculation(attacker, target) == null;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to characterize reentry tracking", exception);
        }
    }

    private static <T> T allocateWithoutConstructor(Class<T> type)
            throws ReflectiveOperationException {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        return type.cast(unsafeType.getMethod("allocateInstance", Class.class)
                .invoke(unsafe, type));
    }

    private static <T> T entityProxy(Class<T> type, UUID id) {
        Object proxy = Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type},
                (ignored, method, arguments) -> {
                    if (method.getName().equals("getUniqueId")) return id;
                    if (method.getName().equals("toString")) return type.getSimpleName();
                    if (method.getName().equals("hashCode")) return System.identityHashCode(ignored);
                    if (method.getName().equals("equals")) return ignored == arguments[0];
                    throw new UnsupportedOperationException(method.toString());
                });
        return type.cast(proxy);
    }

    private static DamageCalculator.Input input(
            DamageType type,
            DamageMode mode,
            DamageKind kind,
            double attackPower,
            double fixedDamage,
            double coefficient,
            double damageIncrease,
            boolean critical,
            double criticalMultiplier,
            double defense,
            double defenseReduction,
            double penetration,
            double flatPenetration,
            double[] reductions,
            double modeMultiplier,
            double shield,
            double health,
            double lifeSteal,
            double lifeStealEfficiency
    ) {
        return new DamageCalculator.Input(
                type, mode, kind,
                attackPower, fixedDamage, coefficient,
                damageIncrease, 0,
                critical, criticalMultiplier,
                defense, defenseReduction,
                penetration, flatPenetration,
                StatCalculator.DEFAULT_DEFENSE_CONSTANT,
                reductions, modeMultiplier,
                shield, health,
                lifeSteal, lifeStealEfficiency, 0);
    }

    private static void assertFiniteAndNonNegative(double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new AssertionError("Expected finite non-negative value: " + value);
        }
    }

    private static void assertClose(double expected, double actual) {
        if (!Double.isFinite(actual)
                || Math.abs(expected - actual) > .000_001) {
            throw new AssertionError(
                    "Expected " + expected + " but got " + actual);
        }
    }
}
