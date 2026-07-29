package io.github.gyai.projects.network;

import java.util.Locale;

public final class MonsterUiMath {
    private MonsterUiMath() {
    }

    public static ThreatBand threatBand(
            int monsterLevel,
            int playerLevel
    ) {
        int difference = monsterLevel - playerLevel;
        if (difference <= -5) {
            return ThreatBand.GRAY;
        }
        if (difference <= 2) {
            return ThreatBand.WHITE;
        }
        if (difference <= 5) {
            return ThreatBand.YELLOW;
        }
        return ThreatBand.RED;
    }

    public static double clampHealth(double current, double maximum) {
        if (!Double.isFinite(maximum) || maximum <= 0.0) {
            return 0.0;
        }
        if (!Double.isFinite(current)) {
            return 0.0;
        }
        return Math.clamp(current, 0.0, maximum);
    }

    public static String formatHealth(double current, double maximum) {
        double safeMaximum = Double.isFinite(maximum)
                ? Math.max(1.0, maximum)
                : 1.0;
        double safeCurrent = clampHealth(current, safeMaximum);
        long currentValue = safeCurrent > 0.0
                ? Math.max(1L, Math.round(safeCurrent))
                : 0L;
        long maximumValue = Math.max(1L, Math.round(safeMaximum));
        return String.format(
                Locale.ROOT, "%,d / %,d",
                currentValue, maximumValue);
    }

    public static double remainingRatio(
            int remainingTicks,
            int totalTicks
    ) {
        if (totalTicks <= 0) {
            return 0.0;
        }
        return Math.clamp(
                remainingTicks / (double) totalTicks,
                0.0,
                1.0);
    }

    public enum ThreatBand {
        GRAY,
        WHITE,
        YELLOW,
        RED
    }
}
