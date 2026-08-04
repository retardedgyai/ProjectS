package io.github.gyai.projects.combat.stat;

import io.github.gyai.projects.combat.damage.DamageCalculator;
import io.github.gyai.projects.combat.damage.DamageMode;
import io.github.gyai.projects.combat.damage.DamageResult;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageEventApplicationPolicy;
import io.github.gyai.projects.combat.damage.DamageOffenseSnapshot;
import io.github.gyai.projects.combat.damage.CriticalHitResolver;
import io.github.gyai.projects.combat.damage.DamageType;
import io.github.gyai.projects.player.StatType;
import io.github.gyai.projects.player.Stats;
import io.github.gyai.projects.player.PlayerData;
import io.github.gyai.projects.skill.SkillManager;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class StatFoundationTest {
    private StatFoundationTest() {
    }

    public static void main(String[] args) {
        assertClose(180.0, StatCalculator.attackPower(100, 20, .50));
        assertClose(90.0, StatCalculator.attackPower(50, 10, .50));
        assertClose(40.0, StatCalculator.baseDamage(10, 20, 1.5));

        assertClose(1.0, StatCalculator.defenseMultiplier(0, 300));
        assertClose(.5, StatCalculator.defenseMultiplier(300, 300));
        assertClose(150.0, StatCalculator.effectiveDefense(500, .20, .50, 50));
        assertClose(0.0, StatCalculator.effectiveDefense(100, .50, .50, 100));

        assertClose(1.5, StatCalculator.attacksPerSecond(1.0, .50));
        assertClose(2.0 / 3.0, StatCalculator.normalAttackInterval(1.0, .50));
        assertClose(2.0 / 3.0, StatCalculator.castDuration(1.0, .50));
        assertClose(10.0, StatCalculator.cooldownSeconds(10.0, 0.0));
        assertClose(20.0, StatCalculator.cooldownSeconds(20.0, 0.0));
        assertClose(8.0, StatCalculator.cooldownSeconds(10.0, .25));
        assertClose(10.0 / 1.5, StatCalculator.cooldownSeconds(10.0, .50));
        assertClose(20.0, StatCalculator.cooldownSeconds(10.0, -.50));
        assertClose(10.0, StatCalculator.cooldownSeconds(10.0, -1.0));
        assertClose(5.0, StatCalculator.cooldownSeconds(10.0, 1.0));
        assertClose(2.5, StatCalculator.cooldownSeconds(10.0, 10.0));

        assertClose(60.0, StatCalculator.healing(100, 0, 0, .20, 0, .50));
        assertClose(9.9, StatCalculator.lifeSteal(100, .30, .33, 0));
        assertClose(10.0, StatCalculator.actualHealthDamage(100, 20, 10));
        assertClose(0.04, StatCalculator.movementSpeed(
                .10, 0, 0, false, .80, .50));
        assertClose(660.0, StatCalculator.maximumMana(400, 40, .50));
        assertClose(36.0, StatCalculator.manaRegeneration(8, 2, .20, true));

        Stats stats = new Stats();
        assertClose(0.0, stats.get(StatType.COOLDOWN_RECOVERY_PERCENT));
        stats.set(StatType.COOLDOWN_RECOVERY_PERCENT, .25);
        stats.reset();
        assertClose(0.0, stats.get(StatType.COOLDOWN_RECOVERY_PERCENT));
        expectIllegal(() -> stats.set(
                StatType.COOLDOWN_RECOVERY_PERCENT, Double.NaN));
        expectIllegal(() -> stats.set(
                StatType.COOLDOWN_RECOVERY_PERCENT, Double.POSITIVE_INFINITY));
        PlayerData newPlayer = new PlayerData(UUID.randomUUID());
        assertClose(0.0, newPlayer.getCooldownRecoveryPercent());
        characterizeFullCooldownToggle();

        stats.set(StatType.CRITICAL_CHANCE_PERCENT, 2.5);
        assertClose(2.5, stats.get(StatType.CRITICAL_CHANCE_PERCENT));
        assertClose(1.0, StatCalculator.criticalChanceForRoll(
                stats.get(StatType.CRITICAL_CHANCE_PERCENT)));
        assertClose(0.0, StatCalculator.criticalChanceForRoll(-5.0));
        expectIllegal(() -> stats.set(StatType.MAX_HEALTH_FLAT, Double.NaN));
        expectIllegal(() -> stats.add(
                StatType.MAX_HEALTH_FLAT, Double.POSITIVE_INFINITY));

        DamageCalculator.Input deterministicInput = new DamageCalculator.Input(
                DamageType.PHYSICAL, DamageMode.PVE, DamageKind.DIRECT_SKILL,
                20, 10, 1.5, 0, 0,
                false, 1.75, 300, 0, 0, 0, 300,
                new double[0], 1.0, 0, 100, 0, 1.0, 0);
        DamageResult first = DamageCalculator.calculate(deterministicInput);
        DamageResult second = DamageCalculator.calculate(deterministicInput);
        assertClose(20.0, first.finalRoundedDamage());
        assertClose(first.finalRoundedDamage(), second.finalRoundedDamage());
        assert !first.critical();

        assertClose(1.0, DamageKind.NORMAL_ATTACK.lifeStealEfficiency(
                false, DamageType.PHYSICAL));
        assertClose(.33, DamageKind.DIRECT_SKILL.lifeStealEfficiency(
                true, DamageType.MAGICAL));
        assertClose(0.0, DamageKind.DIRECT_SKILL.lifeStealEfficiency(
                false, DamageType.TRUE));
        assertClose(0.0, DamageKind.DAMAGE_OVER_TIME.lifeStealEfficiency(
                false, DamageType.MAGICAL));

        DamageResult rawDamage = DamageCalculator.calculate(input(
                DamageType.PHYSICAL, DamageMode.PVE,
                1.2346, 0, new double[0]));
        assertClose(1.2346, rawDamage.damageBeforeShield());
        assertClose(1.235, rawDamage.finalRoundedDamage());

        DamageResult trueDamage = DamageCalculator.calculate(new DamageCalculator.Input(
                DamageType.TRUE, DamageMode.PVE, DamageKind.DIRECT_SKILL,
                0, 100, 0, 0, 0,
                false, 1.75, 1_000_000, 1, 1, 1_000_000, 300,
                new double[0], 1.0, 0, 100, 0, 0, 0));
        assertClose(100.0, trueDamage.finalRoundedDamage());
        assertClose(1.0, trueDamage.defenseMultiplier());

        DamageResult multiplicativeReduction = DamageCalculator.calculate(input(
                DamageType.PHYSICAL, DamageMode.PVE,
                100, 0, new double[]{.50, .50}));
        assertClose(.25, multiplicativeReduction.reductionMultiplier());
        DamageResult pveCap = DamageCalculator.calculate(input(
                DamageType.PHYSICAL, DamageMode.PVE,
                100, 0, new double[]{.90}));
        DamageResult pvpCap = DamageCalculator.calculate(input(
                DamageType.PHYSICAL, DamageMode.PVP,
                100, 0, new double[]{.90}));
        assertClose(.20, pveCap.reductionMultiplier());
        assertClose(.25, pvpCap.reductionMultiplier());

        DamageResult overflow = DamageCalculator.calculate(new DamageCalculator.Input(
                DamageType.PHYSICAL, DamageMode.PVE, DamageKind.DIRECT_SKILL,
                Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                Double.MAX_VALUE, Double.MAX_VALUE,
                true, Double.MAX_VALUE, 0, 0, 0, 0, 300,
                new double[0], Double.MAX_VALUE, 0, Double.MAX_VALUE,
                Double.MAX_VALUE, 1.0, 0));
        assert Double.isFinite(overflow.damageIncreaseMultiplier());
        assert Double.isFinite(overflow.damageBeforeShield());
        assert Double.isFinite(overflow.finalRoundedDamage());
        assert overflow.finalRoundedDamage() > 0.0;

        assert DamageEventApplicationPolicy.replacesModifier("ARMOR");
        assert DamageEventApplicationPolicy.replacesModifier("RESISTANCE");
        assert DamageEventApplicationPolicy.replacesModifier("MAGIC");
        assert DamageEventApplicationPolicy.replacesModifier("BLOCKING");
        assert DamageEventApplicationPolicy.replacesModifier("HARD_HAT");
        assert !DamageEventApplicationPolicy.replacesModifier(
                "INVULNERABILITY_REDUCTION");
        assert !DamageEventApplicationPolicy.replacesModifier("FREEZING");
        assert !DamageEventApplicationPolicy.replacesModifier("ABSORPTION");
        assertClose(-6.0, DamageEventApplicationPolicy.absorptionModifier(10, 6));
        assertClose(4.0, DamageEventApplicationPolicy.damageAfterAbsorption(10, 6));
        assertClose(0.0, DamageEventApplicationPolicy.damageAfterAbsorption(5, 10));
        assert !DamageEventApplicationPolicy.allowsPveTarget(true);
        assert DamageEventApplicationPolicy.allowsPveTarget(false);

        CriticalHitResolver criticalResolver = new CriticalHitResolver(8);
        UUID attacker = UUID.randomUUID();
        UUID sharedCast = UUID.randomUUID();
        AtomicInteger rolls = new AtomicInteger();
        assert criticalResolver.resolve(
                attacker, sharedCast, .5,
                () -> rolls.getAndIncrement() == 0 ? .25 : .75);
        assert criticalResolver.resolve(attacker, sharedCast, .5, () -> .75);
        assert rolls.get() == 1;
        assert !criticalResolver.resolve(
                attacker, UUID.randomUUID(), .5, () -> .75);

        assertClose(50.0, DamageCalculator.calculate(
                lifeStealInput(DamageType.PHYSICAL, DamageKind.NORMAL_ATTACK))
                .lifeStealHealing());
        assertClose(0.0, DamageCalculator.calculate(
                lifeStealInput(DamageType.TRUE, DamageKind.DIRECT_SKILL))
                .lifeStealHealing());
        assertClose(0.0, DamageCalculator.calculate(
                lifeStealInput(DamageType.MAGICAL, DamageKind.DAMAGE_OVER_TIME))
                .lifeStealHealing());
        assertClose(0.0, DamageCalculator.calculate(
                lifeStealInput(DamageType.PHYSICAL, DamageKind.REFLECTED))
                .lifeStealHealing());
        assertClose(0.0, DamageCalculator.calculate(
                lifeStealInput(DamageType.PHYSICAL, DamageKind.PERCENT_HEALTH))
                .lifeStealHealing());

        DamageResult primary = DamageCalculator.calculate(
                new DamageCalculator.Input(
                        DamageType.PHYSICAL, DamageMode.PVE,
                        DamageKind.NORMAL_ATTACK, 0, 100, 0,
                        1.0, 0, true, 2.0, 300, 0, 0, 0, 300,
                        new double[0], 1.0, 0, 1_000, 0, 1, 0));
        assertClose(400.0, primary.offenseResolvedDamage());
        assertClose(200.0, primary.finalRoundedDamage());
        DamageOffenseSnapshot splashSnapshot = new DamageOffenseSnapshot(
                primary.offenseResolvedDamage() * .5,
                primary.critical(), primary.criticalMultiplier());
        DamageResult unarmoredSplash = DamageCalculator.calculateOffenseResolved(
                offenseInput(splashSnapshot, 0));
        DamageResult armoredSplash = DamageCalculator.calculateOffenseResolved(
                offenseInput(splashSnapshot, 300));
        assertClose(200.0, unarmoredSplash.finalRoundedDamage());
        assertClose(100.0, armoredSplash.finalRoundedDamage());
        assert unarmoredSplash.critical();
        assertClose(2.0, unarmoredSplash.criticalMultiplier());
    }

    private static DamageCalculator.Input lifeStealInput(
            DamageType type,
            DamageKind kind
    ) {
        return new DamageCalculator.Input(
                type, DamageMode.PVE, kind,
                0, 100, 0, 0, 0, false, 1.75,
                0, 0, 0, 0, 300, new double[0], 1.0,
                0, 100, .5, 1.0, 0);
    }

    private static DamageCalculator.OffenseInput offenseInput(
            DamageOffenseSnapshot snapshot,
            double defense
    ) {
        return new DamageCalculator.OffenseInput(
                DamageType.PHYSICAL, DamageMode.PVE,
                DamageKind.DIRECT_SKILL, snapshot, 0, defense,
                0, 0, 0, 300, new double[0],
                0, 1_000, 0, .33, 0);
    }

    private static DamageCalculator.Input input(
            DamageType type,
            DamageMode mode,
            double fixedDamage,
            double defense,
            double[] reductions
    ) {
        return new DamageCalculator.Input(
                type, mode, DamageKind.DIRECT_SKILL, 0, fixedDamage, 0,
                0, 0, false, mode.baseCriticalMultiplier(),
                defense, 0, 0, 0, 300,
                reductions, 1.0, 0, 100, 0, 0, 0);
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void characterizeFullCooldownToggle() {
        UUID playerId = UUID.randomUUID();
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getUniqueId")) return playerId;
                    if (method.getName().equals("hashCode")) return playerId.hashCode();
                    if (method.getName().equals("equals")) return proxy == arguments[0];
                    if (method.getName().equals("toString")) return "CooldownTestPlayer";
                    throw new UnsupportedOperationException(method.toString());
                });
        SkillManager manager = new SkillManager(null);
        manager.startCooldown(player, "test", 10.0, 0.0);
        assert manager.getRemainingCooldownSeconds(player, "test") > 9.0;
        assert manager.toggleFullCooldownReduction(player);
        assertClose(0.0, manager.getRemainingCooldownSeconds(player, "test"));
        manager.startCooldown(player, "test", 10.0, 0.0);
        assertClose(0.0, manager.getRemainingCooldownSeconds(player, "test"));
    }

    private static void assertClose(double expected, double actual) {
        if (!Double.isFinite(actual)
                || Math.abs(expected - actual) > 0.000_001) {
            throw new AssertionError(
                    "Expected " + expected + " but got " + actual);
        }
    }
}
