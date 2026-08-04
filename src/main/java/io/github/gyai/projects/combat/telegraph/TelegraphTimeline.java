package io.github.gyai.projects.combat.telegraph;

public final class TelegraphTimeline {
    private TelegraphTimeline() {
    }

    public static long trackingLockTick(
            long startTick,
            long detonateTick,
            double remainingThreshold
    ) {
        if (startTick >= detonateTick
                || !Double.isFinite(remainingThreshold)
                || remainingThreshold <= 0.0
                || remainingThreshold >= 1.0) {
            throw new IllegalArgumentException("Invalid telegraph timeline");
        }
        long duration = detonateTick - startTick;
        long elapsed = Math.max(
                1L,
                Math.round(duration * (1.0 - remainingThreshold)));
        return Math.min(detonateTick, startTick + elapsed);
    }

    public static boolean isImminent(
            int totalWarningTicks,
            double remainingWarningTicks,
            double threshold
    ) {
        return totalWarningTicks > 0
                && Double.isFinite(remainingWarningTicks)
                && Double.isFinite(threshold)
                && threshold > 0.0
                && threshold < 1.0
                && remainingWarningTicks
                <= totalWarningTicks * threshold;
    }
}
