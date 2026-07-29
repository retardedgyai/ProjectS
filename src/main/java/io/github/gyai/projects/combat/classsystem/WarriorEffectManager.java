package io.github.gyai.projects.combat.classsystem;

import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.manager.EnhancementManager;
import io.github.gyai.projects.skill.SkillManager;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WarriorEffectManager implements Listener {
    private final JavaPlugin plugin;
    private final WarriorCombatManager combatManager;
    private final EnhancementManager enhancementManager;
    private final TrainingDummyManager dummyManager;
    private final SkillManager skillManager;
    private final NamespacedKey indomitableAttackSpeedKey;
    private final NamespacedKey bloodBattleAttackSpeedKey;
    private final Map<UUID, IndomitableState> indomitable = new HashMap<>();
    private final Map<UUID, EndureState> endure = new HashMap<>();
    private final Map<UUID, BloodBattleState> bloodBattle = new HashMap<>();
    private final Map<UUID, AbsorptionState> absorption = new HashMap<>();
    private final Map<UUID, ChargeState> charges = new HashMap<>();
    private final Map<UUID, Map<String, List<BukkitTask>>> scheduled =
            new HashMap<>();
    private WarriorLoadoutManager loadoutManager;
    private BukkitTask updateTask;

    public WarriorEffectManager(
            JavaPlugin plugin,
            WarriorCombatManager combatManager,
            EnhancementManager enhancementManager,
            TrainingDummyManager dummyManager,
            SkillManager skillManager
    ) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        this.enhancementManager = enhancementManager;
        this.dummyManager = dummyManager;
        this.skillManager = skillManager;
        indomitableAttackSpeedKey =
                new NamespacedKey(plugin, "warrior_indomitable_attack_speed");
        bloodBattleAttackSpeedKey =
                new NamespacedKey(plugin, "warrior_blood_battle_attack_speed");
    }

    public void setLoadoutManager(WarriorLoadoutManager loadoutManager) {
        this.loadoutManager = loadoutManager;
    }

    public void start() {
        if (updateTask == null) {
            updateTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin, this::update, 1L, 1L);
        }
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        Set<UUID> players = new java.util.HashSet<>();
        players.addAll(indomitable.keySet());
        players.addAll(endure.keySet());
        players.addAll(bloodBattle.keySet());
        players.addAll(absorption.keySet());
        players.addAll(charges.keySet());
        players.addAll(scheduled.keySet());
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                clearPlayer(player, true);
            } else {
                ChargeState charge = charges.remove(playerId);
                if (charge != null) charge.hitSession.close();
            }
        }
        indomitable.clear();
        endure.clear();
        bloodBattle.clear();
        absorption.clear();
        charges.clear();
        scheduled.clear();
    }

    public void startIndomitable(
            Player player,
            double durationSeconds,
            double damageReduction,
            double attackSpeedBonus
    ) {
        cancelIndomitable(player);
        long end = System.currentTimeMillis()
                + Math.round(Math.max(0.1, durationSeconds) * 1_000.0);
        indomitable.put(
                player.getUniqueId(),
                new IndomitableState(end, Math.clamp(damageReduction, 0, .9)));
        addAttackSpeedModifier(
                player, indomitableAttackSpeedKey, attackSpeedBonus);
    }

    public boolean isIndomitableActive(Player player) {
        IndomitableState state = indomitable.get(player.getUniqueId());
        return state != null && state.endsAtMillis() > System.currentTimeMillis();
    }

    public void startEndure(
            Player player,
            double durationSeconds,
            double deferredFraction,
            double outgoingReductionFraction
    ) {
        settleEndure(player);
        endure.put(player.getUniqueId(), new EndureState(
                System.currentTimeMillis()
                        + Math.round(Math.max(.1, durationSeconds) * 1_000.0),
                Math.clamp(deferredFraction, 0, .9),
                Math.clamp(outgoingReductionFraction, 0, 1),
                0.0));
    }

    public void startBloodBattle(
            Player player,
            double durationSeconds,
            double attackSpeedBonus,
            double splashDamageFraction,
            double splashRadius,
            double cooldownReductionPerHit
    ) {
        cancelBloodBattle(player);
        bloodBattle.put(player.getUniqueId(), new BloodBattleState(
                System.currentTimeMillis()
                        + Math.round(Math.max(.1, durationSeconds) * 1_000.0),
                Math.clamp(splashDamageFraction, 0, 2),
                Math.clamp(splashRadius, 0, 12),
                Math.clamp(cooldownReductionPerHit, 0, 10)));
        addAttackSpeedModifier(
                player, bloodBattleAttackSpeedKey, attackSpeedBonus);
    }

    public void grantAbsorption(
            Player player,
            double amount,
            double durationSeconds
    ) {
        removeOwnAbsorption(player);
        double grant = Math.clamp(amount, 0, 40);
        double updated = Math.max(0, player.getAbsorptionAmount()) + grant;
        player.setAbsorptionAmount(updated);
        absorption.put(player.getUniqueId(), new AbsorptionState(
                System.currentTimeMillis()
                        + Math.round(Math.max(.1, durationSeconds) * 1_000.0),
                grant,
                updated));
    }

    public boolean canStartCharge(
            Player player,
            Vector direction,
            double speed
    ) {
        return normalizedDirection(direction) != null;
    }

    public void startCharge(
            Player player,
            Vector direction,
            double maximumDistance,
            double speed,
            double width,
            double damage
    ) {
        cancelCharge(player);
        Vector forward = normalizedDirection(direction);
        if (forward == null) return;
        ChargeState state = new ChargeState(
                forward,
                Math.max(.5, maximumDistance),
                Math.clamp(speed, .2, 2),
                Math.clamp(width, .3, 6),
                Math.max(0, damage),
                player.getLocation().clone(),
                combatManager.beginSkillUse(player));
        charges.put(player.getUniqueId(), state);
        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_RAVAGER_STEP,
                .9f,
                1.25f);
        applyChargeImpulse(player, state);
    }

    public boolean isSkillActive(Player player, String skillId) {
        return switch (skillId) {
            case "warrior_charge" ->
                    charges.containsKey(player.getUniqueId());
            case "indomitable_spirit" -> isIndomitableActive(player);
            case "battlefield_aura" ->
                    absorption.containsKey(player.getUniqueId());
            case "endure" -> endure.containsKey(player.getUniqueId());
            case "blood_battle" ->
                    bloodBattle.containsKey(player.getUniqueId());
            case "end_war_strike" -> hasScheduled(player, skillId);
            default -> false;
        };
    }

    public void schedule(
            Player player,
            String skillId,
            long delayTicks,
            Runnable action
    ) {
        UUID playerId = player.getUniqueId();
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> {
                    removeCompletedTasks(playerId, skillId);
                    if (player.isOnline()
                            && !player.isDead()
                            && combatManager.isWarrior(player)) {
                        action.run();
                    }
                },
                Math.max(1L, delayTicks));
        scheduled
                .computeIfAbsent(playerId, ignored -> new HashMap<>())
                .computeIfAbsent(skillId, ignored -> new ArrayList<>())
                .add(task);
    }

    public void onConfirmedOutgoingHit(
            Player player,
            LivingEntity primaryTarget,
            double finalDamage,
            boolean skillDamage
    ) {
        UUID playerId = player.getUniqueId();
        EndureState endureState = endure.get(playerId);
        if (endureState != null && finalDamage > 0.0) {
            double remaining = Math.max(
                    0.0,
                    endureState.pendingDamage()
                            - finalDamage
                            * endureState.outgoingReductionFraction());
            endure.put(playerId, endureState.withPendingDamage(remaining));
        }

        BloodBattleState bloodState = bloodBattle.get(playerId);
        if (bloodState == null) return;
        if (loadoutManager != null) {
            String eSkill = loadoutManager.get(player).e();
            skillManager.reduceCooldown(
                    player, eSkill, bloodState.cooldownReductionPerHit());
        }
        if (!skillDamage) {
            splashNormalAttack(player, primaryTarget, finalDamage, bloodState);
        }
    }

    public void cancelSkill(Player player, String skillId) {
        switch (skillId) {
            case "warrior_charge" -> cancelCharge(player);
            case "indomitable_spirit" -> cancelIndomitable(player);
            case "battlefield_aura" -> removeOwnAbsorption(player);
            case "endure" -> settleEndure(player);
            case "blood_battle" -> cancelBloodBattle(player);
            default -> cancelScheduled(player, skillId);
        }
    }

    public void clearPlayer(Player player, boolean settleDeferredDamage) {
        cancelCharge(player);
        cancelIndomitable(player);
        cancelBloodBattle(player);
        removeOwnAbsorption(player);
        if (settleDeferredDamage) settleEndure(player);
        else endure.remove(player.getUniqueId());
        cancelAllScheduled(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void reduceIncomingDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !combatManager.isWarrior(player)
                || event.getFinalDamage() <= 0.0) {
            return;
        }

        IndomitableState indomitableState =
                indomitable.get(player.getUniqueId());
        if (indomitableState != null) {
            event.setDamage(event.getDamage()
                    * (1.0 - indomitableState.damageReduction()));
        }

        EndureState endureState = endure.get(player.getUniqueId());
        if (endureState == null) return;
        double deferred = Math.max(
                0.0, event.getFinalDamage() * endureState.deferredFraction());
        event.setDamage(event.getDamage()
                * (1.0 - endureState.deferredFraction()));
        endure.put(
                player.getUniqueId(),
                endureState.withPendingDamage(
                        endureState.pendingDamage() + deferred));
    }

    private void splashNormalAttack(
            Player player,
            LivingEntity primaryTarget,
            double finalDamage,
            BloodBattleState state
    ) {
        if (finalDamage <= 0.0 || state.splashRadius() <= 0.0) return;
        List<LivingEntity> targets = primaryTarget.getLocation()
                .getNearbyLivingEntities(state.splashRadius())
                .stream()
                .filter(target -> !target.equals(primaryTarget))
                .filter(target -> combatManager.isValidEnemy(player, target))
                .toList();
        if (targets.isEmpty()) return;

        try (WarriorCombatManager.SkillHitSession session =
                     combatManager.beginSkillUse(player)) {
            for (LivingEntity target : targets) {
                if (dummyManager.isTrainingDummy(target)) {
                    dummyManager.markSkillDamage(
                            player, target, "blood_battle");
                }
                enhancementManager.beginSkillDamage(player.getUniqueId());
                try (WarriorCombatManager.HitScope ignored =
                             session.activate()) {
                    combatManager.runWithSpiritBonusAlreadyApplied(
                            player,
                            () -> target.damage(
                                    finalDamage
                                            * state.splashDamageFraction(),
                                    player));
                } finally {
                    enhancementManager.endSkillDamage(player.getUniqueId());
                }
            }
        }
    }

    private void update() {
        long now = System.currentTimeMillis();
        updateCharges();
        for (UUID playerId : Set.copyOf(indomitable.keySet())) {
            if (indomitable.get(playerId).endsAtMillis() <= now) {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) cancelIndomitable(player);
                else indomitable.remove(playerId);
            }
        }
        for (UUID playerId : Set.copyOf(bloodBattle.keySet())) {
            if (bloodBattle.get(playerId).endsAtMillis() <= now) {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) cancelBloodBattle(player);
                else bloodBattle.remove(playerId);
            }
        }
        for (UUID playerId : Set.copyOf(endure.keySet())) {
            if (endure.get(playerId).endsAtMillis() <= now) {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) settleEndure(player);
                else endure.remove(playerId);
            }
        }
        for (UUID playerId : Set.copyOf(absorption.keySet())) {
            Player player = plugin.getServer().getPlayer(playerId);
            AbsorptionState state = absorption.get(playerId);
            if (player == null) {
                absorption.remove(playerId);
                continue;
            }
            double current = Math.max(0, player.getAbsorptionAmount());
            double consumed = Math.max(0, state.lastObservedAmount() - current);
            state = state.withAmounts(
                    Math.max(0, state.ownRemaining() - consumed), current);
            absorption.put(playerId, state);
            if (state.endsAtMillis() <= now) removeOwnAbsorption(player);
        }
    }

    private void updateCharges() {
        for (UUID playerId : Set.copyOf(charges.keySet())) {
            ChargeState state = charges.get(playerId);
            Player player = plugin.getServer().getPlayer(playerId);
            if (state == null) continue;
            if (player == null
                    || !player.isOnline()
                    || player.isDead()
                    || !combatManager.isWarrior(player)
                    || (loadoutManager != null
                    && !"warrior_charge".equals(
                    loadoutManager.get(player).e()))) {
                finishCharge(playerId, player);
                continue;
            }

            Location current = player.getLocation();
            if (!current.getWorld().equals(
                    state.lastLocation.getWorld())) {
                finishCharge(playerId, player);
                continue;
            }
            double moved = state.lastLocation.distance(current);
            if (moved > .03) {
                state.traveled += moved;
                state.stalledTicks = 0;
                drawChargeTrail(state.lastLocation, current);
                state.lastLocation = current.clone();
            } else {
                state.stalledTicks++;
            }
            damageChargeTargets(player, state);
            state.elapsedTicks++;

            if (state.traveled >= state.maximumDistance
                    || state.stalledTicks > 4
                    || state.elapsedTicks >= 20) {
                finishCharge(playerId, player);
            }
        }
    }

    private void damageChargeTargets(
            Player player,
            ChargeState state
    ) {
        List<LivingEntity> targets = player.getLocation()
                .getNearbyLivingEntities(state.width)
                .stream()
                .filter(target ->
                        combatManager.isValidEnemy(player, target))
                .filter(target ->
                        state.hitTargets.add(target.getUniqueId()))
                .toList();
        for (LivingEntity target : targets) {
            if (dummyManager.isTrainingDummy(target)) {
                dummyManager.markSkillDamage(
                        player, target, "warrior_charge");
            }
            enhancementManager.beginSkillDamage(player.getUniqueId());
            try (WarriorCombatManager.HitScope ignored =
                         state.hitSession.activate()) {
                target.damage(state.damage, player);
            } finally {
                enhancementManager.endSkillDamage(player.getUniqueId());
            }
        }
    }

    private void applyChargeImpulse(
            Player player,
            ChargeState state
    ) {
        Vector velocity = state.direction.clone()
                .multiply(state.speed);
        player.setVelocity(velocity);
    }

    private void drawChargeTrail(Location from, Location to) {
        Vector segment = to.toVector().subtract(from.toVector());
        int samples = Math.clamp(
                (int) Math.ceil(segment.length() * 4), 2, 6);
        for (int index = 0; index <= samples; index++) {
            Location point = from.clone().add(
                    segment.clone().multiply(index / (double) samples));
            point.add(0, .3, 0);
            point.getWorld().spawnParticle(
                    Particle.CLOUD,
                    point,
                    1,
                    .08, .05, .08,
                    .01);
            if (index % 2 == 0) {
                point.getWorld().spawnParticle(
                        Particle.CRIT,
                        point.clone().add(0, .25, 0),
                        1,
                        .04, .04, .04,
                        0);
            }
        }
    }

    private Vector normalizedDirection(Vector direction) {
        Vector forward = direction.clone();
        if (forward.lengthSquared() < .0001) return null;
        return forward.normalize();
    }

    private void cancelCharge(Player player) {
        finishCharge(player.getUniqueId(), player);
    }

    private void finishCharge(UUID playerId, Player player) {
        ChargeState state = charges.remove(playerId);
        if (state == null) return;
        state.hitSession.close();
    }

    private void settleEndure(Player player) {
        EndureState state = endure.remove(player.getUniqueId());
        if (state == null || state.pendingDamage() <= 0.0 || player.isDead()) {
            return;
        }
        player.damage(state.pendingDamage());
    }

    private void cancelIndomitable(Player player) {
        indomitable.remove(player.getUniqueId());
        removeAttackSpeedModifier(player, indomitableAttackSpeedKey);
    }

    private void cancelBloodBattle(Player player) {
        bloodBattle.remove(player.getUniqueId());
        removeAttackSpeedModifier(player, bloodBattleAttackSpeedKey);
    }

    private void addAttackSpeedModifier(
            Player player,
            NamespacedKey key,
            double amount
    ) {
        AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute == null) return;
        removeAttackSpeedModifier(player, key);
        attribute.addTransientModifier(new AttributeModifier(
                key,
                Math.clamp(amount, -.9, 5),
                AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void removeAttackSpeedModifier(Player player, NamespacedKey key) {
        AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute == null) return;
        AttributeModifier modifier = attribute.getModifier(key);
        if (modifier != null) attribute.removeModifier(modifier);
    }

    private void removeOwnAbsorption(Player player) {
        AbsorptionState state = absorption.remove(player.getUniqueId());
        if (state == null) return;
        double current = Math.max(0, player.getAbsorptionAmount());
        player.setAbsorptionAmount(
                Math.max(0, current - Math.min(current, state.ownRemaining())));
    }

    private void cancelScheduled(Player player, String skillId) {
        Map<String, List<BukkitTask>> bySkill =
                scheduled.get(player.getUniqueId());
        if (bySkill == null) return;
        List<BukkitTask> tasks = bySkill.remove(skillId);
        if (tasks != null) tasks.forEach(BukkitTask::cancel);
        if (bySkill.isEmpty()) scheduled.remove(player.getUniqueId());
    }

    private void cancelAllScheduled(Player player) {
        Map<String, List<BukkitTask>> bySkill =
                scheduled.remove(player.getUniqueId());
        if (bySkill == null) return;
        bySkill.values().forEach(tasks -> tasks.forEach(BukkitTask::cancel));
    }

    private boolean hasScheduled(Player player, String skillId) {
        Map<String, List<BukkitTask>> bySkill =
                scheduled.get(player.getUniqueId());
        if (bySkill == null) return false;
        List<BukkitTask> tasks = bySkill.get(skillId);
        return tasks != null && tasks.stream()
                .anyMatch(task -> !task.isCancelled());
    }

    private void removeCompletedTasks(UUID playerId, String skillId) {
        Map<String, List<BukkitTask>> bySkill = scheduled.get(playerId);
        if (bySkill == null) return;
        List<BukkitTask> tasks = bySkill.get(skillId);
        if (tasks != null) tasks.removeIf(
                task -> task.isCancelled() || task.getTaskId() >= 0);
        bySkill.remove(skillId);
        if (bySkill.isEmpty()) scheduled.remove(playerId);
    }

    private static final class ChargeState {
        private final Vector direction;
        private final double maximumDistance;
        private final double speed;
        private final double width;
        private final double damage;
        private final Set<UUID> hitTargets = new HashSet<>();
        private final WarriorCombatManager.SkillHitSession hitSession;
        private Location lastLocation;
        private double traveled;
        private int stalledTicks;
        private int elapsedTicks;

        private ChargeState(
                Vector direction,
                double maximumDistance,
                double speed,
                double width,
                double damage,
                Location lastLocation,
                WarriorCombatManager.SkillHitSession hitSession
        ) {
            this.direction = direction;
            this.maximumDistance = maximumDistance;
            this.speed = speed;
            this.width = width;
            this.damage = damage;
            this.lastLocation = lastLocation;
            this.hitSession = hitSession;
        }
    }

    private record IndomitableState(
            long endsAtMillis,
            double damageReduction
    ) {
    }

    private record EndureState(
            long endsAtMillis,
            double deferredFraction,
            double outgoingReductionFraction,
            double pendingDamage
    ) {
        private EndureState withPendingDamage(double value) {
            return new EndureState(
                    endsAtMillis,
                    deferredFraction,
                    outgoingReductionFraction,
                    value);
        }
    }

    private record BloodBattleState(
            long endsAtMillis,
            double splashDamageFraction,
            double splashRadius,
            double cooldownReductionPerHit
    ) {
    }

    private record AbsorptionState(
            long endsAtMillis,
            double ownRemaining,
            double lastObservedAmount
    ) {
        private AbsorptionState withAmounts(
                double ownRemaining,
                double lastObservedAmount
        ) {
            return new AbsorptionState(
                    endsAtMillis, ownRemaining, lastObservedAmount);
        }
    }
}
