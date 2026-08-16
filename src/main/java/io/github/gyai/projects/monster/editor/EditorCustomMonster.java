package io.github.gyai.projects.monster.editor;

import io.github.gyai.projects.combat.damage.DamageApplicationResult;
import io.github.gyai.projects.combat.damage.DamageService;
import io.github.gyai.projects.ability.AbilityRuntime;
import io.github.gyai.projects.combat.skill.CcResistanceProfile;
import io.github.gyai.projects.combat.skill.CrowdControlManager;
import io.github.gyai.projects.manager.MonsterManager;
import io.github.gyai.projects.model.MonsterStats;
import io.github.gyai.projects.monster.CustomMonster;
import io.github.gyai.projects.monster.MonsterData;
import io.github.gyai.projects.monster.MonsterRank;
import io.github.gyai.projects.status.StatusEffectManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class EditorCustomMonster extends CustomMonster {
    private final DamageService damageService;
    private final MobAppearanceApplier appearanceApplier;
    private final AssignedAbilityCaster abilityCaster;
    private MobDefinition definition;
    private MonsterData currentData;
    private long nextTargetRefreshTick;
    private long nextAttackTick;
    private boolean aiPaused;
    private UUID neutralTargetId;
    private AbilityRuntime.Cast activeAbilityCast;
    private UUID activeAbilityTargetId;

    public EditorCustomMonster(
            JavaPlugin plugin,
            MobDefinition definition,
            LivingEntity entity,
            Location spawnLocation,
            BossBar bossBar,
            CrowdControlManager crowdControlManager,
            StatusEffectManager statusEffectManager,
            DamageService damageService,
            MobAppearanceApplier appearanceApplier,
            AssignedAbilityCaster abilityCaster
    ) {
        super(plugin, toMonsterData(definition), entity, spawnLocation,
                bossBar, crowdControlManager, statusEffectManager);
        this.definition = definition;
        currentData = toMonsterData(definition);
        this.damageService = damageService;
        this.appearanceApplier = appearanceApplier;
        this.abilityCaster = Objects.requireNonNull(
                abilityCaster, "abilityCaster");
    }

    @Override
    public void tick() {
        if (!isValid()) return;
        clearFinishedAbilityCast();
        long tick = plugin.getServer().getCurrentTick();
        if (entity instanceof Mob mob) {
            mob.setAware(!aiPaused);
            entity.setCustomNameVisible(
                    definition.nameplateMode() == MobDefinition.NameplateMode.ALWAYS
                            || definition.nameplateMode()
                            == MobDefinition.NameplateMode.COMBAT_ONLY
                            && mob.getTarget() != null);
            if (aiPaused || definition.ai().preset() == MobAiDefinition.Preset.PASSIVE) {
                cancelActiveAbility();
                mob.setTarget(null);
                return;
            }
            if (definition.ai().preset() == MobAiDefinition.Preset.NEUTRAL
                    && neutralTarget() == null) {
                mob.setTarget(null);
            }
            if (returnHomeIfNeeded(mob)) return;
            if (tick >= nextTargetRefreshTick) {
                nextTargetRefreshTick = tick + Math.max(1,
                        Math.round(definition.ai().targetRefreshSeconds() * 20));
                mob.setTarget(definition.ai().preset() == MobAiDefinition.Preset.NEUTRAL
                        ? neutralTarget() : selectTarget());
            }
            LivingEntity target = mob.getTarget();
            cancelAbilityForChangedTarget(target);
            if (target != null && validTarget(target)
                    && target.getLocation().distanceSquared(entity.getLocation())
                    <= square(definition.ai().attackRange())
                    && tick >= nextAttackTick
                    && activeAbilityCast == null) {
                useAttackSlot(target, tick);
            }
        }
    }

    public MobDefinition definition() {
        return definition;
    }

    @Override
    public void handleDamage(EntityDamageEvent event) {
        if (definition.ai().preset() != MobAiDefinition.Preset.NEUTRAL
                || !(event instanceof EntityDamageByEntityEvent byEntity)) return;
        Player attacker = null;
        if (byEntity.getDamager() instanceof Player player) attacker = player;
        if (byEntity.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) attacker = player;
        if (attacker != null && validTarget(attacker)) {
            neutralTargetId = attacker.getUniqueId();
        }
    }

    public boolean applyDefinition(MobDefinition updated) {
        if (!entity.getType().name().equals(updated.entityType())) return false;
        cancelActiveAbility();
        double oldMaximum = Math.max(1, currentData.stats().maxHealth());
        double healthRatio = Math.clamp(entity.getHealth() / oldMaximum, 0, 1);
        definition = updated;
        currentData = toMonsterData(updated);
        applyStatsAndAppearance(healthRatio);
        return true;
    }

    public void initializeEntity() {
        applyStatsAndAppearance(1);
    }

    public void setAiPaused(boolean value) {
        aiPaused = value;
        if (value) cancelActiveAbility();
        if (entity instanceof Mob mob) {
            mob.setAware(!value);
            if (value) mob.setTarget(null);
        }
    }

    public boolean aiPaused() {
        return aiPaused;
    }

    public Location homeLocation() {
        return spawnLocation.clone();
    }

    public void restoreTarget(LivingEntity target) {
        if (!(entity instanceof Mob mob) || target == null || !target.isValid()) return;
        mob.setTarget(target);
        if (definition.ai().preset() == MobAiDefinition.Preset.NEUTRAL
                && target instanceof Player player) {
            neutralTargetId = player.getUniqueId();
        }
        nextTargetRefreshTick = plugin.getServer().getCurrentTick()
                + Math.max(1, Math.round(
                definition.ai().targetRefreshSeconds() * 20));
    }

    @Override
    public MonsterData getData() {
        return currentData;
    }

    private void applyStatsAndAppearance(double healthRatio) {
        setAttribute(Attribute.MAX_HEALTH, definition.stats().maxHealth());
        setAttribute(Attribute.MOVEMENT_SPEED,
                Math.clamp(definition.stats().movementSpeed() * .25, .01, 1));
        setAttribute(Attribute.FOLLOW_RANGE, definition.ai().chaseRange());
        var maximumHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        double effectiveMaximum = maximumHealth == null
                ? entity.getHealth() : maximumHealth.getValue();
        entity.setHealth(Math.max(.001,
                Math.min(effectiveMaximum, effectiveMaximum * healthRatio)));
        entity.setPersistent(false);
        entity.setRemoveWhenFarAway(false);
        entity.setCanPickupItems(false);
        appearanceApplier.apply(entity, definition);
    }

    private boolean returnHomeIfNeeded(Mob mob) {
        if (!definition.ai().returnHome()) return false;
        if (!entity.getWorld().equals(spawnLocation.getWorld())) {
            cancelActiveAbility();
            mob.setTarget(null);
            entity.teleport(spawnLocation);
            if (definition.ai().resetHealthOnReturn()) {
                var maximum = entity.getAttribute(Attribute.MAX_HEALTH);
                entity.setHealth(maximum == null
                        ? entity.getHealth() : maximum.getValue());
            }
            return true;
        }
        if (entity.getLocation().distanceSquared(spawnLocation)
                <= square(definition.ai().leashRange())) {
            return false;
        }
        mob.setTarget(null);
        cancelActiveAbility();
        entity.teleport(spawnLocation);
        if (definition.ai().resetHealthOnReturn()) {
            entity.setHealth(definition.stats().maxHealth());
        }
        return true;
    }

    private LivingEntity selectTarget() {
        var candidates = entity.getWorld().getPlayers().stream()
                .filter(this::validTarget)
                .filter(player -> player.getLocation().distanceSquared(entity.getLocation())
                        <= square(definition.ai().aggroRange()));
        Comparator<Player> comparator = switch (definition.ai().targetPriority()) {
            case NEAREST -> Comparator.comparingDouble(player ->
                    player.getLocation().distanceSquared(entity.getLocation()));
            case LOWEST_HEALTH -> Comparator.comparingDouble(Player::getHealth);
        };
        return candidates.min(comparator).orElse(null);
    }

    private Player neutralTarget() {
        if (neutralTargetId == null) return null;
        Player player = plugin.getServer().getPlayer(neutralTargetId);
        if (player == null || !validTarget(player)
                || !player.getWorld().equals(entity.getWorld())
                || player.getLocation().distanceSquared(entity.getLocation())
                > square(definition.ai().chaseRange())) {
            neutralTargetId = null;
            return null;
        }
        return player;
    }

    private boolean validTarget(LivingEntity target) {
        return target instanceof Player player && player.isOnline()
                && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
                && player.getGameMode() != GameMode.CREATIVE;
    }

    private void attack(LivingEntity target, long tick) {
        scheduleNextAttack(tick);
        DamageApplicationResult result = damageService.applyMob(
                entity, target, definition, UUID.randomUUID());
        if (result.healthDamage() <= 0 || definition.basicAttack().knockback() <= 0) {
            return;
        }
        Vector direction = target.getLocation().toVector()
                .subtract(entity.getLocation().toVector()).setY(.15);
        if (direction.lengthSquared() > .0001) {
            target.setVelocity(target.getVelocity().add(direction.normalize()
                    .multiply(definition.basicAttack().knockback())));
        }
    }

    private void useAttackSlot(LivingEntity target, long tick) {
        if (definition.abilityIds().isEmpty()) {
            attack(target, tick);
            return;
        }
        scheduleNextAttack(tick);
        try {
            AbilityRuntime.Cast cast = abilityCaster.cast(
                    entity, definition, (Player) target);
            if (cast != null && cast.isActive()) {
                activeAbilityCast = cast;
                activeAbilityTargetId = target.getUniqueId();
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning(
                    "Editor Mob Ability発動に失敗しました: "
                            + definition.id() + " ("
                            + exception.getClass().getSimpleName() + ")");
        }
    }

    private void scheduleNextAttack(long tick) {
        double attacksPerSecond = Math.max(.05, definition.stats().attackSpeed());
        nextAttackTick = tick + Math.max(1, Math.round(
                definition.basicAttack().intervalSeconds() * 20 / attacksPerSecond));
    }

    private void clearFinishedAbilityCast() {
        if (activeAbilityCast != null && !activeAbilityCast.isActive()) {
            activeAbilityCast = null;
            activeAbilityTargetId = null;
        }
    }

    private void cancelAbilityForChangedTarget(LivingEntity target) {
        if (activeAbilityCast == null) return;
        if (target == null || !validTarget(target)
                || !target.getUniqueId().equals(activeAbilityTargetId)) {
            cancelActiveAbility();
        }
    }

    private void cancelActiveAbility() {
        if (activeAbilityCast != null) {
            activeAbilityCast.cancel(AbilityRuntime.CancelReason.EXPLICIT);
            activeAbilityCast = null;
            activeAbilityTargetId = null;
        }
    }

    @Override
    public void handleDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        cancelActiveAbility();
        super.handleDeath(event);
    }

    @Override
    public void remove() {
        cancelActiveAbility();
        super.remove();
    }

    private void setAttribute(Attribute attribute, double value) {
        var instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private static double square(double value) {
        return value * value;
    }

    private static MonsterData toMonsterData(MobDefinition definition) {
        MobStatsDefinition stats = definition.stats();
        return new MonsterData(
                definition.id(), definition.displayName(),
                org.bukkit.entity.EntityType.valueOf(definition.entityType()),
                new MonsterStats(
                        stats.maxHealth(), stats.physicalAttack(),
                        Math.clamp(stats.movementSpeed() * .25, .01, 1),
                        0, definition.ai().chaseRange(),
                        definition.appearance().scale()),
                definition.level(), switch (definition.category()) {
                    case NORMAL -> MonsterRank.NORMAL;
                    case ELITE -> MonsterRank.ELITE;
                    case BOSS -> MonsterRank.BOSS;
                },
                new CcResistanceProfile(Set.of(), 1, 1));
    }

    @FunctionalInterface
    public interface AssignedAbilityCaster {
        AbilityRuntime.Cast cast(
                LivingEntity source,
                MobDefinition definition,
                Player target
        );
    }
}
