package io.github.gyai.projects.dummy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.List;

public class TrainingDummyManager {
    public static final long SESSION_TIMEOUT_MILLIS = 5_000L;

    private final JavaPlugin plugin;
    private final NamespacedKey dummyKey;
    private final Set<UUID> dummyIds = new HashSet<>();
    private final Map<SessionKey, TrainingDummySession> sessions = new HashMap<>();
    private final Map<UUID, SessionKey> latestSessionByPlayer = new HashMap<>();
    private final Map<DamageKey, String> skillDamageMarkers = new HashMap<>();
    private final DamageNumberDisplay damageNumberDisplay;
    private BukkitTask sessionTask;
    private final Map<String, DummyType> dummyTypes = new HashMap<>();

    public TrainingDummyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dummyKey = new NamespacedKey(plugin, "training_dummy");
        this.damageNumberDisplay = new DamageNumberDisplay(plugin);
        registerDummyType(new DummyType("training_dummy", "訓練ダミー", org.bukkit.Material.ARMOR_STAND,
                "通常のDPS計測用ダミー"));
    }

    public void start() {
        sessionTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::finishExpiredSessions, 10L, 10L);
    }

    public ArmorStand spawn(Player player) {
        return spawn(player, "training_dummy");
    }

    public ArmorStand spawn(Player player, String typeId) {
        if (!dummyTypes.containsKey(typeId)) {
            return null;
        }
        Location location = findSafeSpawnLocation(player);
        if (location == null) {
            return null;
        }
        DummyType type = dummyTypes.get(typeId);
        ArmorStand dummy = player.getWorld().spawn(location, ArmorStand.class, armorStand -> {
            armorStand.customName(Component.text(type.displayName(), NamedTextColor.GOLD));
            armorStand.setCustomNameVisible(true);
            armorStand.setGravity(false);
            armorStand.setCanPickupItems(false);
            armorStand.setBasePlate(true);
            armorStand.setArms(true);
            armorStand.setPersistent(false);
            armorStand.setFireTicks(0);
            armorStand.getPersistentDataContainer().set(dummyKey, PersistentDataType.BYTE, (byte) 1);
        });
        dummyIds.add(dummy.getUniqueId());
        return dummy;
    }

    public void registerDummyType(DummyType type) {
        dummyTypes.put(type.id(), type);
    }

    public List<DummyType> getDummyTypes() {
        return dummyTypes.values().stream().sorted(java.util.Comparator.comparing(DummyType::id)).toList();
    }

    private Location findSafeSpawnLocation(Player player) {
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() == 0.0) direction.setZ(1.0);
        direction.normalize();
        for (double distance : new double[]{2.0, 3.0, 1.5}) {
            Location candidate = player.getLocation().clone().add(direction.clone().multiply(distance));
            candidate.setX(Math.floor(candidate.getX()) + 0.5);
            candidate.setZ(Math.floor(candidate.getZ()) + 0.5);
            if (candidate.getY() <= player.getWorld().getMinHeight() + 1) continue;
            org.bukkit.block.Block feet = candidate.getBlock();
            org.bukkit.block.Block head = feet.getRelative(org.bukkit.block.BlockFace.UP);
            org.bukkit.block.Block ground = feet.getRelative(org.bukkit.block.BlockFace.DOWN);
            if (feet.isPassable() && head.isPassable() && ground.getType().isSolid()
                    && ground.getType() != org.bukkit.Material.MAGMA_BLOCK) {
                return candidate;
            }
        }
        return null;
    }

    public boolean isTrainingDummy(Entity entity) {
        return entity instanceof ArmorStand
                && entity.getPersistentDataContainer().has(dummyKey, PersistentDataType.BYTE);
    }

    public boolean removeNearest(Player player) {
        ArmorStand nearest = dummyIds.stream()
                .map(plugin.getServer()::getEntity)
                .filter(this::isTrainingDummy)
                .map(ArmorStand.class::cast)
                .filter(dummy -> dummy.getWorld().equals(player.getWorld()))
                .min((left, right) -> Double.compare(
                        left.getLocation().distanceSquared(player.getLocation()),
                        right.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
        if (nearest == null) {
            return false;
        }
        removeDummy(nearest);
        return true;
    }

    public int removeAll() {
        int count = 0;
        for (UUID dummyId : Set.copyOf(dummyIds)) {
            Entity entity = plugin.getServer().getEntity(dummyId);
            if (entity != null && isTrainingDummy(entity)) {
                removeDummy(entity);
                count++;
            } else {
                dummyIds.remove(dummyId);
            }
        }
        return count;
    }

    public int removeNearby(Player player, double radius) {
        double radiusSquared = radius * radius;
        int count = 0;
        for (UUID dummyId : Set.copyOf(dummyIds)) {
            Entity entity = plugin.getServer().getEntity(dummyId);
            if (entity != null && isTrainingDummy(entity)
                    && entity.getWorld().equals(player.getWorld())
                    && entity.getLocation().distanceSquared(player.getLocation()) <= radiusSquared) {
                removeDummy(entity);
                count++;
            }
        }
        return count;
    }

    public int countNearby(Player player, double radius) {
        double radiusSquared = radius * radius;
        return (int) dummyIds.stream()
                .map(plugin.getServer()::getEntity)
                .filter(this::isTrainingDummy)
                .filter(entity -> entity.getWorld().equals(player.getWorld()))
                .filter(entity -> entity.getLocation().distanceSquared(player.getLocation()) <= radiusSquared)
                .count();
    }

    public TrainingDummySession finishPlayerSession(Player player) {
        SessionKey key = latestSessionByPlayer.remove(player.getUniqueId());
        TrainingDummySession session = key == null ? null : sessions.remove(key);
        if (session != null) {
            showFinalResult(session);
        }
        return session;
    }

    public void recordDamage(Player player, ArmorStand dummy, double damage) {
        long now = System.currentTimeMillis();
        SessionKey key = new SessionKey(player.getUniqueId(), dummy.getUniqueId());
        TrainingDummySession session = sessions.computeIfAbsent(
                key, ignored -> new TrainingDummySession(key.playerId(), key.dummyId(), now));
        DamageKey damageKey = new DamageKey(player.getUniqueId(), dummy.getUniqueId());
        String skillId = skillDamageMarkers.remove(damageKey);
        session.recordHit(damage, now, skillId);
        latestSessionByPlayer.put(player.getUniqueId(), key);

        boolean skillDamage = skillId != null;
        NamedTextColor color = skillDamage ? NamedTextColor.AQUA : NamedTextColor.WHITE;
        damageNumberDisplay.show(dummy.getLocation(), damage, color, false);
        playHitReaction(dummy, skillDamage);
    }

    public void markSkillDamage(Player player, Entity dummy, String skillId) {
        skillDamageMarkers.put(new DamageKey(player.getUniqueId(), dummy.getUniqueId()), skillId);
    }

    public void markSkillDamage(Player player, Entity dummy) {
        markSkillDamage(player, dummy, "spin-slash");
    }

    public TrainingDummySession getActiveSession(Player player, long nowMillis) {
        SessionKey key = latestSessionByPlayer.get(player.getUniqueId());
        TrainingDummySession session = key == null ? null : sessions.get(key);
        return session != null && nowMillis - session.getLastHitAtMillis() < SESSION_TIMEOUT_MILLIS ? session : null;
    }

    public void resetPlayer(Player player) {
        sessions.keySet().removeIf(key -> key.playerId().equals(player.getUniqueId()));
        latestSessionByPlayer.remove(player.getUniqueId());
    }

    public void removePlayer(Player player) {
        resetPlayer(player);
        skillDamageMarkers.keySet().removeIf(key -> key.playerId().equals(player.getUniqueId()));
    }

    public void stop() {
        if (sessionTask != null) {
            sessionTask.cancel();
            sessionTask = null;
        }
        removeAll();
        sessions.clear();
        latestSessionByPlayer.clear();
        skillDamageMarkers.clear();
        damageNumberDisplay.clear();
    }

    private void playHitReaction(ArmorStand dummy, boolean strong) {
        dummy.setBodyPose(new EulerAngle(0, 0, Math.toRadians(strong ? 18 : 8)));
        dummy.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, dummy.getLocation().add(0, 1.2, 0), strong ? 5 : 2, 0.25, 0.35, 0.25, 0.05);
        dummy.getWorld().playSound(dummy.getLocation(), Sound.ENTITY_ARMOR_STAND_HIT, strong ? 0.9f : 0.55f, strong ? 0.75f : 1.1f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dummy.isValid()) {
                dummy.setBodyPose(new EulerAngle(0, 0, 0));
            }
        }, strong ? 4L : 3L);
    }

    private void removeDummy(Entity dummy) {
        UUID dummyId = dummy.getUniqueId();
        dummyIds.remove(dummyId);
        var removedSessions = new ArrayList<TrainingDummySession>();
        sessions.entrySet().removeIf(entry -> {
            if (entry.getKey().dummyId().equals(dummyId)) {
                removedSessions.add(entry.getValue());
                return true;
            }
            return false;
        });
        removedSessions.forEach(this::showFinalResult);
        latestSessionByPlayer.entrySet().removeIf(entry -> entry.getValue().dummyId().equals(dummyId));
        skillDamageMarkers.keySet().removeIf(key -> key.dummyId().equals(dummyId));
        dummy.remove();
    }

    private void finishExpiredSessions() {
        long now = System.currentTimeMillis();
        var expired = new ArrayList<TrainingDummySession>();
        sessions.entrySet().removeIf(entry -> {
            if (now - entry.getValue().getLastHitAtMillis() >= SESSION_TIMEOUT_MILLIS) {
                expired.add(entry.getValue());
                return true;
            }
            return false;
        });
        for (TrainingDummySession session : expired) {
            latestSessionByPlayer.remove(session.getPlayerId(), new SessionKey(session.getPlayerId(), session.getDummyId()));
            showFinalResult(session);
        }
    }

    private void showFinalResult(TrainingDummySession session) {
        Player player = plugin.getServer().getPlayer(session.getPlayerId());
        if (player == null) {
            return;
        }
        long end = System.currentTimeMillis();
        player.sendMessage(Component.text("訓練結果", NamedTextColor.GOLD));
        player.sendMessage(Component.text("平均DPS: %.1f".formatted(session.getAverageDps(end)), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("合計ダメージ: %.1f".formatted(session.getTotalDamage()), NamedTextColor.WHITE));
        player.sendMessage(Component.text("計測時間: %.1f秒".formatted(session.getElapsedSeconds(end)), NamedTextColor.WHITE));
        player.sendMessage(Component.text("ヒット数: %d | 最大ダメージ: %.1f".formatted(
                session.getHitCount(), session.getMaximumHit()), NamedTextColor.WHITE));
        session.getSkillDamage().forEach((skillId, total) -> player.sendMessage(
                Component.text("  " + skillId + ": %.1f".formatted(total), NamedTextColor.AQUA)));
    }

    private record SessionKey(UUID playerId, UUID dummyId) {
    }

    private record DamageKey(UUID playerId, UUID dummyId) {
    }

    public record DummyType(String id, String displayName, org.bukkit.Material icon, String description) {
    }
}
