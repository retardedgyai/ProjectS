package io.github.gyai.projects.dev;

import io.github.gyai.projects.combat.skill.HardControlType;

import java.util.List;

public final class HardControlTestSelection {
    private static final List<HardControlType> MODES = List.of(
            HardControlType.STUN,
            HardControlType.FEAR,
            HardControlType.CHARM,
            HardControlType.ROOT);
    private static final List<Integer> DURATIONS = List.of(20, 60, 100, 200);

    private HardControlTestSelection() {
    }

    public static boolean supports(HardControlType type) {
        return MODES.contains(type);
    }

    public static boolean supportsDuration(int ticks) {
        return DURATIONS.contains(ticks);
    }

    public static HardControlType nextMode(HardControlType current) {
        int index = MODES.indexOf(current);
        return MODES.get((index < 0 ? 0 : index + 1) % MODES.size());
    }

    public static int nextDurationTicks(int current) {
        int index = DURATIONS.indexOf(current);
        return DURATIONS.get((index < 0 ? 0 : index + 1) % DURATIONS.size());
    }
}
