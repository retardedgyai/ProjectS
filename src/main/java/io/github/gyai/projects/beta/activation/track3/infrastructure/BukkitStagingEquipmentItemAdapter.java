package io.github.gyai.projects.beta.activation.track3.infrastructure;

import io.github.gyai.projects.beta.activation.track3.StagingEconomyCatalog;
import io.github.gyai.projects.beta.activation.track3.StagingEquipmentCodec;
import io.github.gyai.projects.beta.activation.track3.StagingEquipmentDocument;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.mod.UnknownModEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Bukkit edge for new staging items. It never reads or writes legacy PDC keys. */
public final class BukkitStagingEquipmentItemAdapter {
    private final NamespacedKey markerKey;
    private final NamespacedKey payloadKey;
    private final NamespacedKey revisionKey;
    private final NamespacedKey itemIdKey;
    private final StagingEquipmentCodec codec = new StagingEquipmentCodec();

    public BukkitStagingEquipmentItemAdapter(JavaPlugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("plugin is required");
        markerKey = new NamespacedKey(plugin, "beta_staging_equipment");
        payloadKey = new NamespacedKey(plugin, "beta_staging_equipment_payload");
        revisionKey = new NamespacedKey(plugin, "beta_staging_equipment_revision");
        itemIdKey = new NamespacedKey(plugin, "beta_staging_item_id");
    }

    /** Preview uses the supplied immutable item and never generates an instance UUID. */
    public ItemStack preview(EquipmentItemV1 item) {
        if (item.instanceId().isPresent()) {
            throw new IllegalArgumentException("preview must not carry an instance UUID");
        }
        return render(codec.encode(item, 0));
    }

    public ItemStack committed(StagingEquipmentDocument document) {
        if (document.item().instanceId().isEmpty()) {
            throw new IllegalArgumentException("committed staging item requires an instance UUID");
        }
        return render(document);
    }

    public Optional<StagingEquipmentDocument> read(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(markerKey, PersistentDataType.BYTE)) return Optional.empty();
        byte[] payload = pdc.get(payloadKey, PersistentDataType.BYTE_ARRAY);
        Long revision = pdc.get(revisionKey, PersistentDataType.LONG);
        String itemId = pdc.get(itemIdKey, PersistentDataType.STRING);
        if (payload == null || revision == null || itemId == null) return Optional.empty();
        StagingEquipmentDocument decoded = codec.decode(payload);
        if (decoded.revision() != revision || !decoded.item().itemId().equals(itemId)
                || !StagingEconomyCatalog.isStagingItem(itemId)) {
            throw new IllegalArgumentException("staging PDC envelope mismatch");
        }
        return Optional.of(decoded);
    }

    private ItemStack render(StagingEquipmentDocument document) {
        EquipmentItemV1 item = document.item();
        ItemStack stack = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("[STAGING] " + displayId(item.itemId()),
                NamedTextColor.LIGHT_PURPLE));
        ArrayList<Component> lore = new ArrayList<>();
        lore.add(Component.text("Beta staging fixture", NamedTextColor.DARK_GRAY));
        lore.add(Component.text(item.tier().name() + " / ILv " + item.itemLevel(),
                NamedTextColor.GRAY));
        lore.add(Component.text("Enhancement +" + item.enhancementLevel(),
                NamedTextColor.AQUA));
        if (item.broken()) lore.add(Component.text("BROKEN", NamedTextColor.DARK_RED));
        long unsupported = item.modSlots().stream()
                .flatMap(slot -> slot.entry().stream())
                .filter(UnknownModEntry.class::isInstance).count();
        if (unsupported > 0) {
            lore.add(Component.text("Unsupported MOD disabled: " + unsupported,
                    NamedTextColor.YELLOW));
        }
        meta.lore(List.copyOf(lore));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(payloadKey, PersistentDataType.BYTE_ARRAY, document.payload());
        pdc.set(revisionKey, PersistentDataType.LONG, document.revision());
        pdc.set(itemIdKey, PersistentDataType.STRING, item.itemId());
        stack.setItemMeta(meta);
        return stack;
    }

    private static String displayId(String itemId) {
        int slash = itemId.lastIndexOf('/');
        return slash < 0 ? itemId : itemId.substring(slash + 1);
    }
}
