package io.github.gyai.projects.monster.boss;

import io.github.gyai.projects.combat.skill.CrowdControlManager;
import io.github.gyai.projects.combat.skill.HardControlRemovalReason;
import io.github.gyai.projects.combat.skill.HardControlState;
import io.github.gyai.projects.combat.skill.HardControlType;
import io.github.gyai.projects.monster.CustomMonster;
import io.github.gyai.projects.monster.MonsterData;
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
import java.util.Set;
import java.util.UUID;

public final class HarborDevourerBoss extends CustomMonster {
    public static final String MONSTER_ID = "harbor_devourer_grohm";
    public static final String BREAKWATER_SLAM_ID = "breakwater_slam";
    public static final String HULLBREAKER_CHARGE_ID = "hullbreaker_charge";

    private static final Particle.DustOptions RED_DUST =
            new Particle.DustOptions(Color.fromRGB(190, 35, 45), 1.35f);
    private static final Particle.DustOptions PURPLE_DUST =
            new Particle.DustOptions(Color.fromRGB(130, 35, 180), 1.5f);

    private final Ravager ravager;
    private final Settings settings;
    private final Set<BukkitTask> actionTasks = new HashSet<>();
    private boolean phaseTwo;
    private boolean activeAction;
    private ActionType activeActionType = ActionType.NONE;
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
            StatusEffectManager statusEffectManager
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
        initializeCooldowns();
    }

    @Override
    public void tick() {
        pruneCompletedTasks();
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

    @Override
    public void handleDeath(EntityDeathEvent event) {
        cancelActionTasks();
        activeAction = false;
        activeActionType = ActionType.NONE;
        super.handleDeath(event);
    }

    @Override
    public void remove() {
        cancelActionTasks();
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
        return player.isValid()
                && !player.isDead()
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    private boolean isOutsideLeash() {
        return !ravager.getWorld().equals(spawnLocation.getWorld())
                || ravager.getLocation().distanceSquared(spawnLocation)
                >= settings.leashRange() * settings.leashRange();
    }

    private void resetBoss() {
        cancelActionTasks();
        activeAction = false;
        activeActionType = ActionType.NONE;
        phaseTwo = false;
        currentPhase = 1;
        noTargetTicks = 0;
        bossBar.removeAll();
        bossBar.setColor(BarColor.RED);
        clearManagedEffects(HardControlRemovalReason.BOSS_RESET);

        ravager.setAI(true);
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
        cancelActionTasks();
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
                drawWarningCircle(settings.slamRadius());
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
                plugin, this::executeSlam, settings.slamWarningTicks()));
    }

    private void executeSlam() {
        if (!isValid()
                || !activeAction
                || activeActionType != ActionType.SLAM
                || !canUseStationaryAttack()) {
            finishAction();
            return;
        }
        Location center = ravager.getLocation();
        damagePlayersInRing(
                center, 0.0, settings.slamRadius(), settings.slamDamage(), 1.2, 0.35);
        ravager.getWorld().spawnParticle(
                Particle.EXPLOSION, center.clone().add(0.0, 0.4, 0.0),
                10, settings.slamRadius() * 0.4, 0.3,
                settings.slamRadius() * 0.4, 0.0);
        ravager.getWorld().playSound(
                center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);

        if (phaseTwo) {
            track(plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (isValid()
                        && activeAction
                        && activeActionType == ActionType.SLAM
                        && canUseStationaryAttack()) {
                    Location secondCenter = ravager.getLocation();
                    drawWarningCircle(8.0);
                    damagePlayersInRing(secondCenter, 5.0, 8.0, 12.0, 0.8, 0.35);
                    ravager.getWorld().spawnParticle(
                            Particle.CLOUD, secondCenter.clone().add(0.0, 0.25, 0.0),
                            55, 4.0, 0.25, 4.0, 0.03);
                    ravager.getWorld().playSound(
                            secondCenter, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 0.9f);
                }
                finishAction();
            }, 10L));
        } else {
            finishAction();
        }
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
                drawWarningLine(start, direction, warningDistance);
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
                () -> beginChargeMovement(start, direction),
                settings.chargeWarningTicks()));
    }

    private void beginChargeMovement(Location start, Vector direction) {
        if (!isValid()
                || !activeAction
                || activeActionType != ActionType.CHARGE
                || !canUseMovementAttack()) {
            finishAction();
            return;
        }
        Set<UUID> hitPlayers = new HashSet<>();
        BukkitRunnable movement = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isValid()
                        || !activeAction
                        || activeActionType != ActionType.CHARGE
                        || !canUseMovementAttack()) {
                    cancel();
                    finishAction();
                    return;
                }
                Location current = ravager.getLocation();
                if (horizontalDistance(start, current) >= settings.chargeDistance()
                        || collidesWithSolidBlock(current, direction)) {
                    cancel();
                    finishAction();
                    return;
                }

                ravager.setVelocity(direction.clone().multiply(1.35));
                ravager.getWorld().spawnParticle(
                        Particle.CLOUD, current.clone().add(0.0, 0.35, 0.0),
                        5, 0.7, 0.2, 0.7, 0.02);
                for (Player player : ravager.getWorld().getPlayers()) {
                    if (!isEligiblePlayer(player)
                            || hitPlayers.contains(player.getUniqueId())
                            || !isInsideChargeHitbox(current, player.getLocation())) {
                        continue;
                    }
                    hitPlayers.add(player.getUniqueId());
                    player.damage(settings.chargeDamage(), ravager);
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
        double dx = boss.getX() - player.getX();
        double dz = boss.getZ() - player.getZ();
        double dy = Math.abs(boss.getY() - player.getY());
        return dx * dx + dz * dz <= 4.0 && dy <= 2.75;
    }

    private void damagePlayersInRing(
            Location center,
            double innerRadius,
            double outerRadius,
            double damage,
            double horizontalKnockback,
            double verticalKnockback
    ) {
        double innerSquared = innerRadius * innerRadius;
        double outerSquared = outerRadius * outerRadius;
        for (Player player : center.getWorld().getPlayers()) {
            if (!isEligiblePlayer(player)) {
                continue;
            }
            double distance = horizontalDistanceSquared(center, player.getLocation());
            if ((innerRadius > 0.0 && distance <= innerSquared)
                    || distance > outerSquared
                    || Math.abs(player.getY() - center.getY()) > 3.5) {
                continue;
            }
            player.damage(damage, ravager);
            Vector direction = horizontalDirection(center, player.getLocation());
            player.setVelocity(direction.multiply(horizontalKnockback).setY(verticalKnockback));
        }
    }

    private void drawWarningCircle(double radius) {
        Location center = ravager.getLocation().clone().add(0.0, 0.12, 0.0);
        int points = Math.max(24, (int) Math.ceil(radius * 10.0));
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0 * index / points;
            Location point = center.clone().add(
                    Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            ravager.getWorld().spawnParticle(
                    Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0,
                    phaseTwo ? PURPLE_DUST : RED_DUST);
        }
    }

    private void drawWarningLine(Location start, Vector direction, double distance) {
        for (double step = 0.5; step <= distance; step += 0.55) {
            Location point = start.clone()
                    .add(direction.clone().multiply(step))
                    .add(0.0, 0.12, 0.0);
            ravager.getWorld().spawnParticle(
                    Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, RED_DUST);
        }
    }

    private void finishAction() {
        if (!isValid()) {
            return;
        }
        ravager.setVelocity(new Vector());
        restoreAiForCurrentControl();
        activeAction = false;
        activeActionType = ActionType.NONE;
    }

    private void interruptActionForControl(HardControlType type) {
        cancelActionTasks();
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

    private boolean canUseStationaryAttack() {
        HardControlType type = getHardControlType();
        return type == null || type == HardControlType.ROOT;
    }

    private boolean canUseMovementAttack() {
        return getHardControlType() == null;
    }

    private void restoreAiForCurrentControl() {
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
}
