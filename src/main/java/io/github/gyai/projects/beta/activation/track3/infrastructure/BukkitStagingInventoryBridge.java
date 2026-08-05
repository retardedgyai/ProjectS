package io.github.gyai.projects.beta.activation.track3.infrastructure;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Main-thread Bukkit inventory boundary. The resolver is invoked per callback;
 * no Player or Inventory reference is retained by this adapter.
 */
public final class BukkitStagingInventoryBridge {
    private final Function<UUID, Player> playerResolver;

    public BukkitStagingInventoryBridge(Function<UUID, Player> playerResolver) {
        if (playerResolver == null) throw new IllegalArgumentException("player resolver is required");
        this.playerResolver = playerResolver;
    }

    public Optional<InventorySnapshot> snapshot(UUID playerId) {
        requirePrimaryThread();
        Player player = playerResolver.apply(playerId);
        if (player == null || !player.isOnline()) return Optional.empty();
        ItemStack[] contents = player.getInventory().getStorageContents();
        ItemStack[] copy = Arrays.stream(contents)
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
        Player player = playerResolver.apply(playerId);
        if (player == null || !player.isOnline()) return new MutationResult(false, "offline");
        if (slot < 0 || slot >= player.getInventory().getStorageContents().length
                || replacement == null) {
            return new MutationResult(false, "invalid-slot-or-output");
        }
        ItemStack current = player.getInventory().getItem(slot);
        if (!same(current, expected)) return new MutationResult(false, "revision-conflict");
        ItemStack rollback = current == null ? null : current.clone();
        try {
            player.getInventory().setItem(slot, replacement.clone());
            ItemStack committed = player.getInventory().getItem(slot);
            if (!same(committed, replacement)) throw new IllegalStateException("inventory rejected output");
            return new MutationResult(true, "committed");
        } catch (RuntimeException failure) {
            player.getInventory().setItem(slot, rollback);
            return new MutationResult(false, "rolled-back");
        }
    }

    private static boolean same(ItemStack first, ItemStack second) {
        if (first == null || second == null) return first == second;
        return first.getAmount() == second.getAmount() && first.isSimilar(second);
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
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
}
