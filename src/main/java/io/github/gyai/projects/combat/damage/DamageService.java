package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.combat.stat.StatCalculator;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.item.Armor;
import io.github.gyai.projects.manager.EnhancementManager;
import io.github.gyai.projects.manager.ItemManager;
import io.github.gyai.projects.manager.PlayerManager;
import io.github.gyai.projects.monster.editor.MobDefinition;
import io.github.gyai.projects.monster.editor.MobStatsDefinition;
import io.github.gyai.projects.player.StatType;
import io.github.gyai.projects.player.Stats;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;

public final class DamageService implements Listener {
    private static final int MAX_CRITICAL_CAST_CACHE = 2_048;

    private final PlayerManager playerManager;
    private final ItemManager itemManager;
    private final EnhancementManager enhancementManager;
    private final TrainingDummyManager dummyManager;
    private final BukkitDamageSnapshotResolver snapshotResolver;
    private final Map<DamageKey, Deque<DamageResult>> applying = new HashMap<>();
    private final CriticalHitResolver criticalResolver =
            new CriticalHitResolver(MAX_CRITICAL_CAST_CACHE);
    private Function<LivingEntity, MobStatsDefinition> mobStatsResolver =
            ignored -> null;

    public DamageService(
            PlayerManager playerManager,
            ItemManager itemManager,
            EnhancementManager enhancementManager,
            TrainingDummyManager dummyManager
    ) {
        this.playerManager = playerManager;
        this.itemManager = itemManager;
        this.enhancementManager = enhancementManager;
        this.dummyManager = dummyManager;
        snapshotResolver = new BukkitDamageSnapshotResolver(
                playerManager, itemManager, enhancementManager);
    }

    public DamageResult calculate(DamageRequest request) {
        Stats attackerStats = playerManager.getPlayerData(request.attacker()).getStats();
        Stats targetStats = request.target() instanceof Player player
                ? playerManager.getPlayerData(player).getStats() : new Stats();
        double weaponAttack = switch (request.damageType()) {
            case PHYSICAL -> enhancementManager.getPhysicalAttackPower(
                    request.attacker(), request.attacker().getInventory().getItemInMainHand());
            case MAGICAL -> enhancementManager.getMagicalAttackPower(
                    request.attacker(), request.attacker().getInventory().getItemInMainHand());
            case TRUE -> 0.0;
        };
        double resolvedAttackPower = switch (request.damageType()) {
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
        double damageIncrease = attackerStats.get(StatType.DAMAGE_INCREASE_PERCENT);
        damageIncrease += switch (request.damageType()) {
            case PHYSICAL -> attackerStats.get(StatType.PHYSICAL_DAMAGE_INCREASE_PERCENT);
            case MAGICAL -> attackerStats.get(StatType.MAGICAL_DAMAGE_INCREASE_PERCENT);
            case TRUE -> 0.0;
        };
        damageIncrease += request.damageKind() == DamageKind.NORMAL_ATTACK
                ? attackerStats.get(StatType.BASIC_ATTACK_DAMAGE_INCREASE_PERCENT)
                : attackerStats.get(StatType.SKILL_DAMAGE_INCREASE_PERCENT);

        MobStatsDefinition mobStats = mobStatsResolver.apply(request.target());
        double equipmentDefense = mobStats == null
                ? equipmentDefense(request.target(), request.damageType()) : 0.0;
        double defense = switch (request.damageType()) {
            case PHYSICAL -> StatCalculator.defense(
                    equipmentDefense + (mobStats == null ? 0 : mobStats.physicalDefense()),
                    targetStats.get(StatType.PHYSICAL_DEFENSE_FLAT),
                    targetStats.get(StatType.PHYSICAL_DEFENSE_PERCENT));
            case MAGICAL -> StatCalculator.defense(
                    equipmentDefense + (mobStats == null ? 0 : mobStats.magicalDefense()),
                    targetStats.get(StatType.MAGICAL_DEFENSE_FLAT),
                    targetStats.get(StatType.MAGICAL_DEFENSE_PERCENT));
            case TRUE -> 0.0;
        };
        double penetrationPercent = switch (request.damageType()) {
            case PHYSICAL -> attackerStats.get(StatType.PHYSICAL_PENETRATION_PERCENT);
            case MAGICAL -> attackerStats.get(StatType.MAGICAL_PENETRATION_PERCENT);
            case TRUE -> 0.0;
        };
        double flatPenetration = switch (request.damageType()) {
            case PHYSICAL -> attackerStats.get(StatType.PHYSICAL_PENETRATION_FLAT);
            case MAGICAL -> attackerStats.get(StatType.MAGICAL_PENETRATION_FLAT);
            case TRUE -> 0.0;
        };
        double[] reductions = append(
                request.additionalDamageReductions(),
                StatCalculator.saturatedAdd(
                        targetStats.get(StatType.DAMAGE_REDUCTION_PERCENT),
                        mobStats == null ? 0 : mobStats.damageReduction()));
        if (request.offenseSnapshot() != null) {
            return DamageCalculator.calculateOffenseResolved(
                    new DamageCalculator.OffenseInput(
                            request.damageType(), request.mode(), request.damageKind(),
                            request.offenseSnapshot(),
                            request.damageTakenIncreasePercent(), defense,
                            request.defenseReductionPercent(), penetrationPercent,
                            flatPenetration, StatCalculator.DEFAULT_DEFENSE_CONSTANT,
                            reductions, request.target().getAbsorptionAmount(),
                            request.target().getHealth(),
                            attackerStats.get(StatType.LIFESTEAL_PERCENT),
                            request.lifeStealEfficiency(),
                            request.healingReductionPercent()));
        }
        boolean critical = resolveCritical(request, attackerStats);
        double criticalMultiplier = request.mode().baseCriticalMultiplier()
                + attackerStats.get(StatType.CRITICAL_DAMAGE_BONUS);
        return DamageCalculator.calculate(new DamageCalculator.Input(
                request.damageType(), request.mode(), request.damageKind(),
                resolvedAttackPower,
                request.fixedDamage(), request.coefficient(), damageIncrease,
                request.damageTakenIncreasePercent(), critical, criticalMultiplier,
                defense, request.defenseReductionPercent(), penetrationPercent,
                flatPenetration, StatCalculator.DEFAULT_DEFENSE_CONSTANT,
                reductions, request.modeMultiplier(),
                request.target().getAbsorptionAmount(), request.target().getHealth(),
                attackerStats.get(StatType.LIFESTEAL_PERCENT),
                request.lifeStealEfficiency(), request.healingReductionPercent()));
    }

    public DamageApplicationResult apply(DamageRequest request) {
        DamageResult calculated = calculate(request);
        return applyResolved(request, calculated);
    }

    public DamageApplicationResult apply(
            DamageRequest request,
            Consumer<DamageResult> calculationObserver
    ) {
        return apply(request, calculationObserver, null);
    }

    public DamageApplicationResult apply(
            DamageRequest request,
            Consumer<DamageResult> calculationObserver,
            Consumer<RuntimeException> calculationFailureObserver
    ) {
        DamageResult calculated;
        try {
            calculated = calculate(request);
        } catch (RuntimeException exception) {
            if (calculationFailureObserver != null) {
                try {
                    calculationFailureObserver.accept(exception);
                } catch (RuntimeException ignored) {
                    // Validation must never replace the original combat failure.
                }
            }
            throw exception;
        }
        return observeThenApply(
                calculated,
                calculationObserver,
                result -> applyResolved(request, result));
    }

    static <T> T observeThenApply(
            DamageResult legacyResult,
            Consumer<DamageResult> calculationObserver,
            Function<DamageResult, T> legacyApplier
    ) {
        if (calculationObserver != null) {
            try {
                calculationObserver.accept(legacyResult);
            } catch (RuntimeException ignored) {
                // Observational validation must not cancel legacy application.
            }
        }
        return legacyApplier.apply(legacyResult);
    }

    DamageApplicationResult applyResolved(
            DamageRequest request,
            DamageResult calculated
    ) {
        if (calculated.finalRoundedDamage() <= 0.0
                || !request.target().isValid()
                || request.mode() == DamageMode.PVE
                && !DamageEventApplicationPolicy.allowsPveTarget(
                        request.target() instanceof Player)) {
            return new DamageApplicationResult(
                    calculated, false, 0.0, 0.0, 0.0);
        }
        if (dummyManager.isTrainingDummy(request.target())
                && request.skillId() != null
                && request.damageKind() != DamageKind.NORMAL_ATTACK) {
            dummyManager.markSkillDamage(
                    request.attacker(), request.target(), request.skillId());
        }
        double healthBefore = request.target().getHealth();
        double shieldBefore = request.target().getAbsorptionAmount();
        DamageKey key = new DamageKey(
                request.attacker().getUniqueId(), request.target().getUniqueId());
        applying.computeIfAbsent(key, ignored -> new ArrayDeque<>())
                .push(calculated);
        try {
            request.target().damage(
                    calculated.finalRoundedDamage(), request.attacker());
        } finally {
            Deque<DamageResult> calculations = applying.get(key);
            if (calculations != null) {
                calculations.poll();
                if (calculations.isEmpty()) applying.remove(key);
            }
        }
        double healthDamage = Math.max(0.0,
                healthBefore - Math.max(0.0, request.target().getHealth()));
        double shieldDamage = Math.max(0.0,
                shieldBefore - Math.max(0.0, request.target().getAbsorptionAmount()));
        double effectiveLifeStealEfficiency =
                DamageCalculator.effectiveLifeStealEfficiency(
                        request.damageType(), request.damageKind(),
                        request.lifeStealEfficiency());
        double lifeSteal = StatCalculator.lifeSteal(
                healthDamage,
                playerManager.getPlayerData(request.attacker()).getStats()
                        .get(StatType.LIFESTEAL_PERCENT),
                effectiveLifeStealEfficiency,
                request.healingReductionPercent());
        double appliedHealing = healAttacker(request.attacker(), lifeSteal);
        return new DamageApplicationResult(
                calculated, true, shieldDamage, healthDamage, appliedHealing);
    }

    public DamageApplicationResult applyMob(
            LivingEntity attacker,
            LivingEntity target,
            MobDefinition definition,
            UUID castId
    ) {
        MobStatsDefinition sourceStats = definition.stats();
        MobBasicAttackValues values = new MobBasicAttackValues(definition);
        Stats targetStats = target instanceof Player player
                ? playerManager.getPlayerData(player).getStats() : new Stats();
        MobStatsDefinition targetMobStats = mobStatsResolver.apply(target);
        double equipmentDefense = targetMobStats == null
                ? equipmentDefense(target, values.damageType()) : 0.0;
        double defense = switch (values.damageType()) {
            case PHYSICAL -> StatCalculator.defense(
                    equipmentDefense + (targetMobStats == null
                            ? 0 : targetMobStats.physicalDefense()),
                    targetStats.get(StatType.PHYSICAL_DEFENSE_FLAT),
                    targetStats.get(StatType.PHYSICAL_DEFENSE_PERCENT));
            case MAGICAL -> StatCalculator.defense(
                    equipmentDefense + (targetMobStats == null
                            ? 0 : targetMobStats.magicalDefense()),
                    targetStats.get(StatType.MAGICAL_DEFENSE_FLAT),
                    targetStats.get(StatType.MAGICAL_DEFENSE_PERCENT));
            case TRUE -> 0;
        };
        boolean critical = values.criticalAllowed()
                && criticalResolver.resolve(
                attacker.getUniqueId(), castId, sourceStats.criticalChance(),
                () -> ThreadLocalRandom.current().nextDouble());
        DamageResult calculated = DamageCalculator.calculate(
                new DamageCalculator.Input(
                        values.damageType(), DamageMode.PVE,
                        DamageKind.NORMAL_ATTACK, values.attackPower(),
                        values.fixedDamage(), values.coefficient(),
                        0, 0, critical, sourceStats.criticalDamage(),
                        defense, 0, 0, 0,
                        StatCalculator.DEFAULT_DEFENSE_CONSTANT,
                        new double[]{StatCalculator.saturatedAdd(
                                targetStats.get(StatType.DAMAGE_REDUCTION_PERCENT),
                                targetMobStats == null
                                        ? 0 : targetMobStats.damageReduction())},
                        1, target.getAbsorptionAmount(), target.getHealth(),
                        0, 0, 0));
        return applyCalculated(attacker, target, calculated);
    }

    public void setMobStatsResolver(
            Function<LivingEntity, MobStatsDefinition> resolver
    ) {
        mobStatsResolver = resolver == null ? ignored -> null : resolver;
        snapshotResolver.setMobStatsResolver(resolver);
    }

    DamageCalculationSnapshot resolveSnapshot(
            DamageRequest request,
            boolean critical
    ) {
        return snapshotResolver.resolve(request, critical);
    }

    private DamageApplicationResult applyCalculated(
            LivingEntity attacker,
            LivingEntity target,
            DamageResult calculated
    ) {
        if (calculated.finalRoundedDamage() <= 0 || !target.isValid()) {
            return new DamageApplicationResult(calculated, false, 0, 0, 0);
        }
        double healthBefore = target.getHealth();
        double shieldBefore = target.getAbsorptionAmount();
        DamageKey key = new DamageKey(attacker.getUniqueId(), target.getUniqueId());
        applying.computeIfAbsent(key, ignored -> new ArrayDeque<>()).push(calculated);
        try {
            target.damage(calculated.finalRoundedDamage(), attacker);
        } finally {
            Deque<DamageResult> calculations = applying.get(key);
            if (calculations != null) {
                calculations.poll();
                if (calculations.isEmpty()) applying.remove(key);
            }
        }
        return new DamageApplicationResult(
                calculated, true,
                Math.max(0, shieldBefore - Math.max(0, target.getAbsorptionAmount())),
                Math.max(0, healthBefore - Math.max(0, target.getHealth())), 0);
    }

    public boolean isApplying(Player attacker, LivingEntity target) {
        return isApplying((LivingEntity) attacker, target);
    }

    public boolean isApplying(LivingEntity attacker, LivingEntity target) {
        Deque<DamageResult> calculations = applying.get(new DamageKey(
                attacker.getUniqueId(), target.getUniqueId()));
        return calculations != null && !calculations.isEmpty();
    }

    public DamageResult currentCalculation(Player attacker, LivingEntity target) {
        Deque<DamageResult> calculations = applying.get(new DamageKey(
                attacker.getUniqueId(), target.getUniqueId()));
        return calculations == null ? null : calculations.peek();
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void removeBukkitMitigation(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker)
                || !(event.getEntity() instanceof LivingEntity target)
                || !isApplying(attacker, target)) {
            return;
        }
        for (DamageModifier modifier : DamageModifier.values()) {
            if (DamageEventApplicationPolicy.replacesModifier(modifier.name())
                    && event.isApplicable(modifier)) {
                event.setDamage(modifier, 0.0);
            }
        }
        double damageBeforeAbsorption = 0.0;
        for (DamageModifier modifier : DamageModifier.values()) {
            if (modifier != DamageModifier.ABSORPTION
                    && event.isApplicable(modifier)) {
                damageBeforeAbsorption += event.getDamage(modifier);
            }
        }
        if (event.isApplicable(DamageModifier.ABSORPTION)) {
            event.setDamage(DamageModifier.ABSORPTION,
                    DamageEventApplicationPolicy.absorptionModifier(
                            damageBeforeAbsorption,
                            target.getAbsorptionAmount()));
        }
    }

    public void clear() {
        applying.clear();
        criticalResolver.clear();
    }

    private boolean resolveCritical(DamageRequest request, Stats stats) {
        if (!request.criticalAllowed()) return false;
        return criticalResolver.resolve(
                request.attacker().getUniqueId(), request.castId(),
                stats.get(StatType.CRITICAL_CHANCE_PERCENT),
                () -> ThreadLocalRandom.current().nextDouble());
    }

    private double equipmentDefense(LivingEntity target, DamageType type) {
        EntityEquipment equipment = target.getEquipment();
        if (equipment == null) return 0.0;
        double total = 0.0;
        for (ItemStack item : equipment.getArmorContents()) {
            if (itemManager.getItem(itemManager.getItemId(item)) instanceof Armor armor) {
                total += type == DamageType.MAGICAL
                        ? armor.getMagicalDefense() : armor.getPhysicalDefense();
            }
        }
        return total;
    }

    private static double[] append(double[] values, double value) {
        double[] result = new double[values.length + 1];
        System.arraycopy(values, 0, result, 0, values.length);
        result[values.length] = value;
        return result;
    }

    private static double healAttacker(Player attacker, double requested) {
        if (requested <= 0.0 || attacker.isDead()) return 0.0;
        var maximumHealth = attacker.getAttribute(Attribute.MAX_HEALTH);
        if (maximumHealth == null) return 0.0;
        double before = attacker.getHealth();
        double after = Math.min(maximumHealth.getValue(), before + requested);
        attacker.setHealth(after);
        return Math.max(0.0, after - before);
    }

    private record DamageKey(UUID attackerId, UUID targetId) {
    }

    private record MobBasicAttackValues(
            DamageType damageType,
            double attackPower,
            double fixedDamage,
            double coefficient,
            boolean criticalAllowed
    ) {
        MobBasicAttackValues(MobDefinition definition) {
            this(
                    definition.basicAttack().damageType(),
                    switch (definition.basicAttack().damageType()) {
                        case PHYSICAL -> definition.stats().physicalAttack();
                        case MAGICAL -> definition.stats().magicalAttack();
                        case TRUE -> 0;
                    },
                    definition.basicAttack().fixedDamage(),
                    definition.basicAttack().coefficient(),
                    definition.basicAttack().criticalAllowed());
        }
    }
}
