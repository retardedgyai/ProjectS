package io.github.gyai.projects.beta.activation.track4;

import java.util.UUID;

/** Player lifecycle callbacks without Bukkit object retention. */
public interface BetaCapabilityLifecycleListener {
    void onJoin(UUID playerId);

    void onChannelRegistered(UUID playerId, String channel);

    void onQuit(UUID playerId);

    void onKick(UUID playerId);
}
