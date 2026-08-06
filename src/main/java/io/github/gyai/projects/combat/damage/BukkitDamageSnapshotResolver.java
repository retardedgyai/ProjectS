package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.combat.stat.StatCalculator;
import io.github.gyai.projects.item.Armor;
import io.github.gyai.projects.manager.EnhancementManager;
import io.github.gyai.projects.manager.ItemManager;
import io.github.gyai.projects.manager.PlayerManager;
import io.github.gyai.projects.monster.editor.MobStatsDefinition;
import io.github.gyai.projects.player.StatType;
import io.github.gyai.projects.player.Stats;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.Function;

/** Resolves Bukkit and ProjectS runtime state into pure immutable snapshots. */
public final class BukkitDamageSnapshotResolver {
    private final PlayerManager playerManager;
    private final ItemManager itemManager;
    private final EnhancementManager enhancementManager;
    private Function<LivingEntity, MobStatsDefinition> mobStatsResolver =
            ignored -> null;

    public BukkitDamageSnapshotResolver(
            PlayerManager playerManager,
            ItemManager itemManager,
            EnhancementManager enhancementManager
    ) {
        this.playerManager = Objects.requireNonNull(
                playerManager, "playerManager");
        this.itemManager = Objects.requireNonNull(itemManager, "itemManager");
        this.enhancementManager = Objects.requireNonNull(
                enhancementManager, "enhancementManager");
    }

    public void setMobStatsResolver(
            Function<LivingEntity, MobStatsDefinition> resolver
    ) {
        mobStatsResolver = resolver == null ? ignored -> null : resolver;
    }

    public DamageCalculationSnapshot resolve(
            DamageRequest request,
            boolean critical
    ) {
        Objects.requireNonNull(request, "request");
        Stats attackerStats = playerManager
                .getPlayerData(request.attacker()).getStats();
        Stats targetStats = request.target() instanceof Player player
                ? playerManager.getPlayerData(player).getStats()
                : new Stats();
        MobStatsDefinition mobStats = mobStatsResolver.apply(request.target());

        double attackPower = attackPower(request, attackerStats);
        double damageIncrease = damageIncrease(request, attackerStats);
        double criticalMultiplier = critical
                ? Math.max(1.0, StatCalculator.finiteOrZero(
                request.mode().baseCriticalMultiplier()
                        + attackerStats.get(StatType.CRITICAL_DAMAGE_BONUS)))
                : 1.0;
        double baseDamage = StatCalculator.baseDamage(
                request.fixedDamage(), attackPower, request.coefficient());
        double outgoingMultiplier = StatCalculator.nonNegative(
                StatCalculator.saturatedAdd(1.0, damageIncrease));
        double offenseDamage = StatCalculator.saturatedMultiply(
                baseDamage, outgoingMultiplier);
        offenseDamage = StatCalculator.saturatedMultiply(
                offenseDamage, criticalMultiplier);
        offenseDamage = StatCalculator.saturatedMultiply(
                offenseDamage, request.calculationMultiplier());
        DamageOffenseSnapshot offenseSnapshot = new DamageOffenseSnapshot(
                offenseDamage, critical, criticalMultiplier);

        DamageDefenseSnapshot defenseSnapshot = new DamageDefenseSnapshot(
                defense(request.target(), targetStats, mobStats, DamageType.PHYSICAL),
                defense(request.target(), targetStats, mobStats, DamageType.MAGICAL),
                StatCalculator.clamp01(request.defenseReductionPercent()),
                StatCalculator.clamp01(request.defenseReductionPercent()),
                StatCalculator.nonNegative(StatCalculator.saturatedAdd(
                        1.0, request.damageTakenIncreasePercent())),
                StatCalculator.clamp01(StatCalculator.saturatedAdd(
                        targetStats.get(StatType.DAMAGE_REDUCTION_PERCENT),
                        mobStats == null ? 0.0 : mobStats.damageReduction())),
                StatCalculator.nonNegative(request.target().getAbsorptionAmount()),
                StatCalculator.nonNegative(request.target().getHealth()),
                1.0);

        return new DamageCalculationSnapshot(
                request.damageType(),
                request.mode(),
                request.damageKind(),
                request.attackMetadata(),
                attackPower,
                request.fixedDamage(),
                request.coefficient(),
                damageIncrease,
                critical,
                criticalMultiplier,
                offenseSnapshot,
                defenseSnapshot,
                penetrationPercent(request.damageType(), attackerStats),
                flatPenetration(request.damageType(), attackerStats),
                StatCalculator.DEFAULT_DEFENSE_CONSTANT,
                request.additionalDamageReductions(),
                request.calculationMultiplier(),
                attackerStats.get(StatType.LIFESTEAL_PERCENT),
                request.lifeStealEfficiency(),
                request.healingReductionPercent());
    }

    private double attackPower(DamageRequest request, Stats attackerStats) {
        ItemStack weapon = request.attacker()
                .getInventory().getItemInMainHand();
        double weaponAttack = switch (request.damageType()) {
            case PHYSICAL -> enhancementManager.getPhysicalAttackPower(
                    request.attacker(), weapon);
            case MAGICAL -> enhancementManager.getMagicalAttackPower(
                    request.attacker(), weapon);
            case TRUE -> 0.0;
        };
        return switch (request.damageType()) {
            case PHYSICAL -> StatCalculator.attackPower(
                    weaponAttack,
                    attackerStats.get(StatType.PHYSICAL_ATTACK_FLAT),
                    attackerStats.get(StatType.PHYSICAL_ATTACK_PERCENT));
            case MAGICAL -> StatCalculator.attackPower(
                    weaponAttack,
                    attackerStats.get(StatType.MAGICAL_ATTACK_FLAT),
                    attackerStats.get(StatType.MAGICAL_ATTACK_PERCENT));
            case TRUE -> 0.0;
        };
    }

    private static double damageIncrease(
            DamageRequest request,
            Stats attackerStats
    ) {
        double increase = attackerStats.get(
                StatType.DAMAGE_INCREASE_PERCENT);
        increase = StatCalculator.saturatedAdd(increase, switch (request.damageType()) {
            case PHYSICAL -> attackerStats.get(
                    StatType.PHYSICAL_DAMAGE_INCREASE_PERCENT);
            case MAGICAL -> attackerStats.get(
                    StatType.MAGICAL_DAMAGE_INCREASE_PERCENT);
            case TRUE -> 0.0;
        });
        increase = StatCalculator.saturatedAdd(increase,
                request.damageKind() == DamageKind.NORMAL_ATTACK
                ? attackerStats.get(
                StatType.BASIC_ATTACK_DAMAGE_INCREASE_PERCENT)
                : attackerStats.get(StatType.SKILL_DAMAGE_INCREASE_PERCENT));
        return increase;
    }

    private double defense(
            LivingEntity target,
            Stats targetStats,
            MobStatsDefinition mobStats,
            DamageType type
    ) {
        double equipmentDefense = mobStats == null
                ? equipmentDefense(target, type) : 0.0;
        return switch (type) {
            case PHYSICAL -> StatCalculator.defense(
                    equipmentDefense + (mobStats == null
                            ? 0.0 : mobStats.physicalDefense()),
                    targetStats.get(StatType.PHYSICAL_DEFENSE_FLAT),
                    targetStats.get(StatType.PHYSICAL_DEFENSE_PERCENT));
            case MAGICAL -> StatCalculator.defense(
                    equipmentDefense + (mobStats == null
                            ? 0.0 : mobStats.magicalDefense()),
                    targetStats.get(StatType.MAGICAL_DEFENSE_FLAT),
                    targetStats.get(StatType.MAGICAL_DEFENSE_PERCENT));
            case TRUE -> 0.0;
        };
    }

    private double equipmentDefense(LivingEntity target, DamageType type) {
        EntityEquipment equipment = target.getEquipment();
        if (equipment == null) {
            return 0.0;
        }
        double total = 0.0;
        for (ItemStack item : equipment.getArmorContents()) {
            if (itemManager.getItem(itemManager.getItemId(item))
                    instanceof Armor armor) {
                total += type == DamageType.MAGICAL
                        ? armor.getMagicalDefense()
                        : armor.getPhysicalDefense();
            }
        }
        return total;
    }

    private static double penetrationPercent(
            DamageType type,
            Stats attackerStats
    ) {
        return switch (type) {
            case PHYSICAL -> attackerStats.get(
                    StatType.PHYSICAL_PENETRATION_PERCENT);
            case MAGICAL -> attackerStats.get(
                    StatType.MAGICAL_PENETRATION_PERCENT);
            case TRUE -> 0.0;
        };
    }

    private static double flatPenetration(
            DamageType type,
            Stats attackerStats
    ) {
        return switch (type) {
            case PHYSICAL -> attackerStats.get(
                    StatType.PHYSICAL_PENETRATION_FLAT);
            case MAGICAL -> attackerStats.get(
                    StatType.MAGICAL_PENETRATION_FLAT);
            case TRUE -> 0.0;
        };
    }
}
