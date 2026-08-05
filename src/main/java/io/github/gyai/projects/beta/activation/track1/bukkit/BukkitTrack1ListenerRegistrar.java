package io.github.gyai.projects.beta.activation.track1.bukkit;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/** Registration infrastructure supplied only by the future Integration Gate. */
public final class BukkitTrack1ListenerRegistrar implements Track1ListenerRegistrar {
    private final Plugin plugin;

    public BukkitTrack1ListenerRegistrar(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override public void register(String key, Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override public void unregister(String key, Listener listener) {
        HandlerList.unregisterAll(listener);
    }
}
