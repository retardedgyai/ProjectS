package io.github.gyai.projects.combat.shape;

import java.util.Objects;

/** Numeric safety domain for ProjectS world-space combat geometry. */
public final class GeometryDomain {
    /** Exact power-of-two ceiling with over 3.5 million blocks beyond Minecraft's world limit. */
    public static final double MAX_ABSOLUTE_COORDINATE = 0x1.0p25;
    /** Maximum separation across the supported coordinate cube; not a gameplay balance cap. */
    public static final double MAX_EXTENT = 0x1.0p26;

    private GeometryDomain() {
    }

    static Vec3 requirePosition(Vec3 position, String name) {
        Objects.requireNonNull(position, name);
        requireCoordinate(position.x(), name + ".x");
        requireCoordinate(position.y(), name + ".y");
        requireCoordinate(position.z(), name + ".z");
        return position;
    }

    static double requireCoordinate(double value, String name) {
        GeometryTolerance.requireFinite(value, name);
        if (Math.abs(value) > MAX_ABSOLUTE_COORDINATE) {
            throw new IllegalArgumentException(name + " exceeds supported geometry coordinates");
        }
        return value;
    }

    static double requireExtent(double value, String name) {
        GeometryTolerance.requireFinite(value, name);
        if (value < 0.0 || value > MAX_EXTENT) {
            throw new IllegalArgumentException(name + " exceeds supported geometry extents");
        }
        return value;
    }

    static void requireEnvelope(Vec3 center, double xExtent, double yExtent, double zExtent,
                                String name) {
        requirePosition(center, name);
        GeometryTolerance.requireFinite(xExtent, name + " x extent");
        GeometryTolerance.requireFinite(yExtent, name + " y extent");
        GeometryTolerance.requireFinite(zExtent, name + " z extent");
        requireRange(center.x() - xExtent, center.x() + xExtent, name + ".x");
        requireRange(center.y() - yExtent, center.y() + yExtent, name + ".y");
        requireRange(center.z() - zExtent, center.z() + zExtent, name + ".z");
    }

    static void requireRange(double minimum, double maximum, String name) {
        requireCoordinate(minimum, name + " minimum");
        requireCoordinate(maximum, name + " maximum");
    }
}
