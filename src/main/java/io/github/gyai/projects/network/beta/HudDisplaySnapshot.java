package io.github.gyai.projects.network.beta;

import java.util.Map;

public record HudDisplaySnapshot(
        int level,
        long experience,
        String classId,
        Map<String, Double> resourceSummaries,
        EndgameUnlockState endgameUnlockState
) {
    public enum EndgameUnlockState { UNKNOWN, LOCKED, UNLOCKED }

    public HudDisplaySnapshot {
        if (level < 0 || experience < 0 || endgameUnlockState == null) {
            throw new IllegalArgumentException("Invalid HUD values");
        }
        classId = BetaDisplayValidation.id(classId, "classId");
        resourceSummaries = BetaDisplayValidation.map(resourceSummaries, 64, "resources");
        resourceSummaries.forEach((id, value) -> {
            BetaDisplayValidation.id(id, "resourceId");
            BetaDisplayValidation.finite(value, "resourceValue");
        });
    }
}
