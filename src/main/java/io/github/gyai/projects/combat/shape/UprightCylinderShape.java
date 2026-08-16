package io.github.gyai.projects.combat.shape;

import java.util.Objects;

/** Solid cylinder aligned with the world Y axis; tangencies intersect. */
public record UprightCylinderShape(Vec3 center, double radius, double verticalHalfHeight)
        implements CombatShape {
    public UprightCylinderShape {
        center = GeometryDomain.requirePosition(Objects.requireNonNull(center, "center"), "center");
        GeometryDomain.requireExtent(radius, "radius");
        GeometryDomain.requireExtent(verticalHalfHeight, "verticalHalfHeight");
        if (radius <= 0 || verticalHalfHeight <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        GeometryDomain.requireEnvelope(center, radius + GeometryTolerance.LENGTH,
                verticalHalfHeight + GeometryTolerance.LENGTH,
                radius + GeometryTolerance.LENGTH, "upright cylinder");
    }

    @Override
    public Aabb broadPhaseBounds() {
        return new Aabb(center.x() - radius, center.y() - verticalHalfHeight, center.z() - radius,
                center.x() + radius, center.y() + verticalHalfHeight, center.z() + radius)
                .expand(GeometryTolerance.LENGTH);
    }

    @Override
    public boolean intersects(Aabb target) {
        Objects.requireNonNull(target, "target");
        if (target.maxY() < center.y() - verticalHalfHeight - GeometryTolerance.LENGTH
                || target.minY() > center.y() + verticalHalfHeight + GeometryTolerance.LENGTH) return false;
        double dx = center.x() - Aabb.clamp(center.x(), target.minX(), target.maxX());
        double dz = center.z() - Aabb.clamp(center.z(), target.minZ(), target.maxZ());
        return dx * dx + dz * dz <= SphereShape.sq(radius + GeometryTolerance.LENGTH);
    }
}
