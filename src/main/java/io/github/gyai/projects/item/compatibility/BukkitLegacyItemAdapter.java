package io.github.gyai.projects.item.compatibility;

import org.bukkit.inventory.ItemStack;

public final class BukkitLegacyItemAdapter {
    private final LegacyItemCompatibilityReader reader = new LegacyItemCompatibilityReader();
    public LegacyItemReadResult read(ItemStack item) {
        return reader.read(new BukkitLegacyPdcSource(item));
    }
}
