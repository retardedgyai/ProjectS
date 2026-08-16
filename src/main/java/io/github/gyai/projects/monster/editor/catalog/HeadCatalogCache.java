package io.github.gyai.projects.monster.editor.catalog;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class HeadCatalogCache<T> {
    private final long ttlMillis;
    private final int maximumEntries;
    private final Clock clock;
    private final LinkedHashMap<String, Cached<T>> values =
            new LinkedHashMap<>(16, .75f, true);

    public HeadCatalogCache(long ttlMillis, int maximumEntries) {
        this(ttlMillis, maximumEntries, Clock.systemUTC());
    }

    public HeadCatalogCache(long ttlMillis, int maximumEntries, Clock clock) {
        this.ttlMillis = Math.max(1, ttlMillis);
        this.maximumEntries = Math.max(1, maximumEntries);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized Optional<T> get(String key) {
        if (key == null) return Optional.empty();
        Cached<T> cached = values.get(key);
        if (cached == null) return Optional.empty();
        if (expired(clock.millis(), cached.createdAt)) {
            values.remove(key);
            return Optional.empty();
        }
        return Optional.ofNullable(cached.value);
    }

    public synchronized void put(String key, T value) {
        if (key == null || value == null) return;
        long now = clock.millis();
        purgeExpired(now);
        values.put(key, new Cached<>(value, now));
        while (values.size() > maximumEntries) {
            String oldest = values.entrySet().iterator().next().getKey();
            values.remove(oldest);
        }
    }

    public synchronized int size() {
        purgeExpired(clock.millis());
        return values.size();
    }

    public synchronized void clear() { values.clear(); }

    private void purgeExpired(long now) {
        values.entrySet().removeIf(entry -> expired(now, entry.getValue().createdAt));
    }

    private boolean expired(long now, long createdAt) {
        if (now < createdAt) return false;
        if (createdAt > Long.MAX_VALUE - ttlMillis) return false;
        return now >= createdAt + ttlMillis;
    }

    private record Cached<T>(T value, long createdAt) { }
}
