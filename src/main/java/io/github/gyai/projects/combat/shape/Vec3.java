package io.github.gyai.projects.combat.shape;

import java.util.Objects;

/** Immutable finite three-dimensional value used for positions and unbounded directions. */
public record Vec3(double x, double y, double z) {
    public Vec3 {
        GeometryTolerance.requireFinite(x, "x");
        GeometryTolerance.requireFinite(y, "y");
        GeometryTolerance.requireFinite(z, "z");
    }

    public Vec3 add(Vec3 other) {
        Objects.requireNonNull(other, "other");
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 subtract(Vec3 other) {
        Objects.requireNonNull(other, "other");
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 scale(double factor) {
        GeometryTolerance.requireFinite(factor, "factor");
        return new Vec3(x * factor, y * factor, z * factor);
    }

    public double dot(Vec3 other) {
        Objects.requireNonNull(other, "other");
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        Objects.requireNonNull(other, "other");
        return new Vec3(y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    public double lengthSquared() {
        return dot(this);
    }

    public double length() {
        return Math.hypot(Math.hypot(x, y), z);
    }

    public Vec3 normalized() {
        double maximum = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
        if (maximum == 0.0) {
            throw new IllegalArgumentException("direction must be finite and non-zero");
        }
        double scaledX = x / maximum;
        double scaledY = y / maximum;
        double scaledZ = z / maximum;
        double scaledLength = Math.sqrt(scaledX * scaledX
                + scaledY * scaledY + scaledZ * scaledZ);
        Vec3 result = new Vec3(scaledX / scaledLength,
                scaledY / scaledLength, scaledZ / scaledLength);
        if (!Double.isFinite(result.x()) || !Double.isFinite(result.y())
                || !Double.isFinite(result.z())) {
            throw new IllegalArgumentException("direction cannot be normalized safely");
        }
        return result;
    }

    public Vec3 negate() {
        return new Vec3(-x, -y, -z);
    }
}
