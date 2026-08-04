package io.github.gyai.projects.enhancement.v2;

import io.github.gyai.projects.item.compatibility.LegacyItemCompatibilityReader;
import io.github.gyai.projects.item.compatibility.LegacyItemReadResult;
import io.github.gyai.projects.item.compatibility.LegacyPdcSource;

/** Read-only adapter for the currently deployed legacy enhancement PDC format. */
public final class LegacyEnhancementCharacterization {
    public static final int MIN_LEVEL = 0;
    public static final int MAX_LEVEL = 30;
    public static final String LEVEL_KEY = LegacyItemCompatibilityReader.ENHANCEMENT_LEVEL_KEY;
    public static final String BROKEN_KEY = LegacyItemCompatibilityReader.BROKEN_KEY;
    public static final String ATTACK_POWER_BONUS_KEY =
            LegacyItemCompatibilityReader.ATTACK_POWER_BONUS_KEY;
    public static final String ATTACK_SPEED_BONUS_KEY =
            LegacyItemCompatibilityReader.ATTACK_SPEED_BONUS_KEY;

    private final LegacyItemCompatibilityReader reader = new LegacyItemCompatibilityReader();

    public LegacyItemReadResult read(LegacyPdcSource source) {
        return reader.read(source);
    }
}
