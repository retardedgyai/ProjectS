package io.github.gyai.projects.enhancement.v2;

import io.github.gyai.projects.item.compatibility.LegacyItemCompatibilityReader;
import io.github.gyai.projects.item.compatibility.LegacyItemReadResult;
import io.github.gyai.projects.item.compatibility.LegacyPdcSource;

import java.util.List;

/** Read-only characterization of the currently deployed legacy enhancement format. */
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

    public int materialCost(int targetLevel) {
        requireLevel(targetLevel);
        return Math.max(1, (targetLevel + 4) / 5);
    }

    public int repairCost(int level) {
        requireLevel(level);
        return Math.max(1, (level + 4) / 5);
    }

    public double successChancePercent(int targetLevel) {
        requireLevel(targetLevel);
        if (targetLevel <= 5) return 100.0;
        if (targetLevel <= 10) return 95.0 - (targetLevel - 6) * 7.5;
        if (targetLevel <= 15) return 55.0 - (targetLevel - 11) * 5.0;
        if (targetLevel <= 20) return 30.0 - (targetLevel - 16) * 4.0;
        if (targetLevel <= 25) return 10.0 - (targetLevel - 21) * 1.5;
        return 3.0 - (targetLevel - 26) * 0.5;
    }

    public double breakChancePercent(int currentLevel) {
        requireLevel(currentLevel);
        if (currentLevel < 15) return 0.0;
        return Math.min(50.0, 5.0 + (currentLevel - 15) * 3.0);
    }

    public LegacyPresentation describe(int level, boolean broken, double attackPowerBonus,
                                       double attackSpeedBonus) {
        if (level < MIN_LEVEL || level > MAX_LEVEL
                || !Double.isFinite(attackPowerBonus) || !Double.isFinite(attackSpeedBonus)) {
            throw new IllegalArgumentException("invalid legacy enhancement state");
        }
        String prefix = (broken ? "§4[破損] " : "")
                + (level > 0 ? "§6[+" + level + "] " : "");
        List<String> loreFacts = List.of(
                "強化値: +" + level + " / +30",
                "攻撃倍率: " + (1.0 + level * 0.04),
                "強化攻撃速度: " + (level * 0.008),
                "武器攻撃力調整: " + attackPowerBonus,
                "武器攻撃速度調整: " + attackSpeedBonus);
        return new LegacyPresentation(
                prefix, loreFacts, !broken && level * 0.008 + attackSpeedBonus != 0.0,
                broken, FailureBehavior.NO_CHANGE_OR_BROKEN,
                FlagDisabledBehavior.LEGACY_MANAGER_AND_LISTENER);
    }

    public record LegacyPresentation(
            String displayPrefix,
            List<String> loreFacts,
            boolean attackSpeedAttributePresent,
            boolean zeroAttackPowerWhileBroken,
            FailureBehavior failureBehavior,
            FlagDisabledBehavior flagDisabledBehavior
    ) {
        public LegacyPresentation {
            loreFacts = List.copyOf(loreFacts);
        }
    }

    public enum FailureBehavior { NO_CHANGE_OR_BROKEN }
    public enum FlagDisabledBehavior { LEGACY_MANAGER_AND_LISTENER }

    private void requireLevel(int level) {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException("legacy level must be 0..30");
        }
    }
}
