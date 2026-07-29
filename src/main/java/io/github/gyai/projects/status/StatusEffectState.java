package io.github.gyai.projects.status;

import java.util.Objects;
import java.util.UUID;

public record StatusEffectState(
        StatusEffectType type,
        UUID sourceId,
        long startTick,
        long endTick,
        int originalDurationTicks,
        double strength
) {
    public StatusEffectState {
        Objects.requireNonNull(type, "type");
        if (endTick <= startTick
                || originalDurationTicks <= 0
                || !Double.isFinite(strength)
                || strength < 0.0) {
            throw new IllegalArgumentException("Invalid status effect state");
        }
    }

    public boolean activeAt(long tick) {
        return tick < endTick;
    }

    public int remainingTicks(long tick) {
        return (int) Math.clamp(endTick - tick, 0L, Integer.MAX_VALUE);
    }

    public static Transition apply(
            StatusEffectState current,
            StatusEffectType type,
            UUID sourceId,
            long currentTick,
            int durationTicks,
            double strength
    ) {
        Objects.requireNonNull(type, "type");
        if (durationTicks <= 0
                || !Double.isFinite(strength)
                || strength < 0.0) {
            throw new IllegalArgumentException("Invalid status effect application");
        }
        long newEndTick = Math.addExact(currentTick, durationTicks);
        if (current == null || !current.activeAt(currentTick)) {
            return new Transition(new StatusEffectState(
                    type, sourceId, currentTick, newEndTick,
                    durationTicks, strength),
                    StatusApplicationResult.APPLIED);
        }
        return new Transition(new StatusEffectState(
                type,
                sourceId == null ? current.sourceId() : sourceId,
                current.startTick(),
                Math.max(current.endTick(), newEndTick),
                Math.max(current.originalDurationTicks(), durationTicks),
                Math.max(current.strength(), strength)),
                StatusApplicationResult.REFRESHED);
    }

    public record Transition(
            StatusEffectState state,
            StatusApplicationResult result
    ) {
    }
}
