package io.github.gyai.projects.combat.shape;

import java.util.Objects;

/** Solid axis-aligned box; face contact intersects. */
public record AxisAlignedBoxShape(Aabb bounds) implements CombatShape {
    public AxisAlignedBoxShape {
        bounds = Objects.requireNonNull(bounds, "bounds");
        Vec3 halfExtents = bounds.halfExtents();
        GeometryDomain.requireExtent(halfExtents.x(), "box x half extent");
        GeometryDomain.requireExtent(halfExtents.y(), "box y half extent");
        GeometryDomain.requireExtent(halfExtents.z(), "box z half extent");
        GeometryDomain.requireRange(bounds.minX() - GeometryTolerance.LENGTH,
                bounds.maxX() + GeometryTolerance.LENGTH, "box.x");
        GeometryDomain.requireRange(bounds.minY() - GeometryTolerance.LENGTH,
                bounds.maxY() + GeometryTolerance.LENGTH, "box.y");
        GeometryDomain.requireRange(bounds.minZ() - GeometryTolerance.LENGTH,
                bounds.maxZ() + GeometryTolerance.LENGTH, "box.z");
    }

    @Override
    public Aabb broadPhaseBounds() {
        return bounds.expand(GeometryTolerance.LENGTH);
    }

    @Override
    public boolean intersects(Aabb target) {
        return bounds.expand(GeometryTolerance.LENGTH)
                .overlaps(Objects.requireNonNull(target, "target"));
    }
}
