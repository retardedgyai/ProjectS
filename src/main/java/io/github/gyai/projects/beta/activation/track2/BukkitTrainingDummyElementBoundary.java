package io.github.gyai.projects.beta.activation.track2;

import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageMode;
import io.github.gyai.projects.combat.damage.DamageOffenseSnapshot;
import io.github.gyai.projects.combat.damage.DamageRequest;
import io.github.gyai.projects.combat.damage.DamageService;
import io.github.gyai.projects.combat.damage.DamageType;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Concrete Bukkit boundary. Merely constructing it performs no registration;
 * scheduling begins only after the module has passed all Runtime gates.
 */
public final class BukkitTrainingDummyElementBoundary implements TrainingDummyElementBoundary {
    private static final int MAXIMUM_VISUAL_RATE_KEYS = 128;
    private static final long VISUAL_RATE_MILLIS = 500L;
    private static final double MAXIMUM_VIEW_DISTANCE_SQUARED = 32.0 * 32.0;

    private final JavaPlugin plugin;
    private final TrainingDummyManager dummyManager;
    private final DamageService damageService;
    private final BoundedVisualRateLimiter visualRateLimiter =
            new BoundedVisualRateLimiter(MAXIMUM_VISUAL_RATE_KEYS, VISUAL_RATE_MILLIS);

    public BukkitTrainingDummyElementBoundary(
            JavaPlugin plugin,
            TrainingDummyManager dummyManager,
            DamageService damageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dummyManager = Objects.requireNonNull(dummyManager, "dummyManager");
        this.damageService = Objects.requireNonNull(damageService, "damageService");
    }

    @Override
    public boolean isLiveTrainingDummy(UUID targetId) {
        Entity entity = targetId == null ? null : Bukkit.getEntity(targetId);
        return entity instanceof LivingEntity && entity.isValid()
                && dummyManager.isTrainingDummy(entity);
    }

    @Override
    public int targetRuntimeId(UUID targetId) {
        Entity entity = targetId == null ? null : Bukkit.getEntity(targetId);
        return entity != null && entity.isValid() && dummyManager.isTrainingDummy(entity)
                ? entity.getEntityId() : -1;
    }

    @Override
    public List<UUID> nearbyTrainingDummies(UUID centerId, double radius, int limit) {
        if (centerId == null || !Double.isFinite(radius) || radius < 0
                || limit < 1 || limit > TrainingDummyElementRuntime.MAXIMUM_NEARBY_DUMMIES) {
            return List.of();
        }
        Entity center = Bukkit.getEntity(centerId);
        if (!(center instanceof LivingEntity) || !dummyManager.isTrainingDummy(center)) {
            return List.of();
        }
        ArrayList<UUID> result = new ArrayList<>();
        for (Entity entity : center.getWorld().getNearbyEntities(
                center.getLocation(), radius, radius, radius)) {
            if (result.size() == limit) break;
            if (entity instanceof LivingEntity && entity.isValid()
                    && dummyManager.isTrainingDummy(entity)) {
                result.add(entity.getUniqueId());
            }
        }
        return List.copyOf(result);
    }

    @Override
    public void applySecondaryDamage(SecondaryDamage damage) {
        Objects.requireNonNull(damage, "damage");
        Player attacker = Bukkit.getPlayer(damage.attackerId());
        Entity entity = Bukkit.getEntity(damage.targetId());
        if (attacker == null || !attacker.isOnline()
                || !(entity instanceof LivingEntity target)
                || entity instanceof Player || !dummyManager.isTrainingDummy(entity)) {
            return;
        }
        DamageType type = damage.metadata().hasTag(AttackTag.MAGIC)
                ? DamageType.MAGICAL : DamageType.PHYSICAL;
        damageService.apply(DamageRequest.builder(attacker, target)
                .skillId(null)
                .castId(UUID.nameUUIDFromBytes(damage.hitId().getBytes(StandardCharsets.UTF_8)))
                .damageType(type)
                .damageKind(DamageKind.DIRECT_SKILL)
                .mode(DamageMode.PVE)
                .fixedDamage(damage.amount())
                .coefficient(0.0)
                .criticalAllowed(false)
                .offenseSnapshot(new DamageOffenseSnapshot(
                        damage.amount(), false, 1.0))
                .lifeStealEfficiency(0.0)
                .areaDamage(damage.origin()
                        == io.github.gyai.projects.combat.element.ice.IceElementEngine.DamageOrigin.AUTOMATIC_SECONDARY)
                .attackMetadata(damage.metadata())
                .build());
    }

    @Override
    public synchronized void publishVisual(VisualEvent event) {
        Objects.requireNonNull(event, "event");
        Entity entity = Bukkit.getEntity(event.targetId());
        if (!(entity instanceof LivingEntity target) || !dummyManager.isTrainingDummy(entity)) return;
        for (Player viewer : target.getWorld().getPlayers()) {
            if (!viewer.hasPermission("projects.dev")
                    || viewer.getLocation().distanceSquared(target.getLocation())
                    > MAXIMUM_VIEW_DISTANCE_SQUARED) continue;
            if (event.profile() == StagingElementProfile.FIRE) {
                if (event.detonationPulse()) {
                    viewer.spawnParticle(Particle.DUST,
                            target.getLocation().add(0, 1.2, 0), 10,
                            .35, .4, .35, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 96, 32), 1.2f));
                }
                if (!event.compatibleClient() && admitFallback(viewer.getUniqueId(),
                        event.targetId(), event.occurredAtMillis())) {
                    viewer.sendActionBar(Component.text(event.state()));
                }
                continue;
            }
            if (!admitFallback(viewer.getUniqueId(), event.targetId(),
                    event.occurredAtMillis())) continue;
            viewer.spawnParticle(Particle.DUST, target.getLocation().add(0, 1.2, 0),
                    6, .25, .35, .25, 0,
                    new Particle.DustOptions(Color.fromRGB(96, 192, 255), 1.0f));
            viewer.sendActionBar(Component.text("[STAGING ICE] " + event.state()));
        }
    }

    private boolean admitFallback(UUID viewerId, UUID targetId, long occurredAtMillis) {
        String key = viewerId + ":" + targetId;
        return visualRateLimiter.admit(key, occurredAtMillis);
    }

    @Override
    public Cancellable scheduleCleanup(Runnable task, long periodMillis) {
        Objects.requireNonNull(task, "task");
        if (periodMillis < 50L) throw new IllegalArgumentException("period too short");
        long ticks = Math.max(1L, periodMillis / 50L);
        BukkitTask scheduled = Bukkit.getScheduler().runTaskTimer(plugin, task, ticks, ticks);
        return new Cancellable() {
            @Override
            public void cancel() {
                scheduled.cancel();
                synchronized (BukkitTrainingDummyElementBoundary.this) {
                    visualRateLimiter.clear();
                }
            }

            @Override
            public boolean cancelled() {
                return scheduled.isCancelled();
            }
        };
    }
}
