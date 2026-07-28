package io.github.gyai.projects.combat.classsystem;

import io.github.gyai.projects.combat.resource.ResourceDefinition;
import io.github.gyai.projects.combat.resource.ResourceManager;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.manager.ItemManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WarriorCombatManager implements Listener {
    public static final String WARRIOR_WEAPON_ID = "starter_sword";

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final ResourceManager resourceManager;
    private final TrainingDummyManager dummyManager;
    private final long retentionMillis;
    private final int decayPerSecond;
    private final double damageBonusPerSpirit;
    private final double maximumSpiritHealing;
    private final Map<UUID, CombatState> combatStates = new HashMap<>();
    private final Map<UUID, SkillHitSession> activeSkillSessions = new HashMap<>();
    private BukkitTask decayTask;

    public WarriorCombatManager(
            JavaPlugin plugin,
            ItemManager itemManager,
            ResourceManager resourceManager,
            TrainingDummyManager dummyManager,
            double retentionSeconds,
            int decayPerSecond,
            double damagePercentPerSpirit,
            double maximumSpiritHealing
    ) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.resourceManager = resourceManager;
        this.dummyManager = dummyManager;
        this.retentionMillis = Math.max(0L, Math.round(retentionSeconds * 1_000.0));
        this.decayPerSecond = Math.max(0, decayPerSecond);
        this.damageBonusPerSpirit = Math.max(0.0, damagePercentPerSpirit) / 100.0;
        this.maximumSpiritHealing = Math.max(0.0, maximumSpiritHealing);
    }

    public void start() {
        if (decayTask == null) {
            decayTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin, this::updateDecay, 20L, 20L);
        }
    }

    public void stop() {
        if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }
        for (UUID playerId : Set.copyOf(combatStates.keySet())) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                resourceManager.set(player, ResourceDefinition.FIGHTING_SPIRIT, 0);
            }
        }
        combatStates.clear();
        activeSkillSessions.clear();
    }

    public SkillHitSession beginSkillUse(Player player) {
        SkillHitSession session = new SkillHitSession(player.getUniqueId());
        activeSkillSessions.put(player.getUniqueId(), session);
        return session;
    }

    public boolean isValidEnemy(Player player, Entity entity) {
        if (!(entity instanceof LivingEntity)
                || entity.equals(player)
                || entity instanceof Player) {
            return false;
        }
        boolean trainingDummy = dummyManager.isTrainingDummy(entity);
        return trainingDummy
                || (entity instanceof Mob
                && (!(entity instanceof ArmorStand) || trainingDummy));
    }

    public void reset(Player player) {
        UUID playerId = player.getUniqueId();
        combatStates.remove(playerId);
        activeSkillSessions.remove(playerId);
        resourceManager.set(player, ResourceDefinition.FIGHTING_SPIRIT, 0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void applyDamageBonus(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !isWarrior(player)
                || !isValidEnemy(player, event.getEntity())
                || event.getDamage() <= 0.0) {
            return;
        }
        double spirit = resourceManager.get(player, ResourceDefinition.FIGHTING_SPIRIT);
        event.setDamage(event.getDamage() * (1.0 + spirit * damageBonusPerSpirit));
    }

    public void recordConfirmedTrainingDummyHit(EntityDamageByEntityEvent event) {
        recordOutgoingHit(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void recordMobHit(EntityDamageByEntityEvent event) {
        if (dummyManager.isTrainingDummy(event.getEntity())) {
            return;
        }
        recordOutgoingHit(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void recordDamageReceived(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !isWarrior(player)
                || event.getFinalDamage() <= 0.0
                || !isValidIncomingSource(event.getDamager())) {
            return;
        }
        markCombat(player);
    }

    private void recordOutgoingHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !isWarrior(player)
                || !isValidEnemy(player, event.getEntity())
                || event.getFinalDamage() <= 0.0) {
            return;
        }

        double spiritBeforeHit = resourceManager.get(
                player, ResourceDefinition.FIGHTING_SPIRIT);
        SkillHitSession skillSession = activeSkillSessions.get(player.getUniqueId());
        if (skillSession == null || skillSession.markHit(event.getEntity().getUniqueId())) {
            resourceManager.set(
                    player,
                    ResourceDefinition.FIGHTING_SPIRIT,
                    spiritBeforeHit + 1);
        }
        if (spiritBeforeHit >= ResourceDefinition.FIGHTING_SPIRIT.maximum()) {
            heal(player);
        }
        markCombat(player);
    }

    private boolean isWarrior(Player player) {
        return itemManager.isCustomItem(
                player.getInventory().getItemInMainHand(), WARRIOR_WEAPON_ID);
    }

    private boolean isValidIncomingSource(Entity damager) {
        if (damager instanceof Mob) {
            return true;
        }
        return damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Mob;
    }

    private void heal(Player player) {
        var maximumHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maximumHealth == null || maximumSpiritHealing <= 0.0) {
            return;
        }
        player.setHealth(Math.min(maximumHealth.getValue(),
                player.getHealth() + maximumSpiritHealing));
    }

    private void markCombat(Player player) {
        long now = System.currentTimeMillis();
        combatStates.put(player.getUniqueId(), new CombatState(now, now));
    }

    private void updateDecay() {
        if (decayPerSecond <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, CombatState> entry : combatStates.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || !isWarrior(player)) {
                continue;
            }
            CombatState state = entry.getValue();
            long decayStartsAt = state.lastCombatMillis() + retentionMillis;
            long baseline = Math.max(state.lastDecayMillis(), decayStartsAt);
            long elapsedDecaySeconds = (now - baseline) / 1_000L;
            if (elapsedDecaySeconds <= 0L) {
                continue;
            }
            double current = resourceManager.get(
                    player, ResourceDefinition.FIGHTING_SPIRIT);
            resourceManager.set(
                    player,
                    ResourceDefinition.FIGHTING_SPIRIT,
                    current - elapsedDecaySeconds * decayPerSecond);
            entry.setValue(new CombatState(
                    state.lastCombatMillis(),
                    baseline + elapsedDecaySeconds * 1_000L));
        }
    }

    private record CombatState(long lastCombatMillis, long lastDecayMillis) {
    }

    public final class SkillHitSession implements AutoCloseable {
        private final UUID playerId;
        private final Set<UUID> hitTargets = new HashSet<>();
        private boolean closed;

        private SkillHitSession(UUID playerId) {
            this.playerId = playerId;
        }

        private boolean markHit(UUID targetId) {
            return hitTargets.add(targetId);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            activeSkillSessions.remove(playerId, this);
        }
    }
}
