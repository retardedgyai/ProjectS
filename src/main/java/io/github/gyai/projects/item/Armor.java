package io.github.gyai.projects.item;

import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.BiConsumer;

public class Armor extends CustomItem {

    private final int defense;

    public Armor(
            String id,
            String displayName,
            Material material,
            int defense,
            BiConsumer<ItemMeta, String> idWriter
    ) {
        super(id, displayName, material, idWriter);
        this.defense = defense;
    }

    public int getDefense() {
        return defense;
    }
}
