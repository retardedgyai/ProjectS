package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.combat.stat.StatCalculator;

/** Characterizes the legacy Editor Mob basic-attack input contract after delegation. */
public final class MobAbilityDamageParityTest {
    public static void main(String[] args) {
        assertLegacyParity(DamageType.PHYSICAL, 42, 7, .8, true, 1.75, 31, .18, 4, 90);
        assertLegacyParity(DamageType.MAGICAL, 55, 5, 1.2, false, 2.10, 19, .27, 0, 120);
        DamageCalculator.Input ability = DamageService.mobDamageInput(
                DamageType.MAGICAL, DamageKind.DIRECT_SKILL, 55, 5, 1.2,
                false, 2.10, 19, new double[]{.27}, 0, 120);
        assert ability.damageKind() == DamageKind.DIRECT_SKILL;
        System.out.println("MobAbilityDamageParityTest passed");
    }

    private static void assertLegacyParity(
            DamageType type, double attackPower, double fixed, double coefficient,
            boolean critical, double criticalMultiplier, double defense,
            double reduction, double shield, double health
    ) {
        DamageCalculator.Input delegated = DamageService.mobDamageInput(
                type, DamageKind.NORMAL_ATTACK, attackPower, fixed, coefficient,
                critical, criticalMultiplier, defense, new double[]{reduction}, shield, health);
        DamageCalculator.Input legacy = new DamageCalculator.Input(
                type, DamageMode.PVE, DamageKind.NORMAL_ATTACK, attackPower,
                fixed, coefficient, 0, 0, critical, criticalMultiplier,
                defense, 0, 0, 0, StatCalculator.DEFAULT_DEFENSE_CONSTANT,
                new double[]{reduction}, 1, shield, health, 0, 0, 0);
        assert delegated.damageType() == legacy.damageType();
        assert delegated.mode() == legacy.mode();
        assert delegated.damageKind() == legacy.damageKind();
        assert delegated.attackPower() == legacy.attackPower();
        assert delegated.fixedDamage() == legacy.fixedDamage();
        assert delegated.coefficient() == legacy.coefficient();
        assert delegated.critical() == legacy.critical();
        assert delegated.criticalMultiplier() == legacy.criticalMultiplier();
        assert delegated.defense() == legacy.defense();
        assert delegated.defenseReductionPercent() == 0 && delegated.penetrationPercent() == 0
                && delegated.flatPenetration() == 0 && delegated.lifeStealPercent() == 0
                && delegated.lifeStealEfficiency() == 0;
        assert java.util.Arrays.equals(delegated.damageReductions(), legacy.damageReductions());
        assert DamageCalculator.calculate(delegated).equals(DamageCalculator.calculate(legacy));
    }
}
