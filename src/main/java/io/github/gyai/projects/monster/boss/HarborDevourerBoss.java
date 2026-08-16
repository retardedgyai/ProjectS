package io.github.gyai.projects.monster.boss;

import io.github.gyai.projects.combat.damage.DamageApplicationResult;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageService;
import io.github.gyai.projects.combat.damage.DamageType;
import io.github.gyai.projects.ability.AbilityDefinition;
import io.github.gyai.projects.ability.AbilityRuntime;
import io.github.gyai.projects.ability.BossAbilityCaster;
import io.github.gyai.projects.ability.BossAbilityDefinitions;
import io.github.gyai.projects.combat.skill.CrowdControlManager;
import io.github.gyai.projects.combat.skill.HardControlRemovalReason;
import io.github.gyai.projects.combat.skill.HardControlState;
import io.github.gyai.projects.combat.skill.HardControlType;
import io.github.gyai.projects.combat.telegraph.TelegraphGeometry;
import io.github.gyai.projects.combat.telegraph.TelegraphInstance;
import io.github.gyai.projects.combat.telegraph.TelegraphRequest;
import io.github.gyai.projects.manager.TelegraphManager;
import io.github.gyai.projects.monster.CustomMonster;
import io.github.gyai.projects.monster.MonsterData;
import io.github.gyai.projects.monster.editor.MobStatsDefinition;
import io.github.gyai.projects.status.StatusEffectManager;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class HarborDevourerBoss extends CustomMonster {
    public static final String MONSTER_ID = "harbor_devourer_grohm";
    public static final String BREAKWATER_SLAM_ID = "breakwater_slam";
    public static final String HULLBREAKER_CHARGE_ID = "hullbreaker_charge";
    public static final String DEEP_TIDE_SHOCKWAVE_ID =
            "deep_tide_shockwave";

    private static final Particle.DustOptions RED_DUST =
            new Particle.DustOptions(Color.fromRGB(190, 35, 45), 1.35f);
    private static final Particle.DustOptions PURPLE_DUST =
            new Particle.DustOptions(Color.fromRGB(130, 35, 180), 1.5f);
    private static final AbilityDefinition BASIC_ATTACK_ABILITY =
            BossAbilityDefinitions.grohmBasicAttack();

    private final Ravager ravager;
    private final Settings settings;
    private final TelegraphManager telegraphManager;
    private final DamageService damageService;
    private final MobStatsDefinition damageStats;
    private final BossAbilityCaster abilityCaster;
    private final Set<BukkitTask> actionTasks = new HashSet<>();
    private final Set<UUID> activeTelegraphs = new HashSet<>();
    private boolean phaseTwo;
    private boolean activeAction;
    private ActionType activeActionType = ActionType.NONE;
    private ChargeContext activeCharge;
    private int noTargetTicks;
    private long nextSlamTick;
    private long nextChargeTick;

    public HarborDevourerBoss(
            JavaPlugin plugin,
            MonsterData data,
            Ravager entity,
            Location spawnLocation,
            BossBar bossBar,
            Settings settings,
            CrowdControlManager crowdControlManager,
            StatusEffectManager statusEffectManager,
            TelegraphManager telegraphManager,
            DamageService damageService,
            BossAbilityCaster abilityCaster
    ) {
        super(
                plugin,
                data,
                entity,
                spawnLocation,
                bossBar,
                crowdControlManager,
                statusEffectManager);
        this.ravager = entity;
        this.settings = settings;
        this.telegraphManager = telegraphManager;
        this.damageService = Objects.requireNonNull(
                damageService, "damageService");
        this.abilityCaster = Objects.requireNonNull(
                abilityCaster, "abilityCaster");
        this.damageStats = new MobStatsDefinition(
                data.stats().maxHealth(),
                data.stats().attackDamage(),
                0.0,
                0.0,
                0.0,
                data.stats().movementSpeed(),
                1.0,
                0.0,
                1.0,
                0.0);
        initializeCooldowns();
    }

    @Override
    public void tick() {
        pruneCompletedTasks();
        activeTelegraphs.removeIf(
                id -> telegraphManager.get(id).isEmpty());
        if (!isValid()) {
            bossBar.removeAll();
            return;
        }

        updateBossBar();
        if (isOutsideLeash()) {
            resetBoss();
            return;
        }
        HardControlType hardControl = getHardControlType();
        if (hardControl == HardControlType.STUN
                || hardControl == HardControlType.FEAR
                || hardControl == HardControlType.CHARM) {
            return;
        }

        Player target = findNearestTarget();
        if (target == null) {
            ravager.setTarget(null);
            noTargetTicks += settings.managerTickInterval();
            if (noTargetTicks >= settings.resetAfterTicks()) {
                resetBoss();
            }
            return;
        }

        noTargetTicks = 0;
        ravager.setTarget(target);
        checkPhaseTransition(ravager.getHealth());
        if (activeAction) {
            return;
        }

        long currentTick = plugin.getServer().getCurrentTick();
        double distanceSquared = target.getLocation().distanceSquared(ravager.getLocation());
        if (hardControl != HardControlType.ROOT
                && currentTick >= nextChargeTick
                && distanceSquared >= 36.0) {
            startHullbreakerCharge(target);
        } else if (currentTick >= nextSlamTick && distanceSquared <= 49.0) {
            startBreakwaterSlam();
        }
    }

    @Override
    public void handleDamage(EntityDamageEvent event) {
        if (event.isCancelled() || event.getFinalDamage() <= 0.0 || phaseTwo) {
            return;
        }
        double predictedHealth = Math.max(0.0, ravager.getHealth() - event.getFinalDamage());
        checkPhaseTransition(predictedHealth);
    }

    /** Returns whether the DamageService is applying damage for this exact hit. */
    public boolean isApplyingDamage(
            org.bukkit.entity.LivingEntity attacker,
            org.bukkit.entity.LivingEntity target
    ) {
        return damageService.isApplying(attacker, target);
    }

    /** Routes one unmanaged Ravager basic hit through the shared ability runtime. */
    public boolean applyBasicAttack(Player target) {
        if (!isValid()
                || !isEligiblePlayer(target)) {
            return false;
        }
        AbilityRuntime.Cast cast = abilityCaster.cast(
                ravager, target, BASIC_ATTACK_ABILITY);
        return cast != null && cast.state() == AbilityRuntime.State.COMPLETED;
    }

    /** Bounded dev reset entry; the live boss remains spawned. */
    public boolean reset() {
        if (!isValid()) {
            return false;
        }
        resetBoss();
        return true;
    }

    @Override
    public void handleDeath(EntityDeathEvent event) {
        finishChargeIfActive(ChargeFinishReason.SOURCE_REMOVED);
        cancelActionTasks();
        cancelActiveTelegraphs(
                TelegraphInstance.CancellationReason
                        .SOURCE_REMOVED);
        activeAction = false;
        activeActionType = ActionType.NONE;
        super.handleDeath(event);
    }

    @Override
    public void remove() {
        finishChargeIfActive(ChargeFinishReason.SOURCE_REMOVED);
        cancelActionTasks();
        cancelActiveTelegraphs(
                TelegraphInstance.CancellationReason
                        .SOURCE_REMOVED);
        activeAction = false;
        activeActionType = ActionType.NONE;
        if (ravager.isValid()) {
            ravager.setAI(true);
            ravager.setVelocity(new Vector());
        }
        super.remove();
    }

    @Override
    public void handleHardControlChanged(
            HardControlState previous,
            HardControlState current
    ) {
        if (current == null) {
            if (isValid() && !activeAction) {
                ravager.setAI(true);
            }
            return;
        }
        HardControlType type = current.type();
        if (type == HardControlType.STUN
                || type == HardControlType.FEAR
                || type == HardControlType.CHARM
                || type == HardControlType.ROOT
                && activeActionType == ActionType.CHARGE) {
            interruptActionForControl(type);
        }
    }

    @Override
    public void updateBossBar() {
        super.updateBossBar();
        if (removed || !ravager.isValid()) {
            return;
        }

        double rangeSquared = settings.bossBarRange() * settings.bossBarRange();
        for (Player current : Set.copyOf(bossBar.getPlayers())) {
            if (!current.isValid()
                    || !current.getWorld().equals(ravager.getWorld())
                    || current.getLocation().distanceSquared(ravager.getLocation()) > rangeSquared) {
                bossBar.removePlayer(current);
            }
        }
        for (Player player : ravager.getWorld().getPlayers()) {
            if (player.isValid()
                    && player.getLocation().distanceSquared(ravager.getLocation()) <= rangeSquared) {
                bossBar.addPlayer(player);
            }
        }
    }

    private Player findNearestTarget() {
        double rangeSquared = data.stats().followRange() * data.stats().followRange();
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Player player : ravager.getWorld().getPlayers()) {
            if (!isEligiblePlayer(player)) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(ravager.getLocation());
            if (distance <= rangeSquared && distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private boolean isEligiblePlayer(Player player) {
        return player != null
                && player.isValid()
                && !player.isDead()
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    private boolean isOutsideLeash() {
        return !ravager.getWorld().equals(spawnLocation.getWorld())
                || ravager.getLocation().distanceSquared(spawnLocation)
                >= settings.leashRange() * settings.leashRange();
    }

    private void resetBoss() {
        finishChargeIfActive(ChargeFinishReason.BOSS_RESET);
        cancelActionTasks();
        cancelActiveTelegraphs(
                TelegraphInstance.CancellationReason
                        .BOSS_RESET);
        activeAction = false;
        activeActionType = ActionType.NONE;
        phaseTwo = false;
        currentPhase = 1;
        noTargetTicks = 0;
        bossBar.removeAll();
        bossBar.setColor(BarColor.RED);
        clearManagedEffects(HardControlRemovalReason.BOSS_RESET);

        ravager.setAI(true);
        ravager.setAware(true);
        ravager.setTarget(null);
        ravager.setVelocity(new Vector());
        ravager.setFireTicks(0);
        ravager.teleport(spawnLocation);
        setMovementSpeed(data.stats().movementSpeed());
        if (!ravager.isDead()) {
            ravager.setHealth(data.stats().maxHealth());
        }
        initializeCooldowns();
        updateBossBar();
    }

    private void checkPhaseTransition(double healthAfterDamage) {
        if (phaseTwo
                || healthAfterDamage > data.stats().maxHealth()
                * settings.phaseTwoHealthRatio()) {
            return;
        }
        startPhaseTwo();
    }

    private void startPhaseTwo() {
        finishChargeIfActive(ChargeFinishReason.BOSS_RESET);
        cancelActionTasks();
        cancelActiveTelegraphs(
                TelegraphInstance.CancellationReason
                        .BOSS_RESET);
        phaseTwo = true;
        currentPhase = 2;
        activeAction = true;
        activeActionType = ActionType.PHASE_TRANSITION;
        bossBar.setColor(BarColor.PURPLE);
        setMovementSpeed(data.stats().movementSpeed() * 1.2);
        long currentTick = plugin.getServer().getCurrentTick();
        nextSlamTick = currentTick + cooldownTicks(settings.slamCooldownSeconds());
        nextChargeTick = currentTick + cooldownTicks(settings.chargeCooldownSeconds());
        ravager.setAI(false);
        ravager.setVelocity(new Vector());

        Location center = ravager.getLocation().clone().add(0.0, 1.0, 0.0);
        ravager.getWorld().playSound(
                center, Sound.ENTITY_RAVAGER_ROAR, 2.0f, 0.65f);
        ravager.getWorld().spawnParticle(
                Particle.DUST, center, 70, 1.8, 1.3, 1.8, 0.0, RED_DUST);
        ravager.getWorld().spawnParticle(
                Particle.DUST, center, 70, 1.8, 1.3, 1.8, 0.0, PURPLE_DUST);

        for (Player player : ravager.getWorld().getPlayers()) {
            if (!isEligiblePlayer(player)
                    || player.getLocation().distanceSquared(ravager.getLocation()) > 49.0) {
                continue;
            }
            Vector knockback = horizontalDirection(ravager.getLocation(), player.getLocation());
            player.setVelocity(knockback.multiply(0.6).setY(0.25));
            player.sendMessage("§5グロームが深潮の力を解放した！");
        }

        track(plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (isValid()) {
                restoreAiForCurrentControl();
                activeAction = false;
                activeActionType = ActionType.NONE;
            }
        }, 30L));
    }

    private void startBreakwaterSlam() {
        if (!canUseStationaryAttack()) {
            return;
        }
        activeAction = true;
        activeActionType = ActionType.SLAM;
        ravager.setAI(false);
        ravager.setVelocity(new Vector());
        nextSlamTick = plugin.getServer().getCurrentTick()
                + cooldownTicks(settings.slamCooldownSeconds());
        Location center = ravager.getLocation().clone();
        UUID telegraphId = createFixedTelegraph(
                BREAKWATER_SLAM_ID,
                TelegraphInstance.Shape.CIRCLE,
                center,
                new Vector(0.0, 0.0, 1.0),
                settings.slamRadius(),
                0.0,
                0.0,
                0.0,
                settings.slamWarningTicks());

        BukkitRunnable warning = new BukkitRunnable() {
            private int elapsed;

            @Override
            public void run() {
                if (!isValid()
                        || !activeAction
                        || activeActionType != ActionType.SLAM
                        || !canUseStationaryAttack()
                        || elapsed >= settings.slamWarningTicks()) {
                    cancel();
                    return;
                }
                if (elapsed % 8 == 0) {
                    ravager.getWorld().playSound(
                            ravager.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS,
                            1.0f, 0.65f);
                }
                elapsed += 4;
            }
        };
        track(warning.runTaskTimer(plugin, 0L, 4L));
        track(plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> executeSlam(telegraphId),
                settings.slamWarningTicks()));
    }

    private void executeSlam(UUID telegraphId) {
        if (!isValid()
                || !activeAction
                || activeActionType != ActionType.SLAM
                || !canUseStationaryAttack()
                || !telegraphManager.detonate(telegraphId)) {
            finishAction();
            return;
        }
        activeTelegraphs.remove(telegraphId);
        Location center = telegraphManager.center(telegraphId);
        if (center == null) {
            finishAction();
            return;
        }
        damagePlayersInTelegraph(
                telegraphId,
                settings.slamDamage(),
                1.2,
                0.35);
        ravager.getWorld().playSound(
                center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);

        if (phaseTwo) {
            UUID shockwaveId = createFixedTelegraph(
                    DEEP_TIDE_SHOCKWAVE_ID,
                    TelegraphInstance.Shape.DONUT,
                    center,
                    new Vector(0.0, 0.0, 1.0),
                    8.0,
                    5.0,
                    0.0,
                    0.0,
                    settings.phaseTwoShockwaveWarningTicks());
            track(plugin.getServer().getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> executePhaseTwoShockwave(
                                    shockwaveId),
                            settings
                                    .phaseTwoShockwaveWarningTicks()));
        } else {
            finishAction();
        }
    }

    private void executePhaseTwoShockwave(
            UUID telegraphId
    ) {
        if (!isValid()
                || !activeAction
                || activeActionType != ActionType.SLAM
                || !canUseStationaryAttack()
                || !telegraphManager.detonate(telegraphId)) {
            finishAction();
            return;
        }
        activeTelegraphs.remove(telegraphId);
        Location center = telegraphManager.center(telegraphId);
        if (center != null) {
            damagePlayersInTelegraph(
                    telegraphId,
                    12.0,
                    0.8,
                    0.35);
            ravager.getWorld().playSound(
                    center,
                    Sound.ENTITY_GENERIC_EXPLODE,
                    1.1f,
                    0.9f);
        }
        finishAction();
    }

    private void startHullbreakerCharge(Player target) {
        if (!canUseMovementAttack()) {
            return;
        }
        activeAction = true;
        activeActionType = ActionType.CHARGE;
        ravager.setAI(false);
        ravager.setVelocity(new Vector());
        nextChargeTick = plugin.getServer().getCurrentTick()
                + cooldownTicks(settings.chargeCooldownSeconds());

        Location start = ravager.getLocation().clone();
        Location recordedTarget = target.getLocation().clone();
        Vector direction = recordedTarget.toVector().subtract(start.toVector()).setY(0.0);
        if (direction.lengthSquared() < 0.0001) {
            finishAction();
            return;
        }
        direction.normalize();
        double warningDistance = Math.min(
                settings.chargeDistance(), horizontalDistance(start, recordedTarget));
        UUID telegraphId = createFixedTelegraph(
                HULLBREAKER_CHARGE_ID,
                TelegraphInstance.Shape.LINE,
                start,
                direction,
                0.0,
                0.0,
                4.0,
                warningDistance,
                settings.chargeWarningTicks());
        ChargeContext charge = new ChargeContext(
                telegraphId,
                new ChargeRuntimeGuard());
        activeCharge = charge;

        BukkitRunnable warning = new BukkitRunnable() {
            private int elapsed;

            @Override
            public void run() {
                if (!isValid()
                        || !activeAction
                        || activeActionType != ActionType.CHARGE
                        || !canUseMovementAttack()
                        || elapsed >= settings.chargeWarningTicks()) {
                    cancel();
                    return;
                }
                if (elapsed % 8 == 0) {
                    ravager.getWorld().playSound(
                            ravager.getLocation(), Sound.ENTITY_RAVAGER_STEP,
                            1.1f, 0.65f);
                }
                elapsed += 4;
            }
        };
        track(warning.runTaskTimer(plugin, 0L, 4L));
        track(plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> beginChargeMovement(
                        start, direction, charge),
                settings.chargeWarningTicks()));
    }

    private void beginChargeMovement(
            Location start,
            Vector direction,
            ChargeContext charge
    ) {
        if (!isValid()
                || !activeAction
                || activeActionType != ActionType.CHARGE
                || !canUseMovementAttack()
                || activeCharge != charge
                || !telegraphManager.detonate(
                charge.telegraphId())) {
            finishCharge(
                    ChargeFinishReason.INVALID_STATE,
                    charge);
            return;
        }
        // A no-AI mob does not run vanilla travel and therefore ignores
        // velocity-based movement. Keep travel enabled while preventing the
        // normal Ravager goals from steering the charge away from its line.
        ravager.setAware(false);
        ravager.setAI(true);
        Set<UUID> hitPlayers = new HashSet<>();
        BukkitRunnable movement = new BukkitRunnable() {
            private Location previous =
                    ravager.getLocation().clone();
            private final int maximumTicks = Math.max(
                    8,
                    (int) Math.ceil(
                            settings.chargeDistance()
                                    / 1.35) + 10);

            @Override
            public void run() {
                if (!isValid()
                        || !activeAction
                        || activeActionType != ActionType.CHARGE
                        || activeCharge != charge
                        || !canUseMovementAttack()) {
                    finishCharge(
                            ChargeFinishReason.INVALID_STATE,
                            charge);
                    return;
                }
                Location current = ravager.getLocation();
                if (horizontalDistance(
                        start, current)
                        >= settings.chargeDistance()) {
                    finishCharge(
                            ChargeFinishReason.DISTANCE_REACHED,
                            charge);
                    return;
                }
                if (collidesWithSolidBlock(
                        current, direction)) {
                    finishCharge(
                            ChargeFinishReason.COLLISION,
                            charge);
                    return;
                }
                ChargeRuntimeGuard.StopReason stopReason =
                        charge.guard().observe(
                                horizontalDistance(
                                        previous,
                                        current),
                                maximumTicks);
                previous = current.clone();
                if (stopReason
                        == ChargeRuntimeGuard.StopReason.STUCK) {
                    finishCharge(
                            ChargeFinishReason.STUCK,
                            charge);
                    return;
                }
                if (stopReason
                        == ChargeRuntimeGuard.StopReason.TIMEOUT) {
                    finishCharge(
                            ChargeFinishReason.TIMEOUT,
                            charge);
                    return;
                }

                ravager.setVelocity(direction.clone().multiply(1.35));
                if (charge.guard().particlesAllowed()) {
                    ravager.getWorld().spawnParticle(
                            Particle.CLOUD,
                            current.clone()
                                    .add(0.0, 0.35, 0.0),
                            3,
                            0.6, 0.15, 0.6,
                            0.015);
                }
                for (Player player : ravager.getWorld().getPlayers()) {
                    if (!isEligiblePlayer(player)
                            || hitPlayers.contains(player.getUniqueId())
                            || !isInsideChargeHitbox(current, player.getLocation())) {
                        continue;
                    }
                    hitPlayers.add(player.getUniqueId());
                    DamageApplicationResult result = applyDamage(
                            player,
                            charge.telegraphId(),
                            DamageKind.DIRECT_SKILL,
                            settings.chargeDamage());
                    if (result == null
                            || !(result.shieldDamage() > 0.0
                            || result.healthDamage() > 0.0)) {
                        continue;
                    }
                    player.setVelocity(direction.clone().multiply(1.5).setY(0.25));
                }
            }
        };
        track(movement.runTaskTimer(plugin, 0L, 1L));
    }

    private boolean collidesWithSolidBlock(Location current, Vector direction) {
        Vector side = new Vector(-direction.getZ(), 0.0, direction.getX()).normalize();
        for (double sideOffset : new double[]{-1.0, 0.0, 1.0}) {
            Location probe = current.clone()
                    .add(direction.clone().multiply(1.35))
                    .add(side.clone().multiply(sideOffset));
            for (int y = 0; y <= 2; y++) {
                if (probe.clone().add(0.0, y, 0.0).getBlock().getType().isSolid()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isInsideChargeHitbox(Location boss, Location player) {
        return TelegraphGeometry.containsCircle(
                boss.getX(),
                boss.getY(),
                boss.getZ(),
                2.0,
                3.0,
                player.getX(),
                player.getY(),
                player.getZ());
    }

    private void damagePlayersInTelegraph(
            UUID telegraphId,
            double damage,
            double horizontalKnockback,
            double verticalKnockback
    ) {
        Location center = telegraphManager.center(
                telegraphId);
        if (center == null) {
            return;
        }
        for (Player player : center.getWorld().getPlayers()) {
            if (!isEligiblePlayer(player)
                    || !telegraphManager.contains(
                    telegraphId,
                    player.getLocation())) {
                continue;
            }
            DamageApplicationResult result = applyDamage(
                    player,
                    telegraphId,
                    DamageKind.DIRECT_SKILL,
                    damage);
            if (result == null
                    || !(result.shieldDamage() > 0.0
                    || result.healthDamage() > 0.0)) {
                continue;
            }
            Vector direction = horizontalDirection(center, player.getLocation());
            player.setVelocity(direction.multiply(horizontalKnockback).setY(verticalKnockback));
        }
    }

    private DamageApplicationResult applyDamage(
            Player target,
            UUID castId,
            DamageKind damageKind,
            double fixedDamage
    ) {
        if (!isValid()
                || !isEligiblePlayer(target)
                || !Double.isFinite(fixedDamage)
                || fixedDamage < 0.0) {
            return null;
        }
        DamageProfile profile = damageProfile(damageKind, fixedDamage);
        return damageService.applyMobAbility(
                ravager,
                target,
                damageStats,
                castId,
                profile.damageType(),
                profile.damageKind(),
                profile.fixedDamage(),
                profile.coefficient(),
                profile.criticalAllowed());
    }

    static DamageProfile damageProfile(
            DamageKind damageKind,
            double fixedDamage
    ) {
        Objects.requireNonNull(damageKind, "damageKind");
        if (!Double.isFinite(fixedDamage) || fixedDamage < 0.0) {
            throw new IllegalArgumentException("Boss damage must be finite and non-negative");
        }
        return new DamageProfile(
                DamageType.PHYSICAL,
                damageKind,
                fixedDamage,
                0.0,
                false,
                0.0);
    }

    private void finishAction() {
        if (activeActionType == ActionType.CHARGE
                && activeCharge != null) {
            finishCharge(
                    ChargeFinishReason.INVALID_STATE,
                    activeCharge);
            return;
        }
        if (ravager.isValid()) {
            ravager.setVelocity(new Vector());
            restoreAiForCurrentControl();
        }
        activeAction = false;
        activeActionType = ActionType.NONE;
    }

    private void interruptActionForControl(HardControlType type) {
        finishChargeIfActive(
                ChargeFinishReason.HARD_CONTROL);
        cancelActionTasks();
        cancelActiveTelegraphs(
                TelegraphInstance.CancellationReason
                        .HARD_CONTROL);
        activeAction = false;
        activeActionType = ActionType.NONE;
        if (ravager.isValid()) {
            ravager.setVelocity(new Vector());
            if (type == HardControlType.STUN) {
                ravager.setAI(false);
            } else {
                ravager.setAI(true);
            }
        }
    }

    private void finishChargeIfActive(
            ChargeFinishReason reason
    ) {
        ChargeContext charge = activeCharge;
        if (charge != null) {
            finishCharge(reason, charge);
        }
    }

    private void finishCharge(
            ChargeFinishReason reason,
            ChargeContext charge
    ) {
        ChargeRuntimeGuard.StopReason guardReason =
                reason == ChargeFinishReason.COLLISION
                        ? ChargeRuntimeGuard.StopReason.COLLISION
                        : reason == ChargeFinishReason.STUCK
                        ? ChargeRuntimeGuard.StopReason.STUCK
                        : reason == ChargeFinishReason.TIMEOUT
                        ? ChargeRuntimeGuard.StopReason.TIMEOUT
                        : ChargeRuntimeGuard.StopReason.EXTERNAL;
        if (!charge.guard().finishOnce(guardReason)) {
            return;
        }
        cancelActionTasks();
        TelegraphInstance.CancellationReason
                cancellationReason =
                switch (reason) {
                    case HARD_CONTROL ->
                            TelegraphInstance
                                    .CancellationReason
                                    .HARD_CONTROL;
                    case BOSS_RESET ->
                            TelegraphInstance
                                    .CancellationReason
                                    .BOSS_RESET;
                    case SOURCE_REMOVED ->
                            TelegraphInstance
                                    .CancellationReason
                                    .SOURCE_REMOVED;
                    case INVALID_STATE ->
                            TelegraphInstance
                                    .CancellationReason
                                    .TARGET_INVALID;
                    default ->
                            TelegraphInstance
                                    .CancellationReason.NONE;
                };
        if (cancellationReason
                != TelegraphInstance.CancellationReason.NONE) {
            telegraphManager.cancel(
                    charge.telegraphId(),
                    cancellationReason);
        }
        telegraphManager.removeNow(
                charge.telegraphId());
        activeTelegraphs.remove(
                charge.telegraphId());
        if (ravager.isValid()) {
            ravager.setVelocity(new Vector());
            restoreAiForCurrentControl();
            if (reason == ChargeFinishReason.COLLISION) {
                Location impact = ravager.getLocation()
                        .clone().add(0.0, 0.6, 0.0);
                ravager.getWorld().spawnParticle(
                        Particle.BLOCK,
                        impact,
                        8,
                        0.6, 0.35, 0.6,
                        0.08,
                        impact.clone()
                                .subtract(0.0, 0.7, 0.0)
                                .getBlock()
                                .getBlockData());
                ravager.getWorld().playSound(
                        impact,
                        Sound.ENTITY_RAVAGER_STUNNED,
                        1.0f,
                        0.8f);
            }
        }
        activeAction = false;
        activeActionType = ActionType.NONE;
        if (activeCharge == charge) {
            activeCharge = null;
        }
    }

    private boolean canUseStationaryAttack() {
        HardControlType type = getHardControlType();
        return type == null || type == HardControlType.ROOT;
    }

    private boolean canUseMovementAttack() {
        return getHardControlType() == null;
    }

    private void restoreAiForCurrentControl() {
        ravager.setAware(true);
        ravager.setAI(getHardControlType() != HardControlType.STUN);
    }

    private void setMovementSpeed(double value) {
        AttributeInstance attribute = ravager.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.setBaseValue(value);
        }
    }

    private void initializeCooldowns() {
        long currentTick = plugin.getServer().getCurrentTick();
        nextSlamTick = currentTick + secondsToTicks(settings.slamCooldownSeconds());
        nextChargeTick = currentTick + secondsToTicks(settings.chargeCooldownSeconds());
    }

    private long cooldownTicks(double seconds) {
        double multiplier = phaseTwo ? 0.8 : 1.0;
        return Math.max(1L, Math.round(seconds * 20.0 * multiplier));
    }

    private long secondsToTicks(double seconds) {
        return Math.max(1L, Math.round(seconds * 20.0));
    }

    private void track(BukkitTask task) {
        actionTasks.add(task);
    }

    private void cancelActionTasks() {
        for (BukkitTask task : Set.copyOf(actionTasks)) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        actionTasks.clear();
    }

    private UUID createFixedTelegraph(
            String attackId,
            TelegraphInstance.Shape shape,
            Location center,
            Vector direction,
            double radius,
            double innerRadius,
            double width,
            double length,
            int warningTicks
    ) {
        long startTick =
                plugin.getServer().getCurrentTick();
        TelegraphRequest request = new TelegraphRequest(
                attackId,
                center.getWorld().getUID(),
                center.getWorld().getKey().toString(),
                shape,
                TelegraphInstance.VisualTheme.DAMAGE,
                TelegraphInstance.VisualStyle
                        .GROHM_STONE_TIDE,
                center.getX(),
                center.getY(),
                center.getZ(),
                direction.getX(),
                direction.getZ(),
                radius,
                innerRadius,
                width,
                length,
                startTick,
                startTick + 1L,
                startTick + warningTicks,
                startTick + warningTicks + 5L,
                TelegraphInstance.TrackingMode.FIXED,
                null,
                3.0);
        UUID id = telegraphManager.create(
                ravager, request);
        activeTelegraphs.add(id);
        return id;
    }

    private void cancelActiveTelegraphs(
            TelegraphInstance.CancellationReason reason
    ) {
        for (UUID id : Set.copyOf(activeTelegraphs)) {
            telegraphManager.cancel(id, reason);
        }
        activeTelegraphs.clear();
    }

    private void pruneCompletedTasks() {
        actionTasks.removeIf(task -> task.isCancelled()
                || (!plugin.getServer().getScheduler().isQueued(task.getTaskId())
                && !plugin.getServer().getScheduler().isCurrentlyRunning(task.getTaskId())));
    }

    private static Vector horizontalDirection(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector()).setY(0.0);
        if (direction.lengthSquared() < 0.0001) {
            return new Vector();
        }
        return direction.normalize();
    }

    private static double horizontalDistance(Location first, Location second) {
        return Math.sqrt(horizontalDistanceSquared(first, second));
    }

    private static double horizontalDistanceSquared(Location first, Location second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    public record Settings(
            double leashRange,
            double bossBarRange,
            int resetAfterTicks,
            double phaseTwoHealthRatio,
            double slamCooldownSeconds,
            int slamWarningTicks,
            double slamRadius,
            double slamDamage,
            int phaseTwoShockwaveWarningTicks,
            double chargeCooldownSeconds,
            int chargeWarningTicks,
            double chargeDistance,
            double chargeDamage,
            int managerTickInterval
    ) {
    }

    private enum ActionType {
        NONE,
        PHASE_TRANSITION,
        SLAM,
        CHARGE
    }

    record DamageProfile(
            DamageType damageType,
            DamageKind damageKind,
            double fixedDamage,
            double coefficient,
            boolean criticalAllowed,
            double lifeStealEfficiency
    ) {
    }

    private record ChargeContext(
            UUID telegraphId,
            ChargeRuntimeGuard guard
    ) {
    }

    private enum ChargeFinishReason {
        DISTANCE_REACHED,
        COLLISION,
        STUCK,
        TIMEOUT,
        HARD_CONTROL,
        BOSS_RESET,
        SOURCE_REMOVED,
        INVALID_STATE
    }
}
