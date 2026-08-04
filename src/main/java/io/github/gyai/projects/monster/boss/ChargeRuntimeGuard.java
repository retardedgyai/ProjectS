package io.github.gyai.projects.monster.boss;

public final class ChargeRuntimeGuard {
    public static final double MINIMUM_HORIZONTAL_MOVEMENT =
            0.05;
    public static final int MAXIMUM_STUCK_TICKS = 3;

    private int elapsedTicks;
    private int stuckTicks;
    private boolean finished;
    private StopReason finishReason = StopReason.NONE;

    public StopReason observe(
            double horizontalMovement,
            int maximumTicks
    ) {
        if (finished) {
            return StopReason.ALREADY_FINISHED;
        }
        elapsedTicks++;
        if (!Double.isFinite(horizontalMovement)
                || horizontalMovement
                < MINIMUM_HORIZONTAL_MOVEMENT) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        if (stuckTicks >= MAXIMUM_STUCK_TICKS) {
            return StopReason.STUCK;
        }
        if (elapsedTicks >= maximumTicks) {
            return StopReason.TIMEOUT;
        }
        return StopReason.NONE;
    }

    public boolean finishOnce(StopReason reason) {
        if (reason == StopReason.NONE
                || reason == StopReason.ALREADY_FINISHED) {
            throw new IllegalArgumentException(
                    "A terminal charge reason is required");
        }
        if (finished) {
            return false;
        }
        finished = true;
        finishReason = reason;
        return true;
    }

    public boolean particlesAllowed() {
        return !finished;
    }

    public StopReason finishReason() {
        return finishReason;
    }

    public enum StopReason {
        NONE,
        STUCK,
        TIMEOUT,
        COLLISION,
        EXTERNAL,
        ALREADY_FINISHED
    }
}
