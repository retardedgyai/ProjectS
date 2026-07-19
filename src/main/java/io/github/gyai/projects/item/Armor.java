package io.github.gyai.projects.item;

import org.bukkit.Material;

public class Armor extends CustomItem {

    private final int defense;

    public Armor(
            String id,
            String displayName,
            Material material,
            int defense
    ) {
        super(id, displayName, material);
        this.defense = defense;
    }

    public int getDefense() {
        return defense;
    }
}