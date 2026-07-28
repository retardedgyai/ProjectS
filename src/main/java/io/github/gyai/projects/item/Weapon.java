package io.github.gyai.projects.item;

import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.BiConsumer;

public class Weapon extends CustomItem {

    private final int attackDamage;

    public Weapon(
            String id,
            String displayName,
            Material material,
            int attackDamage,
            BiConsumer<ItemMeta, String> idWriter
    ) {
        super(id, displayName, material, idWriter);
        this.attackDamage = attackDamage;
    }

    public int getAttackDamage() {
        return attackDamage;
    }
}
