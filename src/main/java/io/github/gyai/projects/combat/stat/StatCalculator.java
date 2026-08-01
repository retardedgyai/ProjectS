package io.github.gyai.projects.combat.stat;

import java.util.Arrays;

public final class StatCalculator {
    public static final double MAX_SAFE_VALUE = Long.MAX_VALUE / 1_000.0;
    public static final double DEFAULT_DEFENSE_CONSTANT = 300.0;
    public static final double MINIMUM_NORMAL_ATTACK_INTERVAL = 0.20;
    public static final double MINIMUM_SPEED_DURATION_RATIO = 0.35;
    public static final double MINIMUM_COOLDOWN_RATIO = 0.25;
    public static final double MINIMUM_COOLDOWN_SECONDS = 0.5;

    private StatCalculator() {
    }

    public static double attackPower(
            double weaponAttack,
            double flatAttack,
            double percentAttack
    ) {
        return saturatedMultiply(
                nonNegative(saturatedAdd(nonNegative(weaponAttack), flatAttack)),
                nonNegative(saturatedAdd(1.0, percentAttack)));
    }

    public static double baseDamage(
            double fixedDamage,
            double attackPower,
            double coefficient
    ) {
        return saturatedAdd(nonNegative(fixedDamage), saturatedMultiply(
                nonNegative(attackPower), nonNegative(coefficient)));
    }

    public static double defense(
            double equipmentDefense,
            double flatDefense,
            double percentDefense
    ) {
        return saturatedMultiply(
                nonNegative(saturatedAdd(nonNegative(equipmentDefense), flatDefense)),
                nonNegative(saturatedAdd(1.0, percentDefense)));
    }

    public static double effectiveDefense(
            double defense,
            double defenseReductionPercent,
            double penetrationPercent,
            double flatPenetration
    ) {
        double reduction = clamp01(defenseReductionPercent);
        double penetration = clamp01(penetrationPercent);
        double afterReduction = saturatedMultiply(
                nonNegative(defense), 1.0 - reduction);
        double afterPercentPenetration = saturatedMultiply(
                afterReduction, 1.0 - penetration);
        return nonNegative(saturatedAdd(
                afterPercentPenetration, -nonNegative(flatPenetration)));
    }

    public static double defenseMultiplier(double effectiveDefense, double constant) {
        double safeConstant = positiveOrDefault(constant, DEFAULT_DEFENSE_CONSTANT);
        return safeConstant / (safeConstant + nonNegative(effectiveDefense));
    }

    public static double attacksPerSecond(double baseAttacksPerSecond, double attackSpeedPercent) {
        return saturatedMultiply(nonNegative(baseAttacksPerSecond),
                nonNegative(saturatedAdd(1.0, attackSpeedPercent)));
    }

    public static double normalAttackInterval(
            double baseAttacksPerSecond,
            double attackSpeedPercent
    ) {
        double speed = attacksPerSecond(baseAttacksPerSecond, attackSpeedPercent);
        if (speed <= 0.0) return Double.MAX_VALUE;
        return Math.max(MINIMUM_NORMAL_ATTACK_INTERVAL, 1.0 / speed);
    }

    public static double actionDuration(
            double baseDuration,
            double speedPercent,
            SpeedCategory category
    ) {
        double base = nonNegative(baseDuration);
        if (base == 0.0 || category == null || category == SpeedCategory.FIXED) {
            return base;
        }
        double divisor = positiveOrDefault(1.0 + finiteOrZero(speedPercent), 1.0);
        return Math.max(base * MINIMUM_SPEED_DURATION_RATIO, base / divisor);
    }

    public static double attackActionDuration(double baseDuration, double attackSpeedPercent) {
        return actionDuration(baseDuration, attackSpeedPercent, SpeedCategory.ATTACK);
    }

    public static double castDuration(double baseDuration, double castSpeedPercent) {
        return actionDuration(baseDuration, castSpeedPercent, SpeedCategory.CAST);
    }

    public static double cooldownSeconds(double baseCooldown, double recoveryPercent) {
        double base = nonNegative(baseCooldown);
        if (base == 0.0) return 0.0;
        double divisor = positiveOrDefault(1.0 + finiteOrZero(recoveryPercent), 1.0);
        double minimum = Math.max(base * MINIMUM_COOLDOWN_RATIO, MINIMUM_COOLDOWN_SECONDS);
        return Math.max(minimum, base / divisor);
    }

    public static double healingPower(double flatHealingPower, double percentHealingPower) {
        return saturatedMultiply(nonNegative(flatHealingPower),
                nonNegative(saturatedAdd(1.0, percentHealingPower)));
    }

    public static double healing(
            double fixedHealing,
            double healingPower,
            double healingCoefficient,
            double outgoingIncrease,
            double incomingIncrease,
            double healingReduction
    ) {
        double base = saturatedAdd(nonNegative(fixedHealing), saturatedMultiply(
                nonNegative(healingPower), nonNegative(healingCoefficient)));
        double result = saturatedMultiply(base,
                nonNegative(saturatedAdd(1.0, outgoingIncrease)));
        result = saturatedMultiply(result,
                nonNegative(saturatedAdd(1.0, incomingIncrease)));
        return saturatedMultiply(result, 1.0 - clamp01(healingReduction));
    }

    public static double lifeSteal(
            double actualHealthDamage,
            double lifeStealPercent,
            double skillEfficiency,
            double healingReduction
    ) {
        double result = saturatedMultiply(
                nonNegative(actualHealthDamage), nonNegative(lifeStealPercent));
        result = saturatedMultiply(result, clamp01(skillEfficiency));
        return saturatedMultiply(result, 1.0 - clamp01(healingReduction));
    }

    public static double shieldDamage(double finalDamage, double shield) {
        return Math.min(nonNegative(finalDamage), nonNegative(shield));
    }

    public static double actualHealthDamage(double finalDamage, double shield, double health) {
        return Math.min(nonNegative(health),
                nonNegative(finalDamage) - shieldDamage(finalDamage, shield));
    }

    public static double movementSpeed(
            double baseSpeed,
            double flatSpeed,
            double percentSpeed,
            boolean rooted,
            double... slowMultipliers
    ) {
        double base = nonNegative(baseSpeed);
        if (rooted || base == 0.0) return 0.0;
        double speed = saturatedMultiply(
                nonNegative(saturatedAdd(base, flatSpeed)),
                nonNegative(saturatedAdd(1.0, percentSpeed)));
        if (slowMultipliers != null) {
            double slowMultiplier = Arrays.stream(slowMultipliers)
                    .map(StatCalculator::clamp01)
                    .reduce(1.0, (left, right) -> left * right);
            speed = saturatedMultiply(speed, slowMultiplier);
        }
        return Math.clamp(speed, base * 0.30, base * 2.0);
    }

    public static double maximumMana(double classBaseMana, double flatMana, double percentMana) {
        return saturatedMultiply(
                nonNegative(saturatedAdd(nonNegative(classBaseMana), flatMana)),
                nonNegative(saturatedAdd(1.0, percentMana)));
    }

    public static double manaRegeneration(
            double classBaseRegeneration,
            double flatRegeneration,
            double percentRegeneration,
            boolean outOfCombat
    ) {
        double result = saturatedMultiply(
                nonNegative(saturatedAdd(
                        nonNegative(classBaseRegeneration), flatRegeneration)),
                nonNegative(saturatedAdd(1.0, percentRegeneration)));
        return saturatedMultiply(result, outOfCombat ? 3.0 : 1.0);
    }

    public static double criticalChanceForRoll(double criticalChancePercent) {
        return clamp01(criticalChancePercent);
    }

    public static double clamp01(double value) {
        return Math.clamp(finiteOrZero(value), 0.0, 1.0);
    }

    public static double finiteOrZero(double value) {
        if (Double.isNaN(value)) return 0.0;
        if (value == Double.POSITIVE_INFINITY) return MAX_SAFE_VALUE;
        if (value == Double.NEGATIVE_INFINITY) return -MAX_SAFE_VALUE;
        return Math.clamp(value, -MAX_SAFE_VALUE, MAX_SAFE_VALUE);
    }

    public static double nonNegative(double value) {
        return Math.max(0.0, finiteOrZero(value));
    }

    public static double saturatedAdd(double left, double right) {
        double safeLeft = finiteOrZero(left);
        double safeRight = finiteOrZero(right);
        if (safeRight > 0.0 && safeLeft > MAX_SAFE_VALUE - safeRight) {
            return MAX_SAFE_VALUE;
        }
        if (safeRight < 0.0 && safeLeft < -MAX_SAFE_VALUE - safeRight) {
            return -MAX_SAFE_VALUE;
        }
        return finiteOrZero(safeLeft + safeRight);
    }

    public static double saturatedMultiply(double left, double right) {
        double safeLeft = nonNegative(left);
        double safeRight = nonNegative(right);
        if (safeLeft == 0.0 || safeRight == 0.0) return 0.0;
        if (safeLeft > MAX_SAFE_VALUE / safeRight) return MAX_SAFE_VALUE;
        return nonNegative(safeLeft * safeRight);
    }

    private static double positiveOrDefault(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }
}
