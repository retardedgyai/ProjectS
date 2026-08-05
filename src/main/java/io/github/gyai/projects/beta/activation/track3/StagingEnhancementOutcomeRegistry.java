package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.enhancement.v2.EnhancementOutcome;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded one-shot operator fixture. Values are consumed only after reservation. */
public final class StagingEnhancementOutcomeRegistry implements AutoCloseable {
    public static final int MAXIMUM_PLAYERS = 128;
    private final LinkedHashMap<UUID, EnhancementOutcome> overrides = new LinkedHashMap<>();
    private boolean closed;

    public synchronized void set(UUID playerId, EnhancementOutcome outcome) {
        requireOpen();
        if (playerId == null || outcome == null || outcome == EnhancementOutcome.REJECTED) {
            throw new IllegalArgumentException("invalid staging enhancement outcome");
        }
        if (!overrides.containsKey(playerId) && overrides.size() >= MAXIMUM_PLAYERS) {
            throw new IllegalStateException("staging enhancement override capacity reached");
        }
        overrides.put(playerId, outcome);
    }

    public synchronized EnhancementOutcome peek(UUID playerId) {
        return overrides.getOrDefault(playerId, EnhancementOutcome.NO_CHANGE);
    }

    public synchronized EnhancementOutcome consume(UUID playerId) {
        requireOpen();
        EnhancementOutcome value = overrides.remove(playerId);
        return value == null ? EnhancementOutcome.NO_CHANGE : value;
    }

    public synchronized void logout(UUID playerId) {
        overrides.remove(playerId);
    }

    public synchronized Map<UUID, EnhancementOutcome> snapshot() {
        return Map.copyOf(overrides);
    }

    public synchronized void clear() {
        overrides.clear();
    }

    @Override
    public synchronized void close() {
        overrides.clear();
        closed = true;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("outcome registry is closed");
    }
}
