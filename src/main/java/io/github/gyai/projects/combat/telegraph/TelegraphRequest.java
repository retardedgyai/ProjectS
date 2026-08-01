package io.github.gyai.projects.combat.telegraph;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public record TelegraphRequest(
        String attackId,
        UUID worldId,
        String dimension,
        TelegraphInstance.Shape shape,
        TelegraphInstance.VisualTheme theme,
        TelegraphInstance.VisualStyle style,
        double centerX,
        double centerY,
        double centerZ,
        double directionX,
        double directionZ,
        double radius,
        double innerRadius,
        double width,
        double length,
        long startTick,
        long lockTick,
        long detonateTick,
        long expireTick,
        TelegraphInstance.TrackingMode trackingMode,
        UUID targetId,
        double verticalTolerance
) {
    public static final int MAX_ATTACK_ID_BYTES = 64;
    public static final int MAX_DIMENSION_BYTES = 128;
    public static final int MAX_DURATION_TICKS = 1_200;
    public static final double MAX_SIZE = 128.0;

    public TelegraphRequest {
        Objects.requireNonNull(attackId, "attackId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(trackingMode, "trackingMode");
        if (attackId.isBlank()
                || attackId.getBytes(StandardCharsets.UTF_8).length
                > MAX_ATTACK_ID_BYTES
                || dimension.isBlank()
                || dimension.getBytes(StandardCharsets.UTF_8).length
                > MAX_DIMENSION_BYTES
                || !allFinite(
                centerX, centerY, centerZ,
                directionX, directionZ,
                radius, innerRadius, width, length,
                verticalTolerance)
                || verticalTolerance < 0.0
                || verticalTolerance > 16.0
                || startTick >= lockTick
                || lockTick > detonateTick
                || detonateTick > expireTick
                || expireTick - startTick > MAX_DURATION_TICKS
                || detonateTick - startTick <= 0L) {
            throw new IllegalArgumentException(
                    "Invalid telegraph request");
        }
        if (trackingMode == TelegraphInstance.TrackingMode.TARGET
                && targetId == null) {
            throw new IllegalArgumentException(
                    "Tracking telegraphs require a target");
        }
        switch (shape) {
            case CIRCLE -> {
                validateSize(radius);
                if (innerRadius != 0.0) {
                    throw new IllegalArgumentException(
                            "Circle cannot have an inner radius");
                }
            }
            case DONUT -> {
                validateSize(radius);
                if (innerRadius < 0.0
                        || innerRadius >= radius) {
                    throw new IllegalArgumentException(
                            "Invalid donut radii");
                }
            }
            case LINE -> {
                validateSize(width);
                validateSize(length);
                double directionLength = Math.hypot(
                        directionX, directionZ);
                if (directionLength < 0.000_001) {
                    throw new IllegalArgumentException(
                            "Line requires a direction");
                }
                directionX /= directionLength;
                directionZ /= directionLength;
            }
        }
    }

    public int totalWarningTicks() {
        return Math.toIntExact(detonateTick - startTick);
    }

    private static void validateSize(double value) {
        if (value <= 0.0 || value > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Invalid telegraph size");
        }
    }

    private static boolean allFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
