package io.github.gyai.projects.manager;

import io.github.gyai.projects.item.CustomItem;
import io.github.gyai.projects.item.MaterialItem;
import io.github.gyai.projects.item.Weapon;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;

public class ItemManager {
    private final Map<String, CustomItem> items = new HashMap<>();
    private final NamespacedKey itemIdKey;

    public ItemManager(JavaPlugin plugin) {
        itemIdKey = new NamespacedKey(plugin, "item_id");
    }

    public void initialize(boolean painterMageEnabled) {
        items.clear();

        register(new Weapon(
                "starter_sword",
                "§bProjectSの剣",
                Material.IRON_SWORD,
                10,
                this::writeItemId
        ));
        if (painterMageEnabled) {
            register(new Weapon(
                    "painter_staff",
                    "§d画術師の杖",
                    Material.BLAZE_ROD,
                    7,
                    this::writeItemId
            ));
        }
        register(new Weapon(
                "starter_bow",
                "§a風追いの弓",
                Material.BOW,
                8,
                this::writeItemId
        ));
        register(new MaterialItem(
                EnhancementManager.ENHANCEMENT_MATERIAL_ID,
                "§b✦ 強化石",
                Material.AMETHYST_SHARD,
                this::writeItemId
        ));
        register(new MaterialItem(
                EnhancementManager.REPAIR_MATERIAL_ID,
                "§a◆ 修復の結晶",
                Material.PRISMARINE_CRYSTALS,
                this::writeItemId
        ));
    }

    private void writeItemId(ItemMeta meta, String id) {
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
    }

    public void register(CustomItem item) {
        items.put(item.getId(), item);
    }

    public CustomItem getItem(String id) {
        return items.get(id);
    }

    public Collection<CustomItem> getItems() {
        return java.util.List.copyOf(items.values());
    }

    public ItemStack createItem(String id) {
        CustomItem item = getItem(id);

        if (item == null) {
            return null;
        }

        return item.createItem();
    }

    public String getItemId(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }

    public boolean isCustomItem(ItemStack item, String id) {
        return id.equals(getItemId(item));
    }
}
