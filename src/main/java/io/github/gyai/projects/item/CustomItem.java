package io.github.gyai.projects.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.function.BiConsumer;

public class CustomItem {

    private final String id;
    private final String displayName;
    private final Material material;
    private final BiConsumer<ItemMeta, String> idWriter;

    public CustomItem(String id, String displayName, Material material, BiConsumer<ItemMeta, String> idWriter) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.idWriter = idWriter;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(displayName));
        idWriter.accept(meta, id);
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
