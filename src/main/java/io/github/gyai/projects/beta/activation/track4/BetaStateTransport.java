package io.github.gyai.projects.beta.activation.track4;

import java.util.List;
import java.util.UUID;

/** UUID-only transport/scheduling boundary; it never exposes or retains Bukkit objects. */
public interface BetaStateTransport {
    List<UUID> viewers();
    UUID visibleTarget(UUID viewerId);
    void send(UUID viewerId, String channel, byte[] packet);

    /** Existing send implementations remain source-compatible while publishers can classify delivery. */
    default SendResult sendResult(UUID viewerId, String channel, byte[] packet) {
        send(viewerId, channel, packet);
        return SendResult.SENT;
    }

    Cancellable schedule(Runnable task, long periodMillis);

    enum SendResult {
        SENT,
        NOT_LISTENING,
        FAILED
    }

    interface Cancellable { void cancel(); boolean cancelled(); }
}
