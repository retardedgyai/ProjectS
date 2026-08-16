package io.github.gyai.projects.beta.activation.track4;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/** Idempotent registration boundary; construction performs no messenger calls. */
public final class BukkitBetaChannelRegistrar implements BetaChannelRegistrar {
    private final JavaPlugin plugin;
    private final PluginMessageListener incoming;
    private final java.util.Set<String> active = new java.util.LinkedHashSet<>();

    public BukkitBetaChannelRegistrar(JavaPlugin plugin, PluginMessageListener incoming) {
        this.plugin = java.util.Objects.requireNonNull(plugin);
        this.incoming = java.util.Objects.requireNonNull(incoming);
    }

    @Override public synchronized void register(String channel, Direction direction) {
        String key = channel + ":" + direction;
        if (!active.add(key)) return;
        if (direction == Direction.INCOMING) {
            plugin.getServer().getMessenger().registerIncomingPluginChannel(
                    plugin, channel, incoming);
        } else {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
        }
    }

    @Override public synchronized void unregister(String channel, Direction direction) {
        String key = channel + ":" + direction;
        if (!active.remove(key)) return;
        if (direction == Direction.INCOMING) {
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(
                    plugin, channel, incoming);
        } else {
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, channel);
        }
    }

    public synchronized int activeCount() { return active.size(); }
}
