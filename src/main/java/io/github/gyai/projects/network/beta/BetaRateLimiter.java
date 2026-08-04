package io.github.gyai.projects.network.beta;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;

public final class BetaRateLimiter implements AutoCloseable {
    private final int maximumKeys;
    private final Clock clock;
    private final LinkedHashMap<String, Bucket> buckets =
            new LinkedHashMap<>(16, 0.75f, true);
    private boolean closed;

    public BetaRateLimiter(int maximumKeys, Clock clock) {
        if (maximumKeys <= 0) throw new IllegalArgumentException("maximumKeys must be positive");
        this.maximumKeys = maximumKeys;
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public synchronized boolean tryAcquire(String key, BetaRateLimitPolicy policy) {
        if (closed || key == null || key.isBlank() || policy == null) return false;
        Instant now = clock.instant();
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= maximumKeys) {
                buckets.remove(buckets.keySet().iterator().next());
            }
            bucket = new Bucket(policy.burst(), now);
            buckets.put(key, bucket);
        }
        double elapsed = Math.max(0, (now.toEpochMilli() - bucket.updated.toEpochMilli()) / 1_000.0);
        bucket.tokens = Math.min(policy.burst(), bucket.tokens + elapsed * policy.requestsPerSecond());
        bucket.updated = now;
        if (bucket.tokens < 1.0) return false;
        bucket.tokens -= 1.0;
        return true;
    }

    public synchronized void clear() {
        buckets.clear();
    }

    @Override
    public synchronized void close() {
        closed = true;
        buckets.clear();
    }

    private static final class Bucket {
        private double tokens;
        private Instant updated;

        private Bucket(double tokens, Instant updated) {
            this.tokens = tokens;
            this.updated = updated;
        }
    }
}
