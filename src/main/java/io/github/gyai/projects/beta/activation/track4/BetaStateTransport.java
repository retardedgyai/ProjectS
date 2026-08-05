package io.github.gyai.projects.beta.activation.track4;

import java.util.List;
import java.util.UUID;

/** UUID-only transport/scheduling boundary; it never exposes or retains Bukkit objects. */
public interface BetaStateTransport {
    List<UUID> viewers();
    UUID visibleTarget(UUID viewerId);
    void send(UUID viewerId, String channel, byte[] packet);
    Cancellable schedule(Runnable task, long periodMillis);
    interface Cancellable { void cancel(); boolean cancelled(); }
}
