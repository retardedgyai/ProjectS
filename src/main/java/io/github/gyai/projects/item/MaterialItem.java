package io.github.gyai.projects.item;

import org.bukkit.Material;

public class MaterialItem extends CustomItem {

    public MaterialItem(
            String id,
            String displayName,
            Material material
    ) {
        super(id, displayName, material);
    }
}