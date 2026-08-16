package io.github.gyai.projects.authoring

import io.github.gyai.projects.combat.shape.Aabb
import io.github.gyai.projects.combat.shape.AxisAlignedBoxShape
import io.github.gyai.projects.combat.shape.ConeShape
import io.github.gyai.projects.combat.shape.HorizontalRingShape
import io.github.gyai.projects.combat.shape.LineShape
import io.github.gyai.projects.combat.shape.SphereShape
import io.github.gyai.projects.combat.shape.UprightCylinderShape
import io.github.gyai.projects.combat.shape.Vec3

/** Typed factories returning the existing Java shape implementations directly. */
object CombatShapeAuthoring {
    @JvmStatic fun sphere(center: Vec3, radius: Double): SphereShape = SphereShape(center, radius)
    @JvmStatic fun uprightCylinder(center: Vec3, radius: Double, verticalHalfHeight: Double): UprightCylinderShape = UprightCylinderShape(center, radius, verticalHalfHeight)
    @JvmStatic fun horizontalRing(center: Vec3, innerRadius: Double, outerRadius: Double, verticalHalfHeight: Double): HorizontalRingShape = HorizontalRingShape(center, innerRadius, outerRadius, verticalHalfHeight)
    @JvmStatic fun axisAlignedBox(bounds: Aabb): AxisAlignedBoxShape = AxisAlignedBoxShape(bounds)
    @JvmStatic fun axisAlignedBox(min: Vec3, max: Vec3): AxisAlignedBoxShape = AxisAlignedBoxShape(Aabb(min, max))
    @JvmStatic fun line(start: Vec3, end: Vec3, radius: Double): LineShape = LineShape(start, end, radius)
    @JvmStatic fun cone(origin: Vec3, forward: Vec3, length: Double, halfAngleRadians: Double): ConeShape = ConeShape(origin, forward, length, halfAngleRadians)
}
