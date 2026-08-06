package io.github.gyai.projects.beta.activation.track3.infrastructure;

import io.github.gyai.projects.beta.activation.track3.StagingEconomyCatalog;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Physical representation of a Track 3 staging resource.  The PDC identity,
 * rather than a vanilla {@link Material}, is authoritative; the materials are
 * only the visible fixture used by the current staging slice.
 */
public final class BukkitStagingResourceItemAdapter {
    private static final NamespacedKey RESOURCE_ID = new NamespacedKey(
            "projects", "beta_staging_resource_id");
    private final BiFunction<Material, Integer, ItemStack> stackFactory;

    public BukkitStagingResourceItemAdapter() {
        this(ItemStack::new);
    }

    BukkitStagingResourceItemAdapter(BiFunction<Material, Integer, ItemStack> stackFactory) {
        this.stackFactory = java.util.Objects.requireNonNull(stackFactory);
    }

    public ItemStack create(String itemId, int amount) {
        if (amount < 1 || amount > 64) throw new IllegalArgumentException("invalid staging resource amount");
        ItemStack result = stackFactory.apply(material(itemId), amount);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) throw new IllegalStateException("staging resource lacks item meta");
        meta.getPersistentDataContainer().set(RESOURCE_ID, PersistentDataType.STRING, itemId);
        result.setItemMeta(meta);
        return result;
    }

    public Optional<String> itemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        String itemId = stack.getItemMeta().getPersistentDataContainer().get(RESOURCE_ID,
                PersistentDataType.STRING);
        if (itemId == null) return Optional.empty();
        try {
            return stack.getType() == material(itemId) ? Optional.of(itemId) : Optional.empty();
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    public boolean matches(ItemStack stack, String itemId) {
        return itemId(stack).filter(itemId::equals).isPresent();
    }

    private static Material material(String itemId) {
        return switch (itemId) {
            case StagingEconomyCatalog.IRON_ORE -> Material.RAW_IRON;
            case StagingEconomyCatalog.IRON_INGOT -> Material.IRON_INGOT;
            case StagingEconomyCatalog.TEST_TOKEN -> Material.PAPER;
            default -> throw new IllegalArgumentException("unsupported live staging resource");
        };
    }
}
