package io.github.gyai.projects.beta.activation.track4;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** UUID-only Bukkit boundary for capability advertisement delivery. */
public interface BetaCapabilityAdvertisementTransport {
    List<UUID> onlinePlayers();

    boolean online(UUID playerId);

    Set<String> listeningChannels(UUID playerId);

    String worldName(UUID playerId);

    void send(UUID playerId, String channel, byte[] packet);

    Cancellable scheduleMainThread(Runnable task);

    Cancellable scheduleRepeating(Runnable task, long periodMillis);

    interface Cancellable {
        void cancel();

        boolean cancelled();
    }
}
