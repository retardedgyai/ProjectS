package io.github.gyai.projects.item;

import org.bukkit.Material;

public class Consumable extends CustomItem {

    private final int healAmount;

    public Consumable(
            String id,
            String displayName,
            Material material,
            int healAmount
    ) {
        super(id, displayName, material);
        this.healAmount = healAmount;
    }

    public int getHealAmount() {
        return healAmount;
    }
}