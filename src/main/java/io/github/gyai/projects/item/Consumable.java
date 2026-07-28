package io.github.gyai.projects.item;

import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.BiConsumer;

public class Consumable extends CustomItem {

    private final int healAmount;

    public Consumable(
            String id,
            String displayName,
            Material material,
            int healAmount,
            BiConsumer<ItemMeta, String> idWriter
    ) {
        super(id, displayName, material, idWriter);
        this.healAmount = healAmount;
    }

    public int getHealAmount() {
        return healAmount;
    }
}
