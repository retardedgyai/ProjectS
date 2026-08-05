package io.github.gyai.projects.beta.activation.track4;

import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Bukkit listener registration with UUID-only delegation. */
public final class BukkitBetaCapabilityLifecycleRegistrar
        implements BetaCapabilityLifecycleRegistrar {
    private final JavaPlugin plugin;
    private BukkitListener active;

    public BukkitBetaCapabilityLifecycleRegistrar(JavaPlugin plugin) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public synchronized void register(BetaCapabilityLifecycleListener listener) {
        if (active != null) return;
        active = new BukkitListener(java.util.Objects.requireNonNull(listener, "listener"));
        plugin.getServer().getPluginManager().registerEvents(active, plugin);
    }

    @Override
    public synchronized void unregister() {
        if (active == null) return;
        HandlerList.unregisterAll(active);
        active = null;
    }

    public synchronized boolean registered() {
        return active != null;
    }

    private static final class BukkitListener implements Listener {
        private final BetaCapabilityLifecycleListener delegate;

        private BukkitListener(BetaCapabilityLifecycleListener delegate) {
            this.delegate = delegate;
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            delegate.onJoin(event.getPlayer().getUniqueId());
        }

        @EventHandler
        public void onRegister(PlayerRegisterChannelEvent event) {
            delegate.onChannelRegistered(
                    event.getPlayer().getUniqueId(), event.getChannel());
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            delegate.onQuit(event.getPlayer().getUniqueId());
        }

        @EventHandler
        public void onKick(PlayerKickEvent event) {
            delegate.onKick(event.getPlayer().getUniqueId());
        }
    }
}
