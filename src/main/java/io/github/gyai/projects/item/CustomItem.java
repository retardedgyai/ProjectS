package io.github.gyai.projects.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class CustomItem {

    private final String id;
    private final String displayName;
    private final Material material;

    public CustomItem(String id, String displayName, Material material) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(displayName);
        item.setItemMeta(meta);

        return item;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }
}