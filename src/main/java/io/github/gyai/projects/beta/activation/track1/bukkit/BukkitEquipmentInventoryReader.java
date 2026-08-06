package io.github.gyai.projects.beta.activation.track1.bukkit;

import io.github.gyai.projects.beta.activation.track1.equipment.EquipmentScanEntry;
import io.github.gyai.projects.beta.activation.track3.StagingEquipmentCodec;
import io.github.gyai.projects.item.compatibility.BukkitLegacyPdcSource;
import io.github.gyai.projects.mod.UnknownModEntry;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataType;
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
    private static final NamespacedKey STAGING_PAYLOAD = new NamespacedKey(
            "projects", "beta_staging_equipment_payload");
    private final StagingEquipmentCodec stagingCodec = new StagingEquipmentCodec();
    public List<EquipmentScanEntry> scan(Player player) {
        if (player == null) throw new IllegalArgumentException("player is required");
        return scan(player.getInventory().getStorageContents(), player.getInventory().getArmorContents(),
                player.getInventory().getItemInOffHand());
    }

    /** Package-private immutable-snapshot seam for server-free adapter verification. */
    List<EquipmentScanEntry> scan(ItemStack[] storage, ItemStack[] armor, ItemStack offHand) {
        ArrayList<EquipmentScanEntry> entries = new ArrayList<>();
        storage = storage == null ? new ItemStack[0] : storage;
        for (int index = 0; index < storage.length; index++) add(entries, "inventory-" + index, storage[index]);
        armor = armor == null ? new ItemStack[0] : armor;
        String[] names = {"boots", "legs", "chest", "head"};
        for (int index = 0; index < Math.min(armor.length, names.length); index++) add(entries, names[index], armor[index]);
        add(entries, "offhand", offHand);
        return List.copyOf(entries);
    }

    private void add(List<EquipmentScanEntry> entries, String slot, ItemStack source) {
        if (source == null || source.getType() == Material.AIR) return;
        ItemStack snapshot = source.clone();
        entries.add(new EquipmentScanEntry(slot, new BukkitLegacyPdcSource(snapshot),
                stagingEquipment(snapshot), unknownMods(snapshot), snapshot.serializeAsBytes()));
    }

    private Optional<io.github.gyai.projects.equipment.EquipmentItemV1> stagingEquipment(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Optional.empty();
        byte[] payload = meta.getPersistentDataContainer().get(STAGING_PAYLOAD,
                PersistentDataType.BYTE_ARRAY);
        if (payload == null) return Optional.empty();
        try {
            return Optional.of(stagingCodec.decode(payload).item());
        } catch (IllegalArgumentException invalidStagingPayload) {
            // A malformed untrusted PDC field is not a legacy conversion request.
            return Optional.empty();
        }
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
