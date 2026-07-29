package io.github.gyai.projects.combat.skill;

import java.util.Objects;
import java.util.UUID;

public record HardControlState(
        HardControlType type,
        UUID sourceId,
        long startTick,
        long endTick,
        int originalDurationTicks
) {
    public HardControlState {
        Objects.requireNonNull(type, "type");
        if (endTick <= startTick || originalDurationTicks <= 0) {
            throw new IllegalArgumentException("Hard control duration must be positive");
        }
    }

    public boolean activeAt(long tick) {
        return tick < endTick;
    }

    public int remainingTicks(long tick) {
        return (int) Math.clamp(endTick - tick, 0L, Integer.MAX_VALUE);
    }

    public static Transition apply(
            HardControlState current,
            HardControlType incomingType,
            UUID sourceId,
            long currentTick,
            int durationTicks
    ) {
        Objects.requireNonNull(incomingType, "incomingType");
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("durationTicks must be positive");
        }
        long newEndTick = Math.addExact(currentTick, durationTicks);
        if (current == null || !current.activeAt(currentTick)) {
            return new Transition(new HardControlState(
                    incomingType, sourceId, currentTick, newEndTick, durationTicks),
                    HardControlApplicationResult.APPLIED);
        }
        if (current.type() == incomingType) {
            long refreshedEnd = Math.max(current.endTick(), newEndTick);
            int originalDuration = Math.max(
                    current.originalDurationTicks(), durationTicks);
            return new Transition(new HardControlState(
                    incomingType,
                    sourceId == null ? current.sourceId() : sourceId,
                    current.startTick(),
                    refreshedEnd,
                    originalDuration),
                    HardControlApplicationResult.REFRESHED);
        }
        if (incomingType.priority() < current.type().priority()) {
            return new Transition(
                    current,
                    HardControlApplicationResult.REJECTED_LOWER_PRIORITY);
        }
        return new Transition(new HardControlState(
                incomingType, sourceId, currentTick, newEndTick, durationTicks),
                HardControlApplicationResult.REPLACED);
    }

    public record Transition(
            HardControlState state,
            HardControlApplicationResult result
    ) {
    }
}
