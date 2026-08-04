package io.github.gyai.projects.monster.editor.v2;

import java.time.Duration;

public record MobEditorV2Policy(
        int maximumGlobalSessions,
        int maximumSessionsPerPlayer,
        int maximumGlobalTestSpawns,
        int maximumTestSpawnsPerPlayer,
        int listPageSize,
        Duration sessionExpiry
) {
    public static final MobEditorV2Policy SAFE_DEFAULTS =
            new MobEditorV2Policy(512, 4, 128, 8, 50, Duration.ofMinutes(15));

    public MobEditorV2Policy {
        if (maximumGlobalSessions < 1 || maximumSessionsPerPlayer < 1
                || maximumGlobalTestSpawns < 1 || maximumTestSpawnsPerPlayer < 1
                || listPageSize < 1 || listPageSize > 50
                || sessionExpiry == null || sessionExpiry.isNegative()
                || sessionExpiry.isZero()) throw new IllegalArgumentException("editor policy");
    }
}
