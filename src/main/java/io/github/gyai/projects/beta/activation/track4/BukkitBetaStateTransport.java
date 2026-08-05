package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.dummy.TrainingDummyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Main-thread Bukkit boundary. Player and Entity references are callback-local only. */
public final class BukkitBetaStateTransport implements BetaStateTransport {
    private final JavaPlugin plugin;
    private final TrainingDummyManager dummies;

    public BukkitBetaStateTransport(JavaPlugin plugin, TrainingDummyManager dummies) {
        this.plugin = java.util.Objects.requireNonNull(plugin);
        this.dummies = java.util.Objects.requireNonNull(dummies);
    }

    @Override public List<UUID> viewers() {
        requirePrimaryThread();
        return Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId)
                .limit(ElementSnapshotProtocolAdapter.MAXIMUM_VIEWERS).toList();
    }

    @Override public UUID visibleTarget(UUID viewerId) {
        requirePrimaryThread();
        Player player = Bukkit.getPlayer(viewerId);
        if (player == null || !player.isOnline()) return null;
        Entity target = player.getTargetEntity(32);
        return target != null && target.isValid() && dummies.isTrainingDummy(target)
                && target.getWorld().equals(player.getWorld())
                ? target.getUniqueId() : null;
    }

    @Override public void send(UUID viewerId, String channel, byte[] packet) {
        sendResult(viewerId, channel, packet);
    }

    @Override public SendResult sendResult(UUID viewerId, String channel, byte[] packet) {
        requirePrimaryThread();
        Player player = Bukkit.getPlayer(viewerId);
        if (player == null || !player.isOnline()) return SendResult.FAILED;
        Set<String> listening = player.getListeningPluginChannels();
        if (listening == null || !listening.contains(channel)) return SendResult.NOT_LISTENING;
        try {
            player.sendPluginMessage(plugin, channel, packet.clone());
            return SendResult.SENT;
        } catch (RuntimeException failure) {
            return SendResult.FAILED;
        }
    }

    @Override public Cancellable schedule(Runnable task, long periodMillis) {
        if (periodMillis < 50) throw new IllegalArgumentException("period is too short");
        BukkitTask scheduled = Bukkit.getScheduler().runTaskTimer(
                plugin, task, 1L, Math.max(1L, periodMillis / 50L));
        return new Cancellable() {
            @Override public void cancel() { scheduled.cancel(); }
            @Override public boolean cancelled() { return scheduled.isCancelled(); }
        };
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("main thread required");
    }
}
