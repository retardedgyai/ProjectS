package io.github.gyai.projects.beta.activation.track2;

import java.util.LinkedHashMap;

/** Bounded, entity-free limiter shared by Bukkit visual fallbacks. */
final class BoundedVisualRateLimiter {
    private final int maximumKeys;
    private final long intervalMillis;
    private final LinkedHashMap<String, Long> lastAt = new LinkedHashMap<>();

    BoundedVisualRateLimiter(int maximumKeys, long intervalMillis) {
        if (maximumKeys < 1 || intervalMillis < 1) throw new IllegalArgumentException();
        this.maximumKeys = maximumKeys;
        this.intervalMillis = intervalMillis;
    }

    synchronized boolean admit(String key, long nowMillis) {
        if (key == null || key.isBlank() || key.length() > 128 || nowMillis < 0) return false;
        Long previous = lastAt.get(key);
        if (previous != null && nowMillis - previous < intervalMillis) return false;
        if (!lastAt.containsKey(key) && lastAt.size() >= maximumKeys) {
            lastAt.remove(lastAt.keySet().iterator().next());
        }
        lastAt.put(key, nowMillis);
        return true;
    }

    synchronized int size() { return lastAt.size(); }
    synchronized void clear() { lastAt.clear(); }
}
