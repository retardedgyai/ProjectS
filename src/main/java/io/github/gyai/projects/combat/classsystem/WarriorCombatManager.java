package io.github.gyai.projects.combat.classsystem;

import io.github.gyai.projects.combat.resource.ResourceDefinition;
import io.github.gyai.projects.combat.resource.ResourceManager;
import io.github.gyai.projects.combat.damage.DamageResult;
import io.github.gyai.projects.combat.damage.DamageService;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.manager.EnhancementManager;
import io.github.gyai.projects.manager.ItemManager;
import org.bukkit.attribute.Attribute;
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

import java.util.ArrayDeque;
import java.util.Deque;
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
    private final EnhancementManager enhancementManager;
    private final DamageService damageService;
    private final long retentionMillis;
    private final int decayPerSecond;
    private final double damageBonusPerSpirit;
    private final double maximumSpiritHealing;
    private final Map<UUID, CombatState> combatStates = new HashMap<>();
    private final Map<UUID, Deque<SkillHitSession>> activeSkillContexts =
            new HashMap<>();
    private final Map<UUID, Integer> preScaledDamageDepth = new HashMap<>();
    private WarriorEffectManager effectManager;
    private BukkitTask decayTask;

    public WarriorCombatManager(
            JavaPlugin plugin,
            ItemManager itemManager,
            ResourceManager resourceManager,
            TrainingDummyManager dummyManager,
            EnhancementManager enhancementManager,
            DamageService damageService,
            double retentionSeconds,
            int decayPerSecond,
            double damagePercentPerSpirit,
            double maximumSpiritHealing
    ) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.resourceManager = resourceManager;
        this.dummyManager = dummyManager;
        this.enhancementManager = enhancementManager;
        this.damageService = damageService;
        retentionMillis = Math.max(0L, Math.round(retentionSeconds * 1_000.0));
        this.decayPerSecond = Math.max(0, decayPerSecond);
        damageBonusPerSpirit = Math.max(0.0, damagePercentPerSpirit) / 100.0;
        this.maximumSpiritHealing = Math.max(0.0, maximumSpiritHealing);
    }

    public void setEffectManager(WarriorEffectManager effectManager) {
        this.effectManager = effectManager;
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
                resourceManager.set(
                        player, ResourceDefinition.FIGHTING_SPIRIT, 0);
            }
        }
        combatStates.clear();
        activeSkillContexts.clear();
        preScaledDamageDepth.clear();
    }

    public SkillHitSession beginSkillUse(Player player) {
        return new SkillHitSession(player.getUniqueId());
    }

    public boolean isValidEnemy(Player player, Entity entity) {
        return entity instanceof LivingEntity
                && !entity.equals(player)
                && !(entity instanceof Player)
                && (entity instanceof Mob || dummyManager.isTrainingDummy(entity));
    }

    public boolean isWarrior(Player player) {
        return itemManager.isCustomItem(
                player.getInventory().getItemInMainHand(), WARRIOR_WEAPON_ID);
    }

    public boolean isInCombat(Player player) {
        CombatState state = combatStates.get(player.getUniqueId());
        return state != null
                && System.currentTimeMillis() - state.lastCombatMillis()
                < retentionMillis;
    }

    public void runWithSpiritBonusAlreadyApplied(
            Player player,
            Runnable action
    ) {
        UUID playerId = player.getUniqueId();
        preScaledDamageDepth.merge(playerId, 1, Integer::sum);
        try {
            action.run();
        } finally {
            int depth = preScaledDamageDepth.getOrDefault(playerId, 1) - 1;
            if (depth <= 0) preScaledDamageDepth.remove(playerId);
            else preScaledDamageDepth.put(playerId, depth);
        }
    }

    public double damageMultiplierForSpirit(double spirit) {
        return 1.0
                + Math.clamp(
                spirit,
                0.0,
                ResourceDefinition.FIGHTING_SPIRIT.maximum())
                * damageBonusPerSpirit;
    }

    public long combatRemainingMillis(Player player) {
        CombatState state = combatStates.get(player.getUniqueId());
        if (state == null) return 0L;
        return Math.max(0L,
                retentionMillis
                        - (System.currentTimeMillis() - state.lastCombatMillis()));
    }

    public void reset(Player player) {
        UUID playerId = player.getUniqueId();
        combatStates.remove(playerId);
        activeSkillContexts.remove(playerId);
        preScaledDamageDepth.remove(playerId);
        resourceManager.set(player, ResourceDefinition.FIGHTING_SPIRIT, 0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void applyDamageBonus(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !isWarrior(player)
                || !isValidEnemy(player, event.getEntity())
                || preScaledDamageDepth.containsKey(
                        player.getUniqueId())
                || event.getDamage() <= 0.0) {
            return;
        }
        double spirit = resourceManager.get(
                player, ResourceDefinition.FIGHTING_SPIRIT);
        event.setDamage(event.getDamage()
                * (1.0 + spirit * damageBonusPerSpirit));
    }

    public void recordConfirmedTrainingDummyHit(
            EntityDamageByEntityEvent event
    ) {
        recordOutgoingHit(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void recordMobHit(EntityDamageByEntityEvent event) {
        if (!dummyManager.isTrainingDummy(event.getEntity())) {
            recordOutgoingHit(event);
        }
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
        SkillHitSession skillSession = currentSession(player);
        boolean grantsSpirit = skillSession == null
                || skillSession.markHit(event.getEntity().getUniqueId());
        if (grantsSpirit) {
            int gain = effectManager != null
                    && effectManager.isIndomitableActive(player) ? 2 : 1;
            resourceManager.set(
                    player,
                    ResourceDefinition.FIGHTING_SPIRIT,
                    spiritBeforeHit + gain);
        }
        if (skillSession != null) {
            skillSession.confirmHit();
        }
        if (spiritBeforeHit
                >= ResourceDefinition.FIGHTING_SPIRIT.maximum()) {
            heal(player);
        }
        markCombat(player);
        if (effectManager != null) {
            LivingEntity target = (LivingEntity) event.getEntity();
            DamageResult calculation = damageService.currentCalculation(
                    player, target);
            effectManager.onConfirmedOutgoingHit(
                    player,
                    target,
                    event.getFinalDamage(),
                    enhancementManager.isApplyingSkillDamage(
                            player.getUniqueId()),
                    calculation,
                    damageMultiplierForSpirit(spiritBeforeHit));
        }
    }

    private SkillHitSession currentSession(Player player) {
        Deque<SkillHitSession> contexts =
                activeSkillContexts.get(player.getUniqueId());
        return contexts == null ? null : contexts.peek();
    }

    private boolean isValidIncomingSource(Entity damager) {
        if (damager instanceof Mob) return true;
        return damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Mob;
    }

    private void heal(Player player) {
        var maximumHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maximumHealth == null || maximumSpiritHealing <= 0.0) return;
        player.setHealth(Math.min(
                maximumHealth.getValue(),
                player.getHealth() + maximumSpiritHealing));
    }

    private void markCombat(Player player) {
        long now = System.currentTimeMillis();
        combatStates.put(player.getUniqueId(), new CombatState(now, now));
    }

    private void updateDecay() {
        if (decayPerSecond <= 0) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, CombatState> entry : combatStates.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || !isWarrior(player)) {
                continue;
            }
            CombatState state = entry.getValue();
            long decayStartsAt = state.lastCombatMillis() + retentionMillis;
            long baseline = Math.max(
                    state.lastDecayMillis(), decayStartsAt);
            long elapsedSeconds = (now - baseline) / 1_000L;
            if (elapsedSeconds <= 0L) continue;
            double current = resourceManager.get(
                    player, ResourceDefinition.FIGHTING_SPIRIT);
            resourceManager.set(
                    player,
                    ResourceDefinition.FIGHTING_SPIRIT,
                    current - elapsedSeconds * decayPerSecond);
            entry.setValue(new CombatState(
                    state.lastCombatMillis(),
                    baseline + elapsedSeconds * 1_000L));
        }
    }

    private record CombatState(long lastCombatMillis, long lastDecayMillis) {
    }

    public final class SkillHitSession implements AutoCloseable {
        private final UUID playerId;
        private final UUID sessionId = UUID.randomUUID();
        private final Set<UUID> hitTargets = new HashSet<>();
        private int confirmedHits;
        private boolean closed;

        private SkillHitSession(UUID playerId) {
            this.playerId = playerId;
        }

        public UUID sessionId() {
            return sessionId;
        }

        public int confirmedHits() {
            return confirmedHits;
        }

        public boolean confirmedTarget(UUID targetId) {
            return hitTargets.contains(targetId);
        }

        public HitScope activate() {
            if (closed) {
                throw new IllegalStateException("Skill hit session is closed");
            }
            activeSkillContexts
                    .computeIfAbsent(playerId, ignored -> new ArrayDeque<>())
                    .push(this);
            return new HitScope(this);
        }

        private boolean markHit(UUID targetId) {
            return hitTargets.add(targetId);
        }

        private void confirmHit() {
            confirmedHits++;
        }

        @Override
        public void close() {
            closed = true;
            Deque<SkillHitSession> contexts =
                    activeSkillContexts.get(playerId);
            if (contexts != null) {
                contexts.removeIf(session -> session == this);
                if (contexts.isEmpty()) activeSkillContexts.remove(playerId);
            }
        }
    }

    public final class HitScope implements AutoCloseable {
        private final SkillHitSession session;
        private boolean closed;

        private HitScope(SkillHitSession session) {
            this.session = session;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            Deque<SkillHitSession> contexts =
                    activeSkillContexts.get(session.playerId);
            if (contexts != null) {
                contexts.removeFirstOccurrence(session);
                if (contexts.isEmpty()) {
                    activeSkillContexts.remove(session.playerId);
                }
            }
        }
    }
}
