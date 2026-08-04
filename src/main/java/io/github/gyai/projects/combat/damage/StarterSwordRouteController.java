package io.github.gyai.projects.combat.damage;

import java.time.Clock;
import java.util.Objects;

/** Temporary runtime toggle; it never rewrites config.yml. */
public final class StarterSwordRouteController {
    private final StarterSwordRouteTracker tracker;
    private final Clock clock;
    private volatile boolean enabled;

    public StarterSwordRouteController(
            boolean initiallyEnabled,
            StarterSwordRouteTracker tracker,
            Clock clock
    ) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.clock = Objects.requireNonNull(clock, "clock");
        tracker.reset(clock.instant());
        enabled = initiallyEnabled;
    }

    public synchronized void enable() {
        tracker.reset(clock.instant());
        enabled = true;
    }

    public synchronized void disable() {
        enabled = false;
    }

    public synchronized void reset() {
        tracker.reset(clock.instant());
    }

    public boolean enabled() {
        return enabled;
    }

    public synchronized void recordDecision(StarterSwordRouteDecision decision) {
        tracker.recordDecision(decision);
    }

    public synchronized void recordApplication(
            boolean authoritative,
            boolean attempted
    ) {
        tracker.recordApplication(authoritative, attempted);
    }

    public synchronized void recordAuthoritativeShadow(boolean matches) {
        tracker.recordAuthoritativeShadow(matches);
    }

    public synchronized StarterSwordRouteSnapshot snapshot() {
        return tracker.snapshot(enabled);
    }
}
