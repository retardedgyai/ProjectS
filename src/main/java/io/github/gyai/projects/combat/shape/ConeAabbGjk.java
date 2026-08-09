package io.github.gyai.projects.combat.shape;

import java.util.ArrayList;
import java.util.List;

/** Package-private, cone-specific conservative GJK implementation. */
final class ConeAabbGjk {
    private static final int MAX_ITERATIONS = 48;

    private ConeAabbGjk() {
    }

    static boolean intersects(ConeShape cone, Aabb box) {
        try {
            Vec3 direction = box.center().subtract(cone.origin());
            if (direction.lengthSquared() <= GeometryTolerance.LENGTH * GeometryTolerance.LENGTH) {
                direction = new Vec3(1, 0, 0);
            }
            List<Vec3> simplex = new ArrayList<>(4);
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                Vec3 support = support(cone, box, direction);
                if (!finite(support) || !finite(direction)) return true;

                double directionLength = direction.length();
                double projection = support.dot(direction);
                if (!Double.isFinite(directionLength) || !Double.isFinite(projection)) return true;
                if (projection < -GeometryTolerance.LENGTH * directionLength) return false;

                for (Vec3 old : simplex) {
                    if (support.subtract(old).lengthSquared()
                            <= GeometryTolerance.LENGTH * GeometryTolerance.LENGTH) return true;
                }
                simplex.add(0, support);
                Result result = next(simplex);
                if (result.contains) return true;
                if (!finite(result.direction)
                        || result.direction.lengthSquared() <= GeometryTolerance.LENGTH * GeometryTolerance.LENGTH) return true;
                direction = result.direction;
            }
        } catch (RuntimeException ignored) {
            return true;
        }
        return true;
    }

    private static Vec3 support(ConeShape cone, Aabb box, Vec3 direction) {
        return cone.support(direction).subtract(boxSupport(box, direction.negate()));
    }

    private static Vec3 boxSupport(Aabb box, Vec3 direction) {
        return new Vec3(direction.x() >= 0 ? box.maxX() : box.minX(),
                direction.y() >= 0 ? box.maxY() : box.minY(),
                direction.z() >= 0 ? box.maxZ() : box.minZ());
    }

    private static Result next(List<Vec3> simplex) {
        Vec3 a = simplex.getFirst();
        Vec3 ao = a.negate();
        if (simplex.size() == 1) return new Result(false, ao);
        if (simplex.size() == 2) return lineOrPoint(simplex, a, simplex.get(1), ao);
        if (simplex.size() == 3) return triangle(simplex, a, ao);
        return tetrahedron(simplex, a, ao);
    }

    private static Result triangle(List<Vec3> simplex, Vec3 a, Vec3 ao) {
        Vec3 b = simplex.get(1);
        Vec3 c = simplex.get(2);
        Vec3 ab = b.subtract(a);
        Vec3 ac = c.subtract(a);
        Vec3 abc = ab.cross(ac);
        if (abc.cross(ac).dot(ao) > 0) {
            if (ac.dot(ao) > 0) {
                simplex.remove(1);
                return new Result(false, triple(ac, ao, ac));
            }
            return lineOrPoint(simplex, a, b, ao);
        }
        if (ab.cross(abc).dot(ao) > 0) return lineOrPoint(simplex, a, b, ao);
        if (abc.dot(ao) > 0) return new Result(false, abc);

        // Reverse B/C with the normal so later face tests retain a consistent winding.
        simplex.set(1, c);
        simplex.set(2, b);
        return new Result(false, abc.negate());
    }

    private static Result lineOrPoint(List<Vec3> simplex, Vec3 a, Vec3 b, Vec3 ao) {
        Vec3 ab = b.subtract(a);
        if (ab.dot(ao) > 0) {
            simplex.clear();
            simplex.add(a);
            simplex.add(b);
            return new Result(false, triple(ab, ao, ab));
        }
        simplex.clear();
        simplex.add(a);
        return new Result(false, ao);
    }

    private static Result tetrahedron(List<Vec3> simplex, Vec3 a, Vec3 ao) {
        Vec3 b = simplex.get(1);
        Vec3 c = simplex.get(2);
        Vec3 d = simplex.get(3);
        Result result = outsideFace(simplex, a, b, c, d, ao);
        if (result != null) return result;
        result = outsideFace(simplex, a, c, d, b, ao);
        if (result != null) return result;
        result = outsideFace(simplex, a, d, b, c, ao);
        if (result != null) return result;
        return new Result(true, new Vec3(0, 0, 0));
    }

    private static Result outsideFace(List<Vec3> simplex, Vec3 a, Vec3 b,
                                      Vec3 c, Vec3 opposite, Vec3 ao) {
        Vec3 normal = b.subtract(a).cross(c.subtract(a));
        if (normal.dot(opposite.subtract(a)) > 0) normal = normal.negate();
        if (normal.dot(ao) <= 0) return null;
        simplex.clear();
        simplex.add(a);
        simplex.add(b);
        simplex.add(c);
        return triangle(simplex, a, ao);
    }

    private static Vec3 triple(Vec3 a, Vec3 b, Vec3 c) {
        return a.cross(b).cross(c);
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x()) && Double.isFinite(vector.y()) && Double.isFinite(vector.z());
    }

    private record Result(boolean contains, Vec3 direction) {
    }
}
