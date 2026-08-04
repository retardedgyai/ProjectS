package io.github.gyai.projects.combat.damage;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.logging.Logger;

/** Runtime on/off state and session lifecycle; config remains unchanged. */
public final class DamageShadowValidationController {
    private final DamageShadowValidationTracker tracker;
    private final DamageShadowValidationExporter exporter;
    private final Path exportDirectory;
    private final Clock clock;
    private final Logger logger;
    private volatile boolean enabled;

    public DamageShadowValidationController(
            boolean initiallyEnabled,
            DamageShadowValidationTracker tracker,
            DamageShadowValidationExporter exporter,
            Path exportDirectory,
            Clock clock,
            Logger logger
    ) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
        this.exportDirectory = Objects.requireNonNull(
                exportDirectory, "exportDirectory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
        tracker.reset(clock.instant());
        enabled = initiallyEnabled;
    }

    public synchronized void enable() {
        tracker.reset(clock.instant());
        enabled = true;
    }

    public synchronized DamageShadowValidationSnapshot disable() {
        boolean wasEnabled = enabled;
        enabled = false;
        DamageShadowValidationSnapshot result = tracker.snapshot(false);
        if (wasEnabled) {
            logger.info(summaryLine(result));
        }
        return result;
    }

    public synchronized void reset() {
        tracker.reset(clock.instant());
    }

    public boolean enabled() {
        return enabled;
    }

    public synchronized DamageShadowValidationSnapshot snapshot() {
        return tracker.snapshot(enabled);
    }

    public synchronized void recordComparison(
            DamageShadowRuntimeContext context,
            DamageShadowComparison comparison
    ) {
        if (enabled) {
            tracker.recordComparison(context, comparison);
        }
    }

    public synchronized void recordLegacyFailure() {
        if (enabled) {
            tracker.recordLegacyFailure();
        }
    }

    public synchronized void recordShadowFailure() {
        if (enabled) {
            tracker.recordShadowFailure();
        }
    }

    public synchronized Path export() throws IOException {
        return exporter.export(
                exportDirectory, snapshot(), clock.instant());
    }

    public synchronized void close() {
        disable();
        tracker.reset(clock.instant());
    }

    public static String summaryLine(
            DamageShadowValidationSnapshot snapshot
    ) {
        return ("[DamageShadow] enabled=%s comparisons=%d matches=%d "
                + "mismatches=%d legacyFailures=%d shadowFailures=%d "
                + "maxAbs=%s maxRel=%s avgAbs=%s startedAt=%s")
                .formatted(
                        snapshot.enabled(),
                        snapshot.comparisonCount(),
                        snapshot.matchCount(),
                        snapshot.mismatchCount(),
                        snapshot.legacyFailureCount(),
                        snapshot.shadowFailureCount(),
                        snapshot.maximumAbsoluteError(),
                        snapshot.maximumRelativeError(),
                        snapshot.averageAbsoluteError(),
                        snapshot.sessionStartedAt());
    }
}
