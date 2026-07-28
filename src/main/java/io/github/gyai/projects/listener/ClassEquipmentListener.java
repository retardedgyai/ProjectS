package io.github.gyai.projects.listener;

import io.github.gyai.projects.combat.classsystem.ClassManager;
import io.github.gyai.projects.combat.resource.ResourceManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClassEquipmentListener implements Listener {
    private final JavaPlugin plugin;
    private final ClassManager classManager;
    private final ResourceManager resourceManager;

    public ClassEquipmentListener(JavaPlugin plugin, ClassManager classManager, ResourceManager resourceManager) {
        this.plugin = plugin;
        this.classManager = classManager;
        this.resourceManager = resourceManager;
    }

    @EventHandler public void onHeld(PlayerItemHeldEvent event) { updateNextTick(event.getPlayer()); }
    @EventHandler public void onDrop(PlayerDropItemEvent event) { updateNextTick(event.getPlayer()); }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent event) { updateNextTick(event.getPlayer()); }
    @EventHandler public void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) updateNextTick(player);
    }
    @EventHandler public void onDeath(PlayerDeathEvent event) {
        classManager.removePlayer(event.getPlayer());
        resourceManager.removePlayer(event.getPlayer());
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) { updateNextTick(event.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        classManager.removePlayer(event.getPlayer());
        updateNextTick(event.getPlayer());
    }

    private void updateNextTick(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> classManager.update(player));
    }
}
