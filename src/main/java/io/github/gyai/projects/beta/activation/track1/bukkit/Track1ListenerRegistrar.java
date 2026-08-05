package io.github.gyai.projects.beta.activation.track1.bukkit;

import org.bukkit.event.Listener;

public interface Track1ListenerRegistrar {
    void register(String key, Listener listener);
    void unregister(String key, Listener listener);
}
