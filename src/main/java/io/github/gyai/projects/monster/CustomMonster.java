package io.github.gyai.projects.monster;

import org.bukkit.Location;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

public abstract class CustomMonster {
    protected final JavaPlugin plugin;
    protected final MonsterData data;
    protected final LivingEntity entity;
    protected final Location spawnLocation;
    protected final BossBar bossBar;
    protected int currentPhase = 1;
    protected boolean removed;

    protected CustomMonster(
            JavaPlugin plugin,
            MonsterData data,
            LivingEntity entity,
            Location spawnLocation,
            BossBar bossBar
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.data = Objects.requireNonNull(data, "data");
        this.entity = Objects.requireNonNull(entity, "entity");
        this.spawnLocation = Objects.requireNonNull(spawnLocation, "spawnLocation").clone();
        this.bossBar = Objects.requireNonNull(bossBar, "bossBar");
    }

    public abstract void tick();

    public void handleDamage(EntityDamageEvent event) {
    }

    public void handleDeath(EntityDeathEvent event) {
        removed = true;
        bossBar.removeAll();
    }

    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        bossBar.removeAll();
        if (entity.isValid()) {
            entity.remove();
        }
    }

    public void updateBossBar() {
        if (removed || !entity.isValid()) {
            bossBar.removeAll();
            return;
        }
        double maximum = data.stats().maxHealth();
        double progress = maximum <= 0.0 ? 0.0 : entity.getHealth() / maximum;
        bossBar.setProgress(Math.clamp(progress, 0.0, 1.0));
    }

    public boolean isValid() {
        return !removed && entity.isValid() && !entity.isDead();
    }

    public UUID getEntityId() {
        return entity.getUniqueId();
    }

    public MonsterData getData() {
        return data;
    }

    public LivingEntity getEntity() {
        return entity;
    }
}
