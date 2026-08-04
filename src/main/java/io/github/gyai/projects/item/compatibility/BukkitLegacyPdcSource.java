package io.github.gyai.projects.item.compatibility;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Bukkit boundary that only exposes PDC get operations. */
public final class BukkitLegacyPdcSource implements LegacyPdcSource {
    private final ItemStack item;
    private final String namespace;
    private final Function<ItemStack, String> materialIdentity;

    public BukkitLegacyPdcSource(ItemStack item) { this(item, "projects"); }
    public BukkitLegacyPdcSource(ItemStack item, String namespace) {
        this(item, namespace, value -> value.getType().getKey().toString());
    }
    BukkitLegacyPdcSource(ItemStack item, String namespace,
                          Function<ItemStack, String> materialIdentity) {
        this.item = Objects.requireNonNull(item, "item");
        if (namespace == null || !namespace.matches("[a-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("invalid namespace");
        }
        this.namespace = namespace;
        this.materialIdentity = Objects.requireNonNull(materialIdentity, "materialIdentity");
    }
    @Override public String materialIdentity() { return materialIdentity.apply(item); }
    @Override public boolean contains(String key) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(
                new NamespacedKey(namespace, key));
    }
    @Override public Optional<String> stringValue(String key) { return value(key, PersistentDataType.STRING); }
    @Override public Optional<Integer> integerValue(String key) { return value(key, PersistentDataType.INTEGER); }
    @Override public Optional<Byte> byteValue(String key) { return value(key, PersistentDataType.BYTE); }
    @Override public Optional<Double> doubleValue(String key) { return value(key, PersistentDataType.DOUBLE); }
    private <P, C> Optional<C> value(String key, PersistentDataType<P, C> type) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Optional.empty();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        return Optional.ofNullable(data.get(new NamespacedKey(namespace, key), type));
    }
}
