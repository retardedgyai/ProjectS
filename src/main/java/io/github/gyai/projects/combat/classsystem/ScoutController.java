package io.github.gyai.projects.combat.classsystem;

import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.manager.EnhancementManager;
import io.github.gyai.projects.manager.ItemManager;
import io.github.gyai.projects.skill.SkillManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ScoutController implements ClassController, Listener {
    private static final String BOW_ID = "starter_bow";
    private static final String Q_COOLDOWN_ID = "scout_q";
    private static final String E_COOLDOWN_ID = "scout_e";
    private static final String BLINK_COOLDOWN_ID = "scout_blink";
    private static final long Q_DURATION_MILLIS = 5_000L;
    private static final double Q_ATTACK_SPEED_BONUS = 0.40;

    private final ItemManager itemManager;
    private final EnhancementManager enhancementManager;
    private final SkillManager cooldowns;
    private final TrainingDummyManager dummyManager;
    private final NamespacedKey scoutArrowKey;
    private final Map<UUID, Long> qBuffEnds = new HashMap<>();
    private final Map<UUID, Integer> passiveHits = new HashMap<>();

    public ScoutController(
            JavaPlugin plugin,
            ItemManager itemManager,
            EnhancementManager enhancementManager,
            SkillManager cooldowns,
            TrainingDummyManager dummyManager
    ) {
        this.itemManager = itemManager;
        this.enhancementManager = enhancementManager;
        this.cooldowns = cooldowns;
        this.dummyManager = dummyManager;
        scoutArrowKey = new NamespacedKey(plugin, "scout_arrow");
    }

    @Override
    public void handle(Player player, SkillSlot input) {
        switch (input) {
            case SKILL_Q -> activateQ(player);
            case SKILL_W, SKILL_R -> player.sendActionBar(
                    Component.text("このScoutスキルは未実装です", NamedTextColor.YELLOW));
            case SKILL_E -> castVolley(player);
        }
    }

    private void activateQ(Player player) {
        if (!ready(player, Q_COOLDOWN_ID)) return;
        qBuffEnds.put(player.getUniqueId(), System.currentTimeMillis() + Q_DURATION_MILLIS);
        cooldowns.startCooldown(player, Q_COOLDOWN_ID, 12.0, 0.0);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1, 0), 24, .5, .8, .5, .05);
        player.playSound(player, Sound.ENTITY_ARROW_SHOOT, 1f, 1.6f);
        player.sendMessage(Component.text(
                "Rapid Volley：5秒間、攻撃速度上昇・追加の矢を獲得", NamedTextColor.GREEN));
    }

    private void castVolley(Player player) {
        if (!ready(player, E_COOLDOWN_ID)) return;
        var weapon = player.getInventory().getItemInMainHand();
        if (enhancementManager.isBroken(weapon)) {
            player.sendActionBar(Component.text("弓が破損しています", NamedTextColor.RED));
            return;
        }
        double damage = 11.0 + enhancementManager.getAttackPower(player, weapon) * 1.8;
        Vector center = player.getEyeLocation().getDirection().normalize();
        for (int index = 0; index < 10; index++) {
            double yaw = -27.0 + index * 6.0;
            Arrow arrow = launchArrow(player, rotateY(center, Math.toRadians(yaw)), damage, 2.8);
            markScoutArrow(arrow);
        }
        cooldowns.startCooldown(player, E_COOLDOWN_ID, 8.0, 0.0);
        player.getWorld().spawnParticle(Particle.CLOUD,
                player.getEyeLocation(), 18, .3, .3, .3, .04);
        player.playSound(player, Sound.ENTITY_ARROW_SHOOT, 1.2f, .75f);
    }

    public void onBasicArrowFired(Player player, Arrow arrow, double damage) {
        markScoutArrow(arrow);
        if (!isQActive(player)) return;
        Vector extraDirection = rotateY(
                player.getEyeLocation().getDirection().normalize(), Math.toRadians(3.0));
        markScoutArrow(launchArrow(player, extraDirection, damage, 3.2));
    }

    public double getTemporaryAttackSpeedBonus(Player player) {
        return isQActive(player) ? Q_ATTACK_SPEED_BONUS : 0.0;
    }

    public int getPassiveHits(Player player) {
        return passiveHits.getOrDefault(player.getUniqueId(), 0);
    }

    public double getRapidVolleyRemainingSeconds(Player player) {
        Long end = qBuffEnds.get(player.getUniqueId());
        if (end == null) return 0.0;
        return Math.max(0L, end - System.currentTimeMillis()) / 1_000.0;
    }

    private Arrow launchArrow(Player player, Vector direction, double damage, double speed) {
        Arrow arrow = player.launchProjectile(Arrow.class, direction.clone().normalize().multiply(speed));
        arrow.setDamage(damage);
        arrow.setCritical(false);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        return arrow;
    }

    private void markScoutArrow(Arrow arrow) {
        arrow.getPersistentDataContainer().set(scoutArrowKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onScoutArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)
                || !(projectile.getShooter() instanceof Player player)
                || !projectile.getPersistentDataContainer().has(scoutArrowKey, PersistentDataType.BYTE)
                || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        int hits = passiveHits.merge(player.getUniqueId(), 1, Integer::sum);
        if (hits < 3) return;
        passiveHits.put(player.getUniqueId(), 0);
        var maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            event.setDamage(event.getDamage() + maxHealth.getValue() * 0.10);
            target.getWorld().spawnParticle(
                    Particle.CRIT, target.getLocation().add(0, 1, 0), 18, .4, .5, .4, .15);
            player.sendActionBar(Component.text("Scout Passive!", NamedTextColor.GOLD));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlink(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !isScout(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!ready(player, BLINK_COOLDOWN_ID)) return;

        Vector backward = player.getLocation().getDirection().setY(0);
        if (backward.lengthSquared() == 0.0) return;
        backward.normalize().multiply(-0.5);
        Location destination = player.getLocation().clone();
        for (int step = 0; step < 8; step++) {
            Location next = destination.clone().add(backward);
            if (!next.getBlock().isPassable()
                    || !next.clone().add(0, 1, 0).getBlock().isPassable()) break;
            destination = next;
        }
        player.teleport(destination);
        player.getWorld().spawnParticle(Particle.PORTAL,
                destination.clone().add(0, 1, 0), 28, .4, .7, .4, .2);
        player.playSound(player, Sound.ENTITY_ENDERMAN_TELEPORT, .8f, 1.4f);
        cooldowns.startCooldown(player, BLINK_COOLDOWN_ID, 3.0, 0.0);
    }

    @EventHandler
    public void onGroundMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        boolean standingOnBlock = player.getLocation().getBlock()
                .getRelative(BlockFace.DOWN).getType().isSolid();
        if (isScout(player) && standingOnBlock && canDoubleJump(player)) {
            player.setAllowFlight(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDoubleJump(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!isScout(player) || !canDoubleJump(player)) return;
        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() > 0.0) forward.normalize().multiply(.35);
        player.setVelocity(forward.setY(.72));
        player.getWorld().spawnParticle(
                Particle.CLOUD, player.getLocation(), 16, .35, .1, .35, .05);
        player.playSound(player, Sound.ENTITY_BAT_TAKEOFF, .8f, 1.25f);
    }

    private boolean canDoubleJump(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL
                || player.getGameMode() == GameMode.ADVENTURE;
    }

    private boolean isScout(Player player) {
        return itemManager.isCustomItem(player.getInventory().getItemInMainHand(), BOW_ID);
    }

    private boolean isQActive(Player player) {
        Long end = qBuffEnds.get(player.getUniqueId());
        if (end == null) return false;
        if (end <= System.currentTimeMillis()) {
            qBuffEnds.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private boolean ready(Player player, String cooldownId) {
        double remaining = cooldowns.getRemainingCooldownSeconds(player, cooldownId);
        if (remaining <= 0.0) return true;
        return false;
    }

    private Vector rotateY(Vector vector, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vector(
                vector.getX() * cos - vector.getZ() * sin,
                vector.getY(),
                vector.getX() * sin + vector.getZ() * cos).normalize();
    }

    @Override
    public void reset(Player player) {
        qBuffEnds.remove(player.getUniqueId());
        passiveHits.remove(player.getUniqueId());
        if (canDoubleJump(player)) player.setAllowFlight(false);
    }

    @Override
    public Component getSelectionHud(Player player) {
        long qRemaining = Math.max(0L,
                qBuffEnds.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis());
        String q = qRemaining > 0
                ? "BUFF %.1fs".formatted(qRemaining / 1_000.0)
                : cooldownText(player, Q_COOLDOWN_ID);
        return Component.text(
                "[Q] Rapid " + q
                        + " | [W] --"
                        + " | [E] Fan Volley " + cooldownText(player, E_COOLDOWN_ID)
                        + " | [R] --"
                        + " | Passive " + passiveHits.getOrDefault(player.getUniqueId(), 0) + "/3",
                NamedTextColor.GREEN);
    }

    private String cooldownText(Player player, String id) {
        double remaining = cooldowns.getRemainingCooldownSeconds(player, id);
        return remaining > 0.0 ? "%.1fs".formatted(remaining) : "READY";
    }
}
