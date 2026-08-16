package io.github.gyai.projects.beta.activation.track3.infrastructure;

import io.github.gyai.projects.beta.activation.track3.StagingEconomyCatalog;
import io.github.gyai.projects.beta.activation.track3.StagingEquipmentCodec;
import io.github.gyai.projects.beta.activation.track3.StagingEquipmentDocument;
import io.github.gyai.projects.beta.activation.track3.StagingEquipmentInspectionFormatter;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.mod.UnknownModEntry;
import io.github.gyai.projects.mod.ModEntry;
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
import java.util.StringJoiner;
import java.util.function.Supplier;

/** Bukkit edge for new staging items. It never reads or writes legacy PDC keys. */
public final class BukkitStagingEquipmentItemAdapter {
    private static final int MAXIMUM_LORE_BASE_STAT_ROLLS = 4;
    private final NamespacedKey markerKey;
    private final NamespacedKey payloadKey;
    private final NamespacedKey revisionKey;
    private final NamespacedKey itemIdKey;
    private final Supplier<ItemStack> stackFactory;
    private final StagingEquipmentCodec codec = new StagingEquipmentCodec();

    public BukkitStagingEquipmentItemAdapter(JavaPlugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("plugin is required");
        markerKey = new NamespacedKey(plugin, "beta_staging_equipment");
        payloadKey = new NamespacedKey(plugin, "beta_staging_equipment_payload");
        revisionKey = new NamespacedKey(plugin, "beta_staging_equipment_revision");
        itemIdKey = new NamespacedKey(plugin, "beta_staging_item_id");
        stackFactory = () -> new ItemStack(Material.IRON_SWORD);
    }

    /** Package-private key seam for metadata tests; production keys remain plugin-scoped. */
    BukkitStagingEquipmentItemAdapter(NamespacedKey markerKey, NamespacedKey payloadKey,
                                     NamespacedKey revisionKey, NamespacedKey itemIdKey, Supplier<ItemStack> stackFactory) {
        this.markerKey = java.util.Objects.requireNonNull(markerKey); this.payloadKey = java.util.Objects.requireNonNull(payloadKey);
        this.revisionKey = java.util.Objects.requireNonNull(revisionKey); this.itemIdKey = java.util.Objects.requireNonNull(itemIdKey);
        this.stackFactory = java.util.Objects.requireNonNull(stackFactory);
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
        ItemStack stack = java.util.Objects.requireNonNull(stackFactory.get(), "staging stack factory returned null");
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("[STAGING] " + displayId(item.itemId()),
                NamedTextColor.LIGHT_PURPLE));
        ArrayList<Component> lore = new ArrayList<>();
        lore.add(Component.text("Beta staging fixture", NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Equipment schema " + item.schemaVersion() + " / "
                        + item.tier().name() + " / ILv " + item.itemLevel(),
                NamedTextColor.GRAY));
        lore.add(Component.text("Item ID: " + item.itemId(), NamedTextColor.GRAY));
        lore.add(Component.text("Instance: " + item.instanceId().map(Object::toString)
                .orElse("(preview)"), NamedTextColor.GRAY));
        lore.add(Component.text("Rarity " + item.rarity() + " / Quality " + item.quality(),
                NamedTextColor.GRAY));
        lore.add(Component.text("Category " + item.category() + " / Slot " + item.slot().id(),
                NamedTextColor.GRAY));
        lore.add(Component.text("Binding " + item.binding() + " / Trade " + tradePolicy(item),
                NamedTextColor.GRAY));
        lore.add(Component.text("Enhancement +" + item.enhancementLevel()
                        + " / Broken " + item.broken(),
                NamedTextColor.AQUA));
        item.crafter().ifPresent(crafter -> {
            lore.add(Component.text("Crafter UUID: " + crafter.playerId(), NamedTextColor.GRAY));
            lore.add(Component.text("Crafter: " + crafter.displaySnapshot(), NamedTextColor.GRAY));
        });
        lore.add(Component.text(baseStats(item), NamedTextColor.GRAY));
        lore.add(Component.text("MOD slots: " + item.modSlots().size(), NamedTextColor.GRAY));
        item.modSlots().forEach(slot -> slot.entry().ifPresent(entry -> {
            if (entry instanceof ModEntry mod) {
                lore.add(Component.text("MOD " + mod.modId()
                        + " R" + mod.rank().value() + " " + mod.rolledValue()
                        + " #" + slot.index()
                        + " — " + StagingEquipmentInspectionFormatter.knownDisplayName(mod),
                        NamedTextColor.GOLD));
            } else if (entry instanceof UnknownModEntry unknown) {
                lore.add(Component.text("MOD #" + slot.index() + " " + unknown.modId()
                        + " (" + unknown.schemaId() + "/" + unknown.schemaVersion()
                        + ") UNKNOWN / 効果無効", NamedTextColor.YELLOW));
            }
        }));
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

    private static String baseStats(EquipmentItemV1 item) {
        StringJoiner values = new StringJoiner(", ", "Base stats: ", "");
        item.baseStatRolls().stream().limit(MAXIMUM_LORE_BASE_STAT_ROLLS)
                .forEach(roll -> values.add(roll.statId() + "=" + roll.value()));
        if (item.baseStatRolls().isEmpty()) values.add("none");
        if (item.baseStatRolls().size() > MAXIMUM_LORE_BASE_STAT_ROLLS) {
            values.add("+" + (item.baseStatRolls().size() - MAXIMUM_LORE_BASE_STAT_ROLLS) + " more");
        }
        return values.toString();
    }

    private static String tradePolicy(EquipmentItemV1 item) {
        if (io.github.gyai.projects.equipment.TradePolicy.DENY_ALL.equals(item.tradePolicy())) {
            return "DENY_ALL";
        }
        return "direct=" + item.tradePolicy().directTradeAllowed()
                + ",market=" + item.tradePolicy().marketAllowed()
                + ",dismantle=" + item.tradePolicy().dismantleAllowed();
    }
}
