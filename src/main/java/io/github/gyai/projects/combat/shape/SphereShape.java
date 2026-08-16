package io.github.gyai.projects.combat.shape;

import java.util.Objects;

/** Solid sphere; tangencies intersect. */
public record SphereShape(Vec3 center, double radius) implements CombatShape {
    public SphereShape {
        center = GeometryDomain.requirePosition(Objects.requireNonNull(center, "center"), "center");
        GeometryDomain.requireExtent(radius, "radius");
        if (radius <= 0) throw new IllegalArgumentException("radius must be positive");
        GeometryDomain.requireEnvelope(center, radius + GeometryTolerance.LENGTH,
                radius + GeometryTolerance.LENGTH, radius + GeometryTolerance.LENGTH, "sphere");
    }

    @Override
    public Aabb broadPhaseBounds() {
        return new Aabb(center.x() - radius, center.y() - radius, center.z() - radius,
                center.x() + radius, center.y() + radius, center.z() + radius)
                .expand(GeometryTolerance.LENGTH);
    }

    @Override
    public boolean intersects(Aabb target) {
        Objects.requireNonNull(target, "target");
        return center.subtract(target.closestPoint(center)).lengthSquared()
                <= sq(radius + GeometryTolerance.LENGTH);
    }

    static double sq(double value) { return value * value; }
}
