package io.github.gyai.projects.listener;

import io.github.gyai.projects.manager.EnhancementManager;
import io.github.gyai.projects.manager.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class EnhancementListener implements Listener {
    private static final int WEAPON_SLOT = 13;
    private static final int ACTION_SLOT = 31;
    private final ItemManager itemManager;
    private final EnhancementManager enhancementManager;
    private final JavaPlugin plugin;

    public EnhancementListener(
            JavaPlugin plugin,
            ItemManager itemManager,
            EnhancementManager enhancementManager
    ) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.enhancementManager = enhancementManager;
    }

    public void open(Player player) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!enhancementManager.isWeapon(weapon)) {
            player.sendMessage(Component.text("強化したい武器をメインハンドに持ってください。", NamedTextColor.RED));
            return;
        }
        enhancementManager.refreshWeapon(weapon);
        player.openInventory(createMenu(player, weapon));
    }

    private Inventory createMenu(Player player, ItemStack weapon) {
        Inventory inventory = Bukkit.createInventory(new EnhancementHolder(), 45,
                Component.text("✦ 武器強化工房 ✦", NamedTextColor.DARK_PURPLE));
        ItemStack border = menuItem(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "));
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, border);

        inventory.setItem(WEAPON_SLOT, weapon.clone());
        int level = enhancementManager.getLevel(weapon);
        boolean broken = enhancementManager.isBroken(weapon);
        inventory.setItem(22, menuItem(Material.BOOK,
                Component.text("現在の武器性能", NamedTextColor.AQUA),
                List.of(
                        Component.text("強化値  +" + level, NamedTextColor.WHITE),
                        Component.text("最終攻撃力  %.2f".formatted(
                                enhancementManager.getAttackPower(player, weapon)), NamedTextColor.RED),
                        Component.text("攻撃速度  %+.1f%%".formatted(
                                enhancementManager.getTotalAttackSpeedBonus(player, weapon) * 100),
                                NamedTextColor.YELLOW))));

        if (broken) {
            int cost = enhancementManager.getRepairCost(level);
            inventory.setItem(ACTION_SLOT, menuItem(Material.ANVIL,
                    Component.text("武器を修復", NamedTextColor.GREEN),
                    List.of(
                            Component.text("必要: 修復の結晶 ×" + cost, NamedTextColor.WHITE),
                            Component.text("所持: " + countMaterial(player, EnhancementManager.REPAIR_MATERIAL_ID),
                                    NamedTextColor.GRAY),
                            Component.text("クリックして修復", NamedTextColor.YELLOW))));
        } else if (level >= EnhancementManager.MAX_LEVEL) {
            inventory.setItem(ACTION_SLOT, menuItem(Material.NETHER_STAR,
                    Component.text("最大強化達成", NamedTextColor.GOLD),
                    List.of(Component.text("+30まで強化されています", NamedTextColor.YELLOW))));
        } else {
            int target = level + 1;
            int cost = enhancementManager.getMaterialCost(target);
            inventory.setItem(ACTION_SLOT, menuItem(Material.SMITHING_TABLE,
                    Component.text("+" + target + "へ強化", NamedTextColor.GREEN),
                    List.of(
                            Component.text("成功率: %.1f%%".formatted(
                                    enhancementManager.getSuccessChance(target)), NamedTextColor.AQUA),
                            Component.text("必要: 強化石 ×" + cost, NamedTextColor.WHITE),
                            Component.text("所持: " + countMaterial(player, EnhancementManager.ENHANCEMENT_MATERIAL_ID),
                                    NamedTextColor.GRAY),
                            Component.text(level >= 15
                                            ? "失敗時の破損率: %.1f%%".formatted(
                                            enhancementManager.getBreakChance(level))
                                            : "失敗しても強化値は下がりません",
                                    level >= 15 ? NamedTextColor.RED : NamedTextColor.DARK_GRAY),
                            Component.text("クリックして強化", NamedTextColor.YELLOW))));
        }
        inventory.setItem(40, menuItem(Material.BARRIER,
                Component.text("閉じる", NamedTextColor.RED)));
        return inventory;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof EnhancementHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() == 40) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() != ACTION_SLOT) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!enhancementManager.isWeapon(weapon)) {
            player.closeInventory();
            player.sendMessage(Component.text("メインハンドの武器が変更されたため中止しました。", NamedTextColor.RED));
            return;
        }
        if (enhancementManager.isBroken(weapon)) {
            repair(player, weapon);
        } else {
            enhance(player, weapon);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()
                    || !(player.getOpenInventory().getTopInventory().getHolder()
                    instanceof EnhancementHolder)) {
                return;
            }
            ItemStack currentWeapon = player.getInventory().getItemInMainHand();
            if (enhancementManager.isWeapon(currentWeapon)) {
                player.openInventory(createMenu(player, currentWeapon));
            } else {
                player.closeInventory();
            }
        });
    }

    private void enhance(Player player, ItemStack weapon) {
        int level = enhancementManager.getLevel(weapon);
        if (level >= EnhancementManager.MAX_LEVEL) return;
        int target = level + 1;
        int cost = enhancementManager.getMaterialCost(target);
        if (!consumeMaterial(player, EnhancementManager.ENHANCEMENT_MATERIAL_ID, cost)) {
            player.sendMessage(Component.text("強化石が足りません。", NamedTextColor.RED));
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .7f);
            return;
        }

        if (roll(enhancementManager.getSuccessChance(target))) {
            enhancementManager.setLevel(weapon, target);
            player.sendMessage(Component.text("強化成功！ 武器が +" + target + " になりました。", NamedTextColor.GREEN));
            player.playSound(player, Sound.BLOCK_ANVIL_USE, 1f, 1.35f);
        } else if (roll(enhancementManager.getBreakChance(level))) {
            enhancementManager.setBroken(weapon, true);
            player.sendMessage(Component.text("強化失敗…武器が破損しました！", NamedTextColor.DARK_RED));
            player.playSound(player, Sound.ENTITY_ITEM_BREAK, 1f, .7f);
        } else {
            player.sendMessage(Component.text("強化に失敗しました。強化値は維持されます。", NamedTextColor.RED));
            player.playSound(player, Sound.BLOCK_ANVIL_LAND, .8f, .75f);
        }
    }

    private void repair(Player player, ItemStack weapon) {
        int cost = enhancementManager.getRepairCost(enhancementManager.getLevel(weapon));
        if (!consumeMaterial(player, EnhancementManager.REPAIR_MATERIAL_ID, cost)) {
            player.sendMessage(Component.text("修復の結晶が足りません。", NamedTextColor.RED));
            return;
        }
        enhancementManager.setBroken(weapon, false);
        player.sendMessage(Component.text("武器を完全に修復しました。", NamedTextColor.GREEN));
        player.playSound(player, Sound.BLOCK_ANVIL_USE, 1f, 1f);
    }

    private boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble(100.0) < chance;
    }

    private int countMaterial(Player player, String id) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (itemManager.isCustomItem(item, id)) count += item.getAmount();
        }
        return count;
    }

    private boolean consumeMaterial(Player player, String id, int amount) {
        if (countMaterial(player, id) < amount) return false;
        int remaining = amount;
        int storageSize = player.getInventory().getStorageContents().length;
        for (int slot = 0; slot < storageSize && remaining > 0; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!itemManager.isCustomItem(item, id)) continue;
            int used = Math.min(remaining, item.getAmount());
            int newAmount = item.getAmount() - used;
            if (newAmount <= 0) {
                player.getInventory().setItem(slot, null);
            } else {
                item.setAmount(newAmount);
                player.getInventory().setItem(slot, item);
            }
            remaining -= used;
        }
        return true;
    }

    private ItemStack menuItem(Material material, Component name) {
        return menuItem(material, name, List.of());
    }

    private ItemStack menuItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof EnhancementHolder) event.setCancelled(true);
    }

    private static final class EnhancementHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            throw new UnsupportedOperationException("GUI marker");
        }
    }
}
