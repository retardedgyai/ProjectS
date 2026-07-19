package io.github.gyai.projects.item;

import org.bukkit.Material;

public class Weapon extends CustomItem {

    private final int attackDamage;

    public Weapon(
            String id,
            String displayName,
            Material material,
            int attackDamage
    ) {
        super(id, displayName, material);
        this.attackDamage = attackDamage;
    }

    public int getAttackDamage() {
        return attackDamage;
    }
}