package io.github.gyai.projects.beta.activation.track3.infrastructure;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Main-thread Bukkit inventory boundary. The resolver is invoked per callback;
 * no Player or Inventory reference is retained by this adapter.
 */
public final class BukkitStagingInventoryBridge {
    private final BukkitInventoryAccess inventory;

    public BukkitStagingInventoryBridge(Function<UUID, Player> playerResolver) {
        if (playerResolver == null) throw new IllegalArgumentException("player resolver is required");
        this.inventory = new LiveBukkitInventoryAccess(playerResolver);
    }

    /** Package-private server-free seam; production keeps using the Player resolver above. */
    BukkitStagingInventoryBridge(BukkitInventoryAccess inventory) {
        this.inventory = java.util.Objects.requireNonNull(inventory, "inventory");
    }

    public Optional<InventorySnapshot> snapshot(UUID playerId) {
        requirePrimaryThread();
        Optional<ItemStack[]> contents = inventory.storage(playerId);
        if (contents.isEmpty()) return Optional.empty();
        ItemStack[] copy = Arrays.stream(contents.orElseThrow())
                .map(item -> item == null ? null : item.clone())
                .toArray(ItemStack[]::new);
        return Optional.of(new InventorySnapshot(copy));
    }

    public MutationResult replaceAtomically(
            UUID playerId,
            int slot,
            ItemStack expected,
            ItemStack replacement
    ) {
        requirePrimaryThread();
        Optional<ItemStack[]> storage = inventory.storage(playerId);
        if (storage.isEmpty()) return new MutationResult(false, "offline");
        if (slot < 0 || slot >= storage.orElseThrow().length
                || replacement == null) {
            return new MutationResult(false, "invalid-slot-or-output");
        }
        ItemStack current = storage.orElseThrow()[slot];
        if (!same(current, expected)) return new MutationResult(false, "revision-conflict");
        ItemStack[] replacementContents = copy(storage.orElseThrow());
        replacementContents[slot] = replacement.clone();
        try {
            if (!inventory.replaceStorage(playerId, storage.orElseThrow(), replacementContents)) {
                throw new IllegalStateException("inventory rejected output");
            }
            ItemStack committed = inventory.storage(playerId).orElseThrow()[slot];
            if (!same(committed, replacement)) throw new IllegalStateException("inventory rejected output");
            return new MutationResult(true, "committed");
        } catch (RuntimeException failure) {
            inventory.replaceStorage(playerId, replacementContents, storage.orElseThrow());
            return new MutationResult(false, "rolled-back");
        }
    }

    /** Compare-and-swap the storage snapshot; no Bukkit handles escape this call. */
    public MutationResult replaceStorageAtomically(
            UUID playerId, ItemStack[] expected, ItemStack[] replacement
    ) {
        requirePrimaryThread();
        Optional<ItemStack[]> storage = inventory.storage(playerId);
        if (storage.isEmpty()) return new MutationResult(false, "offline");
        ItemStack[] current = storage.orElseThrow();
        if (!sameContents(current, expected) || replacement == null
                || replacement.length != current.length) {
            return new MutationResult(false, "revision-conflict");
        }
        ItemStack[] before = copy(current);
        try {
            if (!inventory.replaceStorage(playerId, before, copy(replacement))
                    || !sameContents(inventory.storage(playerId).orElseThrow(), replacement)) {
                throw new IllegalStateException("inventory rejected mutation");
            }
            return new MutationResult(true, "committed");
        } catch (RuntimeException failure) {
            inventory.replaceStorage(playerId, replacement, before);
            return new MutationResult(false, "rolled-back");
        }
    }

    private static boolean same(ItemStack first, ItemStack second) {
        if (first == null || second == null) return first == second;
        return first.getAmount() == second.getAmount() && first.isSimilar(second);
    }

    private static boolean sameContents(ItemStack[] first, ItemStack[] second) {
        if (first == null || second == null || first.length != second.length) return false;
        for (int index = 0; index < first.length; index++) if (!same(first[index], second[index])) return false;
        return true;
    }

    private static ItemStack[] copy(ItemStack[] values) {
        return Arrays.stream(values == null ? new ItemStack[0] : values)
                .map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
    }

    private void requirePrimaryThread() {
        if (!inventory.isPrimaryThread()) {
            throw new IllegalStateException("Bukkit inventory access requires the main thread");
        }
    }

    public record InventorySnapshot(ItemStack[] contents) {
        public InventorySnapshot {
            contents = Arrays.stream(contents == null ? new ItemStack[0] : contents)
                    .map(item -> item == null ? null : item.clone())
                    .toArray(ItemStack[]::new);
        }

        @Override
        public ItemStack[] contents() {
            return Arrays.stream(contents)
                    .map(item -> item == null ? null : item.clone())
                    .toArray(ItemStack[]::new);
        }
    }

    public record MutationResult(boolean committed, String status) {
    }

    interface BukkitInventoryAccess {
        boolean isPrimaryThread();
        Optional<ItemStack[]> storage(UUID playerId);
        boolean replaceStorage(UUID playerId, ItemStack[] expected, ItemStack[] replacement);
    }

    private static final class LiveBukkitInventoryAccess implements BukkitInventoryAccess {
        private final Function<UUID, Player> playerResolver;

        private LiveBukkitInventoryAccess(Function<UUID, Player> playerResolver) {
            this.playerResolver = playerResolver;
        }

        @Override public boolean isPrimaryThread() { return Bukkit.isPrimaryThread(); }

        @Override public Optional<ItemStack[]> storage(UUID playerId) {
            Player player = playerResolver.apply(playerId);
            if (player == null || !player.isOnline()) return Optional.empty();
            return Optional.of(player.getInventory().getStorageContents());
        }

        @Override public boolean replaceStorage(UUID playerId, ItemStack[] expected, ItemStack[] replacement) {
            Player player = playerResolver.apply(playerId);
            if (player == null || !player.isOnline()) return false;
            PlayerInventory inventory = player.getInventory();
            if (!sameContents(inventory.getStorageContents(), expected)) return false;
            inventory.setStorageContents(replacement);
            return true;
        }
    }
}
