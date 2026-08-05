package io.github.gyai.projects.beta.activation.track4;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Main-thread transport. Player references remain callback-local. */
public final class BukkitBetaCapabilityAdvertisementTransport
        implements BetaCapabilityAdvertisementTransport {
    private final JavaPlugin plugin;

    public BukkitBetaCapabilityAdvertisementTransport(JavaPlugin plugin) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public List<UUID> onlinePlayers() {
        requirePrimaryThread();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .limit(io.github.gyai.projects.network.beta.BetaCapabilityPolicy
                        .wave3Defaults().maximumSessions())
                .toList();
    }

    @Override
    public Set<String> listeningChannels(UUID playerId) {
        requirePrimaryThread();
        Player player = Bukkit.getPlayer(playerId);
        return player == null || !player.isOnline()
                ? Set.of() : Set.copyOf(player.getListeningPluginChannels());
    }

    @Override
    public String worldName(UUID playerId) {
        requirePrimaryThread();
        Player player = Bukkit.getPlayer(playerId);
        return player == null || !player.isOnline()
                ? null : player.getWorld().getName();
    }

    @Override
    public void send(UUID playerId, String channel, byte[] packet) {
        requirePrimaryThread();
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()
                && player.getListeningPluginChannels().contains(channel)) {
            player.sendPluginMessage(plugin, channel, packet.clone());
        }
    }

    @Override
    public Cancellable scheduleMainThread(Runnable task) {
        BukkitTask scheduled = Bukkit.getScheduler().runTask(
                plugin, java.util.Objects.requireNonNull(task, "task"));
        return new Cancellable() {
            @Override
            public void cancel() {
                scheduled.cancel();
            }

            @Override
            public boolean cancelled() {
                return scheduled.isCancelled();
            }
        };
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("main thread required");
        }
    }
}
