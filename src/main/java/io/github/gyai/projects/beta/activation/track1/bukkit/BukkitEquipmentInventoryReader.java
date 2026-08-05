package io.github.gyai.projects.beta.activation.track1.bukkit;

import io.github.gyai.projects.beta.activation.track1.equipment.EquipmentScanEntry;
import io.github.gyai.projects.item.compatibility.BukkitLegacyPdcSource;
import io.github.gyai.projects.mod.UnknownModEntry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Synchronous inventory snapshotter. Returned entries contain no Player reference. */
public final class BukkitEquipmentInventoryReader {
    public List<EquipmentScanEntry> scan(Player player) {
        if (player == null) throw new IllegalArgumentException("player is required");
        ArrayList<EquipmentScanEntry> entries = new ArrayList<>();
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int index = 0; index < storage.length; index++) add(entries, "inventory-" + index, storage[index]);
        ItemStack[] armor = player.getInventory().getArmorContents();
        String[] names = {"boots", "legs", "chest", "head"};
        for (int index = 0; index < armor.length; index++) add(entries, names[index], armor[index]);
        add(entries, "offhand", player.getInventory().getItemInOffHand());
        return List.copyOf(entries);
    }

    private void add(List<EquipmentScanEntry> entries, String slot, ItemStack source) {
        if (source == null || source.getType().isAir()) return;
        ItemStack snapshot = source.clone();
        entries.add(new EquipmentScanEntry(slot, new BukkitLegacyPdcSource(snapshot),
                Optional.empty(), unknownMods(snapshot), snapshot.serializeAsBytes()));
    }

    private List<UnknownModEntry> unknownMods(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return List.of();
        return meta.getPersistentDataContainer().getKeys().stream()
                .filter(key -> key.getNamespace().equals("projects"))
                .filter(key -> key.getKey().startsWith("mod_") || key.getKey().startsWith("mod-"))
                .sorted(Comparator.comparing(NamespacedKey::asString))
                .limit(4)
                .map(key -> new UnknownModEntry(0, "legacy-pdc", 1, key.asString(),
                        key.asString().getBytes(StandardCharsets.UTF_8)))
                .toList();
    }
}
