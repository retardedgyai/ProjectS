package io.github.gyai.projects.combat.shape;

import java.util.Objects;

/** Finite solid circular cone from its apex along {@code forward}; base and apex are included. */
public final class ConeShape implements CombatShape {
    private final Vec3 origin;
    private final Vec3 forward;
    private final double length;
    private final double halfAngleRadians;
    private final double baseRadius;

    public ConeShape(Vec3 origin, Vec3 forward, double length, double halfAngleRadians) {
        this.origin = GeometryDomain.requirePosition(Objects.requireNonNull(origin, "origin"), "origin");
        this.forward = Objects.requireNonNull(forward, "forward").normalized();
        GeometryDomain.requireExtent(length, "length");
        GeometryTolerance.requireFinite(halfAngleRadians, "halfAngleRadians");
        if (length <= 0 || halfAngleRadians <= 0 || halfAngleRadians >= Math.PI / 2) {
            throw new IllegalArgumentException("invalid cone dimensions");
        }
        this.length = length;
        this.halfAngleRadians = halfAngleRadians;
        this.baseRadius = length * Math.tan(halfAngleRadians);
        GeometryDomain.requireExtent(baseRadius, "cone base radius");
        if (baseRadius <= 0.0) throw new IllegalArgumentException("invalid cone radius");
        // Validate the full tolerance-expanded footprint at construction, not during a later query.
        broadPhaseBounds();
    }

    public Vec3 origin() { return origin; }
    public Vec3 forward() { return forward; }
    public double length() { return length; }
    public double halfAngleRadians() { return halfAngleRadians; }

    @Override
    public Aabb broadPhaseBounds() {
        Vec3 positiveX = support(new Vec3(1, 0, 0));
        Vec3 negativeX = support(new Vec3(-1, 0, 0));
        Vec3 positiveY = support(new Vec3(0, 1, 0));
        Vec3 negativeY = support(new Vec3(0, -1, 0));
        Vec3 positiveZ = support(new Vec3(0, 0, 1));
        Vec3 negativeZ = support(new Vec3(0, 0, -1));
        return new Aabb(negativeX.x(), negativeY.y(), negativeZ.z(),
                positiveX.x(), positiveY.y(), positiveZ.z()).expand(GeometryTolerance.LENGTH);
    }

    @Override
    public boolean intersects(Aabb targetBounds) {
        return ConeAabbGjk.intersects(this, Objects.requireNonNull(targetBounds, "targetBounds"));
    }

    Vec3 support(Vec3 direction) {
        Vec3 unitDirection = Objects.requireNonNull(direction, "direction").normalized();
        double forwardLengthSquared = forward.dot(forward);
        if (!Double.isFinite(forwardLengthSquared) || forwardLengthSquared <= 0.0) {
            throw new ArithmeticException("invalid cone forward direction");
        }

        Vec3 base = origin.add(forward.scale(length));
        double projection = unitDirection.dot(forward) / forwardLengthSquared;
        Vec3 radial = new Vec3(
                Math.fma(-forward.x(), projection, unitDirection.x()),
                Math.fma(-forward.y(), projection, unitDirection.y()),
                Math.fma(-forward.z(), projection, unitDirection.z()));
        double radialLength = radial.length();
        Vec3 disk = base;
        Vec3 radialDirection = null;
        if (baseRadius * (radialLength + GeometryTolerance.UNIT_PROJECTION_ERROR)
                > GeometryTolerance.CONE_SUPPORT_ERROR_BUDGET) {
            radialDirection = radial.normalized();
            disk = base.add(radialDirection.scale(baseRadius));
        }
        double baseScore = length * unitDirection.dot(forward);
        if (radialDirection != null) {
            baseScore += baseRadius * unitDirection.dot(radialDirection);
        }
        return baseScore > 0.0 ? disk : origin;
    }
}
