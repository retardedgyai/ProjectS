package io.github.gyai.projects.combat.shape;

import java.util.Objects;

/** Upright finite annulus with a horizontal circular hole; tangencies intersect. */
public record HorizontalRingShape(Vec3 center, double innerRadius, double outerRadius,
                                  double verticalHalfHeight) implements CombatShape {
    public HorizontalRingShape {
        center = GeometryDomain.requirePosition(Objects.requireNonNull(center, "center"), "center");
        GeometryDomain.requireExtent(innerRadius, "innerRadius");
        GeometryDomain.requireExtent(outerRadius, "outerRadius");
        GeometryDomain.requireExtent(verticalHalfHeight, "verticalHalfHeight");
        if (innerRadius < 0 || innerRadius >= outerRadius || verticalHalfHeight <= 0) {
            throw new IllegalArgumentException("invalid ring dimensions");
        }
        GeometryDomain.requireEnvelope(center, outerRadius + GeometryTolerance.LENGTH,
                verticalHalfHeight + GeometryTolerance.LENGTH,
                outerRadius + GeometryTolerance.LENGTH, "horizontal ring");
    }

    @Override
    public Aabb broadPhaseBounds() {
        return new Aabb(center.x() - outerRadius, center.y() - verticalHalfHeight,
                center.z() - outerRadius, center.x() + outerRadius,
                center.y() + verticalHalfHeight, center.z() + outerRadius)
                .expand(GeometryTolerance.LENGTH);
    }

    @Override
    public boolean intersects(Aabb target) {
        Objects.requireNonNull(target, "target");
        if (target.maxY() < center.y() - verticalHalfHeight - GeometryTolerance.LENGTH
                || target.minY() > center.y() + verticalHalfHeight + GeometryTolerance.LENGTH) return false;
        double nearX = center.x() - Aabb.clamp(center.x(), target.minX(), target.maxX());
        double nearZ = center.z() - Aabb.clamp(center.z(), target.minZ(), target.maxZ());
        double farX = Math.max(Math.abs(target.minX() - center.x()), Math.abs(target.maxX() - center.x()));
        double farZ = Math.max(Math.abs(target.minZ() - center.z()), Math.abs(target.maxZ() - center.z()));
        return nearX * nearX + nearZ * nearZ <= SphereShape.sq(outerRadius + GeometryTolerance.LENGTH)
                && farX * farX + farZ * farZ
                >= SphereShape.sq(Math.max(0, innerRadius - GeometryTolerance.LENGTH));
    }
}
