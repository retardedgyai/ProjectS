package io.github.gyai.projects.monster.editor.catalog;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class HeadCatalogRateLimiter {
    private final int maximum;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();
    private long operations;
    private static final int MAXIMUM_SUBJECTS = 4_096;

    public HeadCatalogRateLimiter(int maximum, long windowMillis) {
        this(maximum, windowMillis, Clock.systemUTC());
    }

    public HeadCatalogRateLimiter(int maximum, long windowMillis, Clock clock) {
        this.maximum = Math.max(1, maximum);
        this.windowMillis = Math.max(1, windowMillis);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized boolean acquire(String subject) {
        String key = normalizeSubject(subject);
        long now = clock.millis();
        if ((++operations & 63L) == 0L || windows.size() >= MAXIMUM_SUBJECTS) {
            windows.entrySet().removeIf(entry ->
                    expired(now, entry.getValue().startedAt));
        }
        if (!windows.containsKey(key) && windows.size() >= MAXIMUM_SUBJECTS) {
            return false;
        }
        Window window = windows.get(key);
        if (window == null || expired(now, window.startedAt)) {
            windows.put(key, new Window(now, 1));
            return true;
        }
        if (window.count >= maximum) return false;
        windows.put(key, new Window(window.startedAt, window.count + 1));
        return true;
    }

    private boolean expired(long now, long startedAt) {
        if (now < startedAt) return false;
        if (startedAt > Long.MAX_VALUE - windowMillis) return false;
        return now >= startedAt + windowMillis;
    }

    private static String normalizeSubject(String subject) {
        String value = HeadCatalogEntry.bounded(subject, 128);
        return value.isBlank() ? "unknown" : value;
    }

    private record Window(long startedAt, int count) { }
}
