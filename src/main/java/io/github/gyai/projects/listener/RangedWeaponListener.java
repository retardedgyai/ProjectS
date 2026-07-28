package io.github.gyai.projects.listener;

import io.github.gyai.projects.manager.EnhancementManager;
import io.github.gyai.projects.manager.ItemManager;
import io.github.gyai.projects.combat.classsystem.ScoutController;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RangedWeaponListener implements Listener {
    private static final String BOW_ID = "starter_bow";
    private static final long BASE_ATTACK_INTERVAL_MILLIS = 800L;
    private static final long INPUT_HOLD_GRACE_MILLIS = 650L;
    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final EnhancementManager enhancementManager;
    private final ScoutController scoutController;
    private final Map<UUID, Long> nextAttackAt = new HashMap<>();
    private final Map<UUID, Long> firingUntil = new HashMap<>();
    private final Map<UUID, Boolean> clientFiring = new HashMap<>();

    public RangedWeaponListener(
            JavaPlugin plugin,
            ItemManager itemManager,
            EnhancementManager enhancementManager,
            ScoutController scoutController
    ) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.enhancementManager = enhancementManager;
        this.scoutController = scoutController;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAutomaticFire, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;

        Player player = event.getPlayer();
        ItemStack bow = player.getInventory().getItemInMainHand();
        if (!itemManager.isCustomItem(bow, BOW_ID)) return;
        firingUntil.put(player.getUniqueId(),
                System.currentTimeMillis() + INPUT_HOLD_GRACE_MILLIS);
        tryFire(player);
    }

    private void tickAutomaticFire() {
        long now = System.currentTimeMillis();
        firingUntil.entrySet().removeIf(entry -> {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || now > entry.getValue()) {
                return true;
            }
            tryFire(player);
            return false;
        });
        clientFiring.entrySet().removeIf(entry -> {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()
                    || !itemManager.isCustomItem(
                    player.getInventory().getItemInMainHand(), BOW_ID)) {
                return true;
            }
            tryFire(player);
            return false;
        });
    }

    public void setFiring(Player player, boolean firing) {
        UUID playerId = player.getUniqueId();
        if (!firing) {
            clientFiring.remove(playerId);
            return;
        }
        if (!itemManager.isCustomItem(
                player.getInventory().getItemInMainHand(), BOW_ID)) {
            return;
        }
        clientFiring.put(playerId, true);
        firingUntil.remove(playerId);
        tryFire(player);
    }

    private void tryFire(Player player) {
        ItemStack bow = player.getInventory().getItemInMainHand();
        if (!itemManager.isCustomItem(bow, BOW_ID)) {
            return;
        }
        if (enhancementManager.isBroken(bow)) {
            player.sendActionBar(Component.text("この弓は破損しています", NamedTextColor.RED));
            return;
        }

        long now = System.currentTimeMillis();
        long readyAt = nextAttackAt.getOrDefault(player.getUniqueId(), 0L);
        if (now < readyAt) return;

        double attackSpeedBonus = enhancementManager.getTotalAttackSpeedBonus(player, bow)
                + scoutController.getTemporaryAttackSpeedBonus(player);
        long interval = Math.max(100L,
                Math.round(BASE_ATTACK_INTERVAL_MILLIS / (1.0 + attackSpeedBonus)));
        nextAttackAt.put(player.getUniqueId(), now + interval);
        player.setCooldown(Material.BOW, Math.max(1, (int) Math.ceil(interval / 50.0)));

        Arrow arrow = player.launchProjectile(
                Arrow.class, player.getEyeLocation().getDirection().multiply(3.2));
        double damage = enhancementManager.getAttackPower(player, bow);
        arrow.setDamage(damage);
        arrow.setCritical(false);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        scoutController.onBasicArrowFired(player, arrow, damage);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.2f);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBowMelee(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player
                && itemManager.isCustomItem(player.getInventory().getItemInMainHand(), BOW_ID)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !itemManager.isCustomItem(event.getItem(), BOW_ID)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        nextAttackAt.remove(event.getPlayer().getUniqueId());
        firingUntil.remove(event.getPlayer().getUniqueId());
        clientFiring.remove(event.getPlayer().getUniqueId());
    }
}
