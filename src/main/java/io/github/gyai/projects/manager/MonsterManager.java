package io.github.gyai.projects.manager;

import io.github.gyai.projects.model.MonsterStats;
import io.github.gyai.projects.monster.CustomMonster;
import io.github.gyai.projects.monster.MonsterData;
import io.github.gyai.projects.monster.boss.HarborDevourerBoss;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MonsterManager {
    public static final String CUSTOM_MONSTER_KEY = "custom_monster_id";
    private static final String CONFIG_ROOT =
            "monsters.bosses.harbor-devourer-grohm.";
    private static final int TICK_INTERVAL = 2;

    private final JavaPlugin plugin;
    private final NamespacedKey customMonsterKey;
    private final Map<String, MonsterData> definitions = new HashMap<>();
    private final Map<UUID, CustomMonster> activeMonsters = new HashMap<>();
    private HarborDevourerBoss.Settings grohmSettings;
    private BukkitTask tickTask;

    public MonsterManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.customMonsterKey = new NamespacedKey(plugin, CUSTOM_MONSTER_KEY);
    }

    public void initialize() {
        MonsterStats stats = new MonsterStats(
                getClampedDouble("max-health", 1000.0, 1.0, 2048.0),
                getClampedDouble("attack-damage", 14.0, 0.0, 2048.0),
                getClampedDouble("movement-speed", 0.26, 0.01, 1.0),
                getClampedDouble("knockback-resistance", 1.0, 0.0, 1.0),
                getClampedDouble("follow-range", 32.0, 1.0, 128.0),
                getClampedDouble("scale", 1.35, 0.1, 4.0));
        register(new MonsterData(
                HarborDevourerBoss.MONSTER_ID,
                "港喰らいの巨獣 グローム",
                EntityType.RAVAGER,
                stats));

        double resetSeconds = getClampedDouble(
                "reset-after-seconds", 10.0, 1.0, 600.0);
        grohmSettings = new HarborDevourerBoss.Settings(
                getClampedDouble("leash-range", 36.0, 4.0, 256.0),
                getClampedDouble("boss-bar-range", 48.0, 4.0, 256.0),
                Math.max(1, (int) Math.round(resetSeconds * 20.0)),
                getClampedDouble("phase-two-health-ratio", 0.5, 0.05, 1.0),
                getClampedDouble(
                        "breakwater-slam.cooldown-seconds", 7.0, 0.1, 600.0),
                getClampedInt("breakwater-slam.warning-ticks", 24, 1, 200),
                getClampedDouble("breakwater-slam.radius", 5.0, 0.5, 64.0),
                getClampedDouble("breakwater-slam.damage", 18.0, 0.0, 2048.0),
                getClampedDouble(
                        "hullbreaker-charge.cooldown-seconds", 12.0, 0.1, 600.0),
                getClampedInt("hullbreaker-charge.warning-ticks", 20, 1, 200),
                getClampedDouble("hullbreaker-charge.distance", 14.0, 1.0, 64.0),
                getClampedDouble("hullbreaker-charge.damage", 24.0, 0.0, 2048.0),
                TICK_INTERVAL);
    }

    public void register(MonsterData data) {
        Objects.requireNonNull(data, "data");
        MonsterData previous = definitions.putIfAbsent(data.id(), data);
        if (previous != null) {
            throw new IllegalArgumentException(
                    "Monster id is already registered: " + data.id());
        }
    }

    public void start() {
        if (tickTask != null) {
            return;
        }
        tickTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        removeAll();
    }

    public CustomMonster spawnHarborDevourer(Location location) {
        Objects.requireNonNull(location, "location");
        if (hasActiveHarborDevourer() || grohmSettings == null) {
            return null;
        }
        MonsterData data = definitions.get(HarborDevourerBoss.MONSTER_ID);
        if (data == null || data.entityType() != EntityType.RAVAGER) {
            plugin.getLogger().warning("グロームのモブ定義が初期化されていません。");
            return null;
        }

        Ravager ravager = location.getWorld().spawn(
                location, Ravager.class, spawned -> configureEntity(spawned, data));
        BossBar bossBar = plugin.getServer().createBossBar(
                data.displayName(),
                BarColor.RED,
                BarStyle.SEGMENTED_10,
                new BarFlag[0]);
        HarborDevourerBoss boss = new HarborDevourerBoss(
                plugin, data, ravager, location, bossBar, grohmSettings);
        activeMonsters.put(ravager.getUniqueId(), boss);
        return boss;
    }

    public boolean hasActiveHarborDevourer() {
        return activeMonsters.values().stream()
                .anyMatch(monster -> monster.isValid()
                        && monster.getData().id().equals(
                                HarborDevourerBoss.MONSTER_ID));
    }

    public boolean removeHarborDevourer() {
        Optional<CustomMonster> active = activeMonsters.values().stream()
                .filter(monster -> monster.getData().id().equals(
                        HarborDevourerBoss.MONSTER_ID))
                .findFirst();
        if (active.isEmpty()) {
            return false;
        }
        return remove(active.get().getEntityId());
    }

    public CustomMonster get(UUID entityId) {
        return activeMonsters.get(entityId);
    }

    public boolean isCustomMonster(Entity entity) {
        String id = entity.getPersistentDataContainer().get(
                customMonsterKey, PersistentDataType.STRING);
        return id != null && definitions.containsKey(id);
    }

    public String getCustomMonsterId(Entity entity) {
        return entity.getPersistentDataContainer().get(
                customMonsterKey, PersistentDataType.STRING);
    }

    public boolean remove(UUID entityId) {
        CustomMonster monster = activeMonsters.remove(entityId);
        if (monster == null) {
            return false;
        }
        monster.remove();
        return true;
    }

    public void forget(UUID entityId) {
        activeMonsters.remove(entityId);
    }

    public int removeAll() {
        int count = activeMonsters.size();
        for (CustomMonster monster : activeMonsters.values().toArray(CustomMonster[]::new)) {
            monster.remove();
        }
        activeMonsters.clear();
        return count;
    }

    public Location findSafeSpawnLocation(Player player) {
        Vector direction = player.getLocation().getDirection().setY(0.0);
        if (direction.lengthSquared() < 0.0001) {
            direction.setZ(1.0);
        }
        direction.normalize();
        for (double distance : new double[]{5.0, 4.0, 6.0}) {
            Location candidate = player.getLocation().clone()
                    .add(direction.clone().multiply(distance));
            candidate.setX(Math.floor(candidate.getX()) + 0.5);
            candidate.setY(Math.floor(candidate.getY()));
            candidate.setZ(Math.floor(candidate.getZ()) + 0.5);
            candidate.setYaw(player.getLocation().getYaw() + 180.0f);
            candidate.setPitch(0.0f);
            if (isSafeSpawnVolume(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void configureEntity(Ravager ravager, MonsterData data) {
        MonsterStats stats = data.stats();
        ravager.customName(Component.text(data.displayName()));
        ravager.setCustomNameVisible(true);
        ravager.setPersistent(false);
        ravager.setRemoveWhenFarAway(false);
        ravager.setCanPickupItems(false);
        ravager.setFireTicks(0);
        ravager.getPersistentDataContainer().set(
                customMonsterKey, PersistentDataType.STRING, data.id());

        setAttribute(ravager, Attribute.MAX_HEALTH, stats.maxHealth());
        setAttribute(ravager, Attribute.ATTACK_DAMAGE, stats.attackDamage());
        setAttribute(ravager, Attribute.MOVEMENT_SPEED, stats.movementSpeed());
        setAttribute(
                ravager, Attribute.KNOCKBACK_RESISTANCE, stats.knockbackResistance());
        setAttribute(ravager, Attribute.FOLLOW_RANGE, stats.followRange());
        setAttribute(ravager, Attribute.SCALE, stats.scale());
        ravager.setHealth(stats.maxHealth());
    }

    private void setAttribute(Ravager ravager, Attribute attribute, double value) {
        AttributeInstance instance = ravager.getAttribute(attribute);
        if (instance == null) {
            plugin.getLogger().warning(
                    "エンティティ " + ravager.getType()
                            + " に属性 " + attribute + " がありません。");
            return;
        }
        instance.setBaseValue(value);
    }

    private void tick() {
        for (Map.Entry<UUID, CustomMonster> entry
                : Map.copyOf(activeMonsters).entrySet()) {
            CustomMonster monster = entry.getValue();
            if (!monster.isValid()) {
                monster.remove();
                activeMonsters.remove(entry.getKey(), monster);
                continue;
            }
            monster.tick();
        }
    }

    private boolean isSafeSpawnVolume(Location location) {
        if (location.getY() <= location.getWorld().getMinHeight() + 1
                || location.getY() + 4 >= location.getWorld().getMaxHeight()) {
            return false;
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block ground = location.clone().add(x, -1, z).getBlock();
                if (!ground.getType().isSolid()
                        || ground.getType() == Material.MAGMA_BLOCK) {
                    return false;
                }
                for (int y = 0; y <= 3; y++) {
                    if (!location.clone().add(x, y, z).getBlock().isPassable()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private double getClampedDouble(
            String suffix,
            double defaultValue,
            double minimum,
            double maximum
    ) {
        String path = CONFIG_ROOT + suffix;
        double configured = plugin.getConfig().getDouble(path, defaultValue);
        if (!Double.isFinite(configured)) {
            warnInvalid(path, configured, defaultValue);
            return defaultValue;
        }
        double clamped = Math.clamp(configured, minimum, maximum);
        if (clamped != configured) {
            warnInvalid(path, configured, clamped);
        }
        return clamped;
    }

    private int getClampedInt(
            String suffix,
            int defaultValue,
            int minimum,
            int maximum
    ) {
        String path = CONFIG_ROOT + suffix;
        int configured = plugin.getConfig().getInt(path, defaultValue);
        int clamped = Math.clamp(configured, minimum, maximum);
        if (clamped != configured) {
            warnInvalid(path, configured, clamped);
        }
        return clamped;
    }

    private void warnInvalid(String path, Object configured, Object replacement) {
        plugin.getLogger().warning(
                "不正な設定値 " + path + "=" + configured
                        + " を " + replacement + " に補正しました。");
    }
}
