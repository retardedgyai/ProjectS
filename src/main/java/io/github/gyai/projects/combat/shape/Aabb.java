package io.github.gyai.projects.combat.shape;

import java.util.Objects;

/** Immutable axis-aligned bounding box with inclusive faces. */
public final class Aabb {
    private final Vec3 min;
    private final Vec3 max;

    public Aabb(Vec3 min, Vec3 max) {
        this.min = GeometryDomain.requirePosition(Objects.requireNonNull(min, "min"), "min");
        this.max = GeometryDomain.requirePosition(Objects.requireNonNull(max, "max"), "max");
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new IllegalArgumentException("min must not exceed max");
        }
    }

    public Aabb(double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ) {
        this(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));
    }

    public Vec3 min() { return min; }
    public Vec3 max() { return max; }
    public double minX() { return min.x(); }
    public double minY() { return min.y(); }
    public double minZ() { return min.z(); }
    public double maxX() { return max.x(); }
    public double maxY() { return max.y(); }
    public double maxZ() { return max.z(); }

    public boolean overlaps(Aabb other) {
        Objects.requireNonNull(other, "other");
        return minX() <= other.maxX() && maxX() >= other.minX()
                && minY() <= other.maxY() && maxY() >= other.minY()
                && minZ() <= other.maxZ() && maxZ() >= other.minZ();
    }

    public boolean contains(Vec3 point) {
        Objects.requireNonNull(point, "point");
        return point.x() >= minX() && point.x() <= maxX()
                && point.y() >= minY() && point.y() <= maxY()
                && point.z() >= minZ() && point.z() <= maxZ();
    }

    public Vec3 closestPoint(Vec3 point) {
        Objects.requireNonNull(point, "point");
        return new Vec3(clamp(point.x(), minX(), maxX()),
                clamp(point.y(), minY(), maxY()),
                clamp(point.z(), minZ(), maxZ()));
    }

    public Aabb expand(double amount) {
        GeometryTolerance.requireFinite(amount, "amount");
        if (amount < 0) throw new IllegalArgumentException("amount must be non-negative");
        return new Aabb(minX() - amount, minY() - amount, minZ() - amount,
                maxX() + amount, maxY() + amount, maxZ() + amount);
    }

    public Aabb translate(Vec3 offset) {
        Objects.requireNonNull(offset, "offset");
        return new Aabb(min.add(offset), max.add(offset));
    }

    public Vec3 center() {
        return new Vec3(minX() / 2 + maxX() / 2,
                minY() / 2 + maxY() / 2, minZ() / 2 + maxZ() / 2);
    }

    public Vec3 halfExtents() {
        return new Vec3(maxX() / 2 - minX() / 2,
                maxY() / 2 - minY() / 2, maxZ() / 2 - minZ() / 2);
    }

    static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Aabb box && min.equals(box.min) && max.equals(box.max);
    }

    @Override
    public int hashCode() { return Objects.hash(min, max); }

    @Override
    public String toString() { return "Aabb[" + min + ".." + max + "]"; }
}
