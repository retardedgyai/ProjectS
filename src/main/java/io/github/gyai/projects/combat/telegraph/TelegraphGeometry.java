package io.github.gyai.projects.combat.telegraph;

public final class TelegraphGeometry {
    private TelegraphGeometry() {
    }

    public static boolean contains(
            TelegraphInstance.Shape shape,
            double centerX,
            double centerY,
            double centerZ,
            double directionX,
            double directionZ,
            double radius,
            double innerRadius,
            double width,
            double length,
            double verticalTolerance,
            double pointX,
            double pointY,
            double pointZ
    ) {
        if (shape == null
                || !allFinite(
                centerX, centerY, centerZ,
                directionX, directionZ,
                radius, innerRadius, width, length,
                verticalTolerance, pointX, pointY, pointZ)
                || verticalTolerance < 0.0
                || Math.abs(pointY - centerY) > verticalTolerance) {
            return false;
        }
        double dx = pointX - centerX;
        double dz = pointZ - centerZ;
        return switch (shape) {
            case CIRCLE -> radius > 0.0
                    && dx * dx + dz * dz <= radius * radius;
            case DONUT -> radius > innerRadius
                    && innerRadius >= 0.0
                    && dx * dx + dz * dz > innerRadius * innerRadius
                    && dx * dx + dz * dz <= radius * radius;
            case LINE -> containsLine(
                    dx, dz, directionX, directionZ, width, length);
        };
    }

    public static boolean containsCircle(
            double centerX,
            double centerY,
            double centerZ,
            double radius,
            double verticalTolerance,
            double pointX,
            double pointY,
            double pointZ
    ) {
        return contains(
                TelegraphInstance.Shape.CIRCLE,
                centerX, centerY, centerZ,
                0.0, 1.0,
                radius, 0.0, 0.0, 0.0,
                verticalTolerance,
                pointX, pointY, pointZ);
    }

    private static boolean containsLine(
            double dx,
            double dz,
            double directionX,
            double directionZ,
            double width,
            double length
    ) {
        double directionLength = Math.hypot(
                directionX, directionZ);
        if (directionLength < 0.000_001
                || width <= 0.0
                || length <= 0.0) {
            return false;
        }
        double normalizedX = directionX / directionLength;
        double normalizedZ = directionZ / directionLength;
        double forward = dx * normalizedX + dz * normalizedZ;
        if (forward < 0.0 || forward > length) {
            return false;
        }
        double side = dx * -normalizedZ + dz * normalizedX;
        return Math.abs(side) <= width * 0.5;
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
