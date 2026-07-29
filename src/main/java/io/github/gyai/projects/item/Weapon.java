package io.github.gyai.projects.item;

import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.BiConsumer;

public class Weapon extends CustomItem {

    private final double attackDamage;
    private final double attackSpeedBonus;

    public Weapon(
            String id,
            String displayName,
            Material material,
            double attackDamage,
            BiConsumer<ItemMeta, String> idWriter
    ) {
        this(id, displayName, material, attackDamage, 0.0, idWriter);
    }

    public Weapon(
            String id,
            String displayName,
            Material material,
            double attackDamage,
            double attackSpeedBonus,
            BiConsumer<ItemMeta, String> idWriter
    ) {
        super(id, displayName, material, idWriter);
        this.attackDamage = attackDamage;
        this.attackSpeedBonus = attackSpeedBonus;
    }

    public double getAttackDamage() {
        return attackDamage;
    }

    public double getAttackSpeedBonus() {
        return attackSpeedBonus;
    }
}
