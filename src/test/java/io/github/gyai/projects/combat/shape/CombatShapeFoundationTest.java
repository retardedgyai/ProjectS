package io.github.gyai.projects.combat.shape;

import io.github.gyai.projects.combat.shape.bukkit.BukkitAabbAdapter;
import org.bukkit.util.BoundingBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/** Server-free executable characterization of the combat shape contract. */
public final class CombatShapeFoundationTest {
    private static final int CONE_ORACLE_SAMPLES = 96;
    private static final double CONE_SUPPORT_EPSILON = GeometryTolerance.LENGTH;

    private CombatShapeFoundationTest() {
    }

    public static void main(String[] args) throws Exception {
        vec3AndAabb();
        numericDomainCases();
        sphereCases();
        cylinderCases();
        ringCases();
        boxCases();
        lineCases();
        coneCases();
        validationCases();
        propertyCases();
        pureQueryPipeline();
        bukkitAdapter();
        coreHasNoBukkitImports();
    }

    private static void vec3AndAabb() {
        Vec3 vector = new Vec3(3, 4, 0);
        assert vector.add(new Vec3(1, 2, 3)).equals(new Vec3(4, 6, 3));
        assert vector.subtract(new Vec3(1, 2, 3)).equals(new Vec3(2, 2, -3));
        assert vector.scale(2).equals(new Vec3(6, 8, 0));
        assert vector.dot(new Vec3(1, 0, 0)) == 3;
        assert vector.lengthSquared() == 25;
        assert vector.length() == 5;
        assert Math.abs(vector.normalized().length() - 1) < 1e-12;
        assertVecNear(new Vec3(Double.MAX_VALUE, Double.MAX_VALUE / 2, -Double.MAX_VALUE / 4)
                .normalized(), new Vec3(1, .5, -.25).normalized(), 1e-15);
        assert new Vec3(Double.MIN_VALUE, 0, 0).normalized().equals(new Vec3(1, 0, 0));
        expectIllegal(() -> new Vec3(Double.NaN, 0, 0));
        expectIllegal(() -> new Vec3(0, Double.POSITIVE_INFINITY, 0));
        expectIllegal(() -> new Vec3(0, 0, 0).normalized());
        expectIllegal(() -> new Aabb(1, 0, 0, 0, 1, 1));

        Aabb box = new Aabb(0, 0, 0, 2, 2, 2);
        assert box.overlaps(new Aabb(2, 2, 2, 3, 3, 3));
        assert !box.overlaps(new Aabb(2.01, 0, 0, 3, 1, 1));
        assert box.closestPoint(new Vec3(3, -1, 1)).equals(new Vec3(2, 0, 1));
        assert box.expand(1).equals(new Aabb(-1, -1, -1, 3, 3, 3));
        assert box.translate(new Vec3(1, -2, 3)).equals(new Aabb(1, -2, 3, 3, 0, 5));
    }

    private static void numericDomainCases() {
        double limit = GeometryDomain.MAX_ABSOLUTE_COORDINATE;
        double inside = limit - 4.0;
        SphereShape sphere = new SphereShape(new Vec3(inside, 0, 0), 1.0);
        LineShape line = new LineShape(new Vec3(inside - 2, 0, 0),
                new Vec3(inside - 1, 0, 0), .25);
        ConeShape cone = new ConeShape(new Vec3(inside, 0, 0),
                new Vec3(-1, .1, 0), 2.0, .4);
        AxisAlignedBoxShape box = new AxisAlignedBoxShape(
                new Aabb(inside - 1, -1, -1, inside, 1, 1));
        assert finite(sphere.broadPhaseBounds());
        assert finite(line.broadPhaseBounds());
        assert finite(cone.broadPhaseBounds());
        assert finite(box.broadPhaseBounds());
        assert new Aabb(-limit, -1, -1, limit, 1, 1).maxX() == limit;

        SphereShape maximumRadius = new SphereShape(new Vec3(0, 0, 0), limit - 1);
        LineShape maximumLength = new LineShape(new Vec3(-limit + 1, 0, 0),
                new Vec3(limit - 1, 0, 0), .5);
        ConeShape maximumConeLength = new ConeShape(new Vec3(-limit + 2, 0, 0),
                new Vec3(1, 0, 0), GeometryDomain.MAX_EXTENT - 4, 1e-8);
        assert finite(maximumRadius.broadPhaseBounds());
        assert finite(maximumLength.broadPhaseBounds());
        assert finite(maximumConeLength.broadPhaseBounds());
        assert GeometryDomain.requireExtent(GeometryDomain.MAX_EXTENT, "extent")
                == GeometryDomain.MAX_EXTENT;

        double outside = Math.nextUp(limit);
        expectIllegal(() -> new SphereShape(new Vec3(outside, 0, 0), 1));
        expectIllegal(() -> new LineShape(new Vec3(0, 0, 0), new Vec3(outside, 0, 0), 1));
        expectIllegal(() -> new ConeShape(new Vec3(outside, 0, 0), new Vec3(1, 0, 0), 1, .4));
        expectIllegal(() -> new Aabb(-1, -1, -1, outside, 1, 1));
        expectIllegal(() -> GeometryDomain.requireExtent(
                Math.nextUp(GeometryDomain.MAX_EXTENT), "extent"));
        expectIllegal(() -> new SphereShape(new Vec3(0, 0, 0),
                Math.nextUp(GeometryDomain.MAX_EXTENT)));
        expectIllegal(() -> new LineShape(new Vec3(-limit, 0, 0),
                new Vec3(limit, 0, 0), Math.nextUp(GeometryTolerance.LENGTH)));
        expectIllegal(() -> new ConeShape(new Vec3(0, 0, 0), new Vec3(1, 0, 0),
                Math.nextUp(GeometryDomain.MAX_EXTENT), .1));
    }

    private static void sphereCases() {
        SphereShape sphere = new SphereShape(new Vec3(0, 0, 0), 1);
        assert sphere.intersects(new Aabb(-.1, -.1, -.1, .1, .1, .1));
        assert sphere.intersects(new Aabb(.9, -.1, -.1, 1.1, .1, .1));
        assert sphere.intersects(new Aabb(1, 0, 0, 2, 1, 1));
        assert sphere.intersects(new Aabb(.9, -.1, -.1, 2, .1, .1));
        assert !sphere.intersects(new Aabb(1.01, 0, 0, 2, 1, 1));
    }

    private static void cylinderCases() {
        UprightCylinderShape cylinder = new UprightCylinderShape(new Vec3(0, 0, 0), 1, 2);
        assert cylinder.intersects(new Aabb(.8, -1, 0, 2, 1, 1));
        assert cylinder.intersects(new Aabb(0, 2, 0, 1, 3, 1));
        assert !cylinder.intersects(new Aabb(0, 2.01, 0, 1, 3, 1));
        assert !cylinder.intersects(new Aabb(0, -3, 0, 1, -2.01, 1));
        assert cylinder.intersects(new Aabb(1, -1, 0, 2, 1, 1));
        assert cylinder.intersects(new Aabb(.7, -1, .7, 2, 1, 2));
    }

    private static void ringCases() {
        HorizontalRingShape ring = new HorizontalRingShape(new Vec3(0, 0, 0), 1, 3, 1);
        assert !ring.intersects(new Aabb(-.2, 0, -.2, .2, 1, .2));
        assert ring.intersects(new Aabb(1.5, 0, 0, 2, 1, .1));
        assert !ring.intersects(new Aabb(3.1, 0, 0, 4, 1, 1));
        assert ring.intersects(new Aabb(0, 0, 0, 2, 1, .1));
        assert ring.intersects(new Aabb(1, 0, 0, 1.1, 1, .1));
        assert ring.intersects(new Aabb(3, 0, 0, 3.1, 1, .1));
    }

    private static void boxCases() {
        AxisAlignedBoxShape shape = new AxisAlignedBoxShape(new Aabb(0, 0, 0, 2, 2, 2));
        assert shape.intersects(new Aabb(1, 1, 1, 3, 3, 3));
        assert shape.intersects(new Aabb(2, 0, 0, 3, 1, 1));
        assert !shape.intersects(new Aabb(2.1, 0, 0, 3, 1, 1));
        assert shape.intersects(new Aabb(.5, .5, .5, 1, 1, 1));
    }

    private static void lineCases() {
        LineShape line = new LineShape(new Vec3(-2, 0, 0), new Vec3(2, 0, 0), .25);
        assert line.intersects(new Aabb(-.1, .2, -.1, .1, .3, .1));
        assert line.intersects(new Aabb(2.2, -.05, -.05, 2.3, .05, .05));
        assert !line.intersects(new Aabb(2.251, -.05, -.05, 2.3, .05, .05));
        assert !line.intersects(new Aabb(-.1, .251, -.1, .1, .4, .1));
        assert line.intersects(new Aabb(-.1, .25, -.1, .1, .25, .1));
        LineShape longLine = new LineShape(new Vec3(0, 0, 0),
                new Vec3(1_000_000.0, 0, 0), .01);
        assert longLine.intersects(new Aabb(1, .009, -.001, 1.0001, .011, .001));
    }

    private static void coneCases() {
        ConeShape cone = new ConeShape(new Vec3(0, 0, 0), new Vec3(1, 0, 0), 4, Math.PI / 4);
        assert cone.intersects(new Aabb(2, -.1, -.1, 2.1, .1, .1));
        assert cone.intersects(new Aabb(3, 2.9, -.1, 3.1, 3.1, .1));
        assert !cone.intersects(new Aabb(-2, -1, -1, -1, 1, 1));
        assert !cone.intersects(new Aabb(4.01, -1, -1, 5, 1, 1));
        assert !cone.intersects(new Aabb(3, 10, -.1, 3.2, 11, .1));
        Aabb edgeCrossing = new Aabb(3.0, 2.9, -.1, 3.1, 3.4, .1);
        assert coneCenterOutside(cone, edgeCrossing);
        assert cone.intersects(edgeCrossing);
        assert !cone.intersects(new Aabb(3.0, 3.5, -.1, 3.1, 3.7, .1));
        ConeShape translated = new ConeShape(new Vec3(10, 5, -3), new Vec3(0, 1, 1), 3, .4);
        assert translated.intersects(new Aabb(9.9, 6, -2.1, 10.1, 6.2, -1.8));
        ConeShape huge = new ConeShape(new Vec3(0, 0, 0),
                new Vec3(1, 1e-15, 0), 1_000_000, 1.2);
        assert huge.broadPhaseBounds().maxY() > 100_000;
        assert cone.intersects(new Aabb(3.9, 0, 0, 4, .1, .1));

        ConeShape diagonal = new ConeShape(new Vec3(0, 0, 0), new Vec3(1, 1, 2), 4, 1.2);
        Vec3 diagonalAxisHit = diagonal.origin().add(diagonal.forward().scale(2));
        Aabb diagonalHit = around(diagonalAxisHit, .01);
        assert diagonal.intersects(diagonalHit);
        assert diagonal.broadPhaseBounds().overlaps(diagonalHit);
        ConeShape rotatedDiagonal = new ConeShape(new Vec3(0, 0, 0), rotateY(diagonal.forward()), 4, 1.2);
        ConeShape reflectedDiagonal = new ConeShape(new Vec3(0, 0, 0), reflectZ(diagonal.forward()), 4, 1.2);
        assert diagonal.intersects(diagonalHit) == rotatedDiagonal.intersects(rotateY(diagonalHit));
        assert diagonal.intersects(diagonalHit) == reflectedDiagonal.intersects(reflectZ(diagonalHit));
        assert rotatedDiagonal.broadPhaseBounds().overlaps(rotateY(diagonalHit));
        assert reflectedDiagonal.broadPhaseBounds().overlaps(reflectZ(diagonalHit));

        // IEEE-754 normalization of this diagonal has a squared length slightly different from one.
        assert diagonal.forward().lengthSquared() != 1.0;
        Vec3 base = diagonal.origin().add(diagonal.forward().scale(diagonal.length()));
        assertVecNear(diagonal.support(diagonal.forward()), base, CONE_SUPPORT_EPSILON);
        Vec3 perpendicular = conePerpendicular(diagonal.forward());
        Vec3 nearAxisDirection = diagonal.forward().scale(5).add(perpendicular.scale(1e-15));
        Vec3 nearAxisSupport = diagonal.support(nearAxisDirection);
        Vec3 nearAxisOffset = nearAxisSupport.subtract(base);
        assert finite(nearAxisSupport);
        assert nearAxisSupport.equals(diagonal.support(nearAxisDirection));
        double nearAxisOmittedSupport = coneBaseRadius(diagonal)
                * projectedRadial(diagonal, nearAxisDirection).length();
        assert nearAxisOmittedSupport <= GeometryTolerance.CONE_SUPPORT_ERROR_BUDGET;
        assert nearAxisOffset.length() <= CONE_SUPPORT_EPSILON;

        double aboveFallback = 2.0 * (GeometryTolerance.CONE_SUPPORT_ERROR_BUDGET
                / coneBaseRadius(diagonal) + GeometryTolerance.UNIT_PROJECTION_ERROR);
        Vec3 rimDirection = diagonal.forward().add(perpendicular.scale(aboveFallback));
        Vec3 rimSupport = diagonal.support(rimDirection);
        assert Math.abs(rimSupport.subtract(base).length() - coneBaseRadius(diagonal))
                <= CONE_SUPPORT_EPSILON;
        Aabb rimContact = around(rimSupport, GeometryTolerance.LENGTH);
        assert diagonal.broadPhaseBounds().overlaps(rimContact);
        assert diagonal.intersects(rimContact);
        coneSupportOracle(diagonal, diagonal.forward());
        coneSupportOracle(diagonal, new Vec3(2, -3, 5));

        hugeCrossCancellationRegression();
        supportDirectionScaleInvariance(diagonal);
    }

    /** Exact stored-double fixture where the former raw huge cross rounded to zero. */
    private static void hugeCrossCancellationRegression() {
        Vec3 rawForward = new Vec3(-0x1.ae5c4f3e67f78p-2,
                -0x1.4908a2c9de61p-3, -0x1.c99e64576be6cp-1);
        ConeShape cone = new ConeShape(new Vec3(0, 0, 0), rawForward, 128, .7);
        Vec3 hugeDirection = new Vec3(-0x1.7ec919f08a97p1018,
                -0x1.24a8fa78960d8p1017, -0x1.9707c73ec8e57p1019);
        assert hugeDirection.cross(cone.forward()).equals(new Vec3(0, 0, 0));
        Vec3 projected = projectedRadial(cone, hugeDirection);
        assert projected.length() > 0.0;
        Vec3 actual = cone.support(hugeDirection);
        Vec3 base = cone.origin().add(cone.forward().scale(cone.length()));
        assert coneBaseRadius(cone)
                * (projected.length() + GeometryTolerance.UNIT_PROJECTION_ERROR)
                <= GeometryTolerance.CONE_SUPPORT_ERROR_BUDGET;
        assertVecNear(actual, base, CONE_SUPPORT_EPSILON);

        expectIllegal(() -> new ConeShape(new Vec3(0, 0, 0), rawForward,
                1_000_000_000_000.0, 1.2));
    }

    private static void supportDirectionScaleInvariance(ConeShape cone) {
        Vec3 baselineDirection = new Vec3(1, 2, 3);
        Vec3 baseline = cone.support(baselineDirection);
        double[] scales = {1e-300, 1e-200, 1e-100, 1e-10, 1.0,
                1e10, 1e100, 1e200, 1e307 / 3.0};
        for (double scale : scales) {
            Vec3 scaled = new Vec3(scale, 2 * scale, 3 * scale);
            assertVecNear(cone.support(scaled), baseline, CONE_SUPPORT_EPSILON);
        }
    }

    private static void validationCases() {
        expectIllegal(() -> new SphereShape(new Vec3(0, 0, 0), 0));
        expectIllegal(() -> new SphereShape(new Vec3(0, 0, 0), Double.NaN));
        expectIllegal(() -> new SphereShape(new Vec3(0, 0, 0), Double.POSITIVE_INFINITY));
        expectIllegal(() -> new UprightCylinderShape(new Vec3(0, 0, 0), 0, 1));
        expectIllegal(() -> new UprightCylinderShape(new Vec3(0, 0, 0), Double.NaN, 1));
        expectIllegal(() -> new UprightCylinderShape(new Vec3(0, 0, 0), 1, Double.POSITIVE_INFINITY));
        expectIllegal(() -> new HorizontalRingShape(new Vec3(0, 0, 0), 2, 2, 1));
        expectIllegal(() -> new HorizontalRingShape(new Vec3(0, 0, 0), Double.NaN, 2, 1));
        expectIllegal(() -> new HorizontalRingShape(new Vec3(0, 0, 0), 1, Double.POSITIVE_INFINITY, 1));
        expectIllegal(() -> new HorizontalRingShape(new Vec3(0, 0, 0), 0, 2, 0));
        expectIllegal(() -> new LineShape(new Vec3(0, 0, 0), new Vec3(0, 0, 0), 1));
        expectIllegal(() -> new LineShape(new Vec3(0, 0, 0), new Vec3(1, 0, 0), Double.NaN));
        expectIllegal(() -> new LineShape(new Vec3(0, 0, 0), new Vec3(1, 0, 0), Double.NEGATIVE_INFINITY));
        expectIllegal(() -> new ConeShape(new Vec3(0, 0, 0), new Vec3(0, 0, 0), 1, .1));
        expectIllegal(() -> new ConeShape(new Vec3(0, 0, 0), new Vec3(1, 0, 0), Double.NaN, .1));
        expectIllegal(() -> new ConeShape(new Vec3(0, 0, 0), new Vec3(1, 0, 0), 1, Double.POSITIVE_INFINITY));
        expectIllegal(() -> new ConeShape(new Vec3(0, 0, 0), new Vec3(1, 0, 0), 1, 0));
        expectIllegal(() -> new ConeShape(new Vec3(0, 0, 0), new Vec3(1, 0, 0), 1, Math.PI / 2));
        expectIllegal(() -> new ConeShape(new Vec3(0, 0, 0), new Vec3(1, 0, 0),
                Double.MIN_VALUE, Double.MIN_VALUE));
        expectIllegal(() -> new Aabb(0, 0, 0, Double.NaN, 1, 1));
        expectIllegal(() -> new Aabb(0, 0, 0, 1, 1, Double.POSITIVE_INFINITY));
        expectIllegal(() -> new Aabb(0, 0, 0, 1, 1, 1).expand(-.1));
        expectIllegal(() -> new Aabb(0, 0, 0, 1, 1, 1).expand(Double.NaN));
    }

    private static void propertyCases() {
        Random random = new Random(99173L);
        for (int i = 0; i < 100; i++) checkProperties(random, randomSphere(random));
        for (int i = 0; i < 100; i++) checkProperties(random, randomCylinder(random));
        for (int i = 0; i < 100; i++) checkProperties(random, randomRing(random));
        for (int i = 0; i < 100; i++) checkProperties(random, randomBox(random));
        for (int i = 0; i < 100; i++) checkProperties(random, randomLine(random));
        for (int i = 0; i < 100; i++) checkProperties(random, randomCone(random));
        symmetryCases();
        coneGuaranteedHitWitnesses(random);
        coneSupportProperties(random);
    }

    private static void checkProperties(Random random, CombatShape shape) {
        Aabb target = randomTarget(random);
        Aabb broad = shape.broadPhaseBounds();
        assert finite(broad);
        if (shape.intersects(target)) assert broad.overlaps(target);
        Vec3 shift = new Vec3(4.25, -3.5, 7.75);
        assert shape.intersects(target) == translated(shape, shift).intersects(target.translate(shift));
    }

    private static SphereShape randomSphere(Random random) {
        return new SphereShape(randomPoint(random), between(random, .1, 3));
    }

    private static UprightCylinderShape randomCylinder(Random random) {
        return new UprightCylinderShape(randomPoint(random), between(random, .1, 3), between(random, .1, 3));
    }

    private static HorizontalRingShape randomRing(Random random) {
        double inner = between(random, 0, 2);
        return new HorizontalRingShape(randomPoint(random), inner, inner + between(random, .1, 3), between(random, .1, 3));
    }

    private static AxisAlignedBoxShape randomBox(Random random) {
        Vec3 point = randomPoint(random);
        double x = between(random, .1, 3), y = between(random, .1, 3), z = between(random, .1, 3);
        return new AxisAlignedBoxShape(new Aabb(point.x() - x, point.y() - y, point.z() - z, point.x() + x, point.y() + y, point.z() + z));
    }

    private static LineShape randomLine(Random random) {
        Vec3 start = randomPoint(random);
        return new LineShape(start, start.add(new Vec3(between(random, .2, 4), between(random, -.5, .5), between(random, -.5, .5))), between(random, .1, 2));
    }

    private static ConeShape randomCone(Random random) {
        return new ConeShape(randomPoint(random), new Vec3(between(random, .2, 1), between(random, -.8, .8), between(random, -.8, .8)), between(random, .2, 4), between(random, .1, 1.1));
    }

    private static Aabb randomTarget(Random random) {
        Vec3 point = randomPoint(random);
        double x = between(random, .05, 2), y = between(random, .05, 2), z = between(random, .05, 2);
        return new Aabb(point.x() - x, point.y() - y, point.z() - z, point.x() + x, point.y() + y, point.z() + z);
    }

    private static Vec3 randomPoint(Random random) {
        return new Vec3(between(random, -10, 10), between(random, -10, 10), between(random, -10, 10));
    }

    private static double between(Random random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private static CombatShape translated(CombatShape shape, Vec3 shift) {
        if (shape instanceof SphereShape sphere) return new SphereShape(sphere.center().add(shift), sphere.radius());
        if (shape instanceof UprightCylinderShape cylinder) return new UprightCylinderShape(cylinder.center().add(shift), cylinder.radius(), cylinder.verticalHalfHeight());
        if (shape instanceof HorizontalRingShape ring) return new HorizontalRingShape(ring.center().add(shift), ring.innerRadius(), ring.outerRadius(), ring.verticalHalfHeight());
        if (shape instanceof AxisAlignedBoxShape box) return new AxisAlignedBoxShape(box.bounds().translate(shift));
        if (shape instanceof LineShape line) return new LineShape(line.start().add(shift), line.end().add(shift), line.radius());
        ConeShape cone = (ConeShape) shape;
        return new ConeShape(cone.origin().add(shift), cone.forward(), cone.length(), cone.halfAngleRadians());
    }

    private static void symmetryCases() {
        Aabb target = new Aabb(-3, -.5, 1, -1, .5, 2);
        Aabb reflected = reflectXz(target);
        assert new SphereShape(new Vec3(0, 0, 0), 2).intersects(target) == new SphereShape(new Vec3(0, 0, 0), 2).intersects(reflected);
        assert new UprightCylinderShape(new Vec3(0, 0, 0), 2, 1).intersects(target) == new UprightCylinderShape(new Vec3(0, 0, 0), 2, 1).intersects(reflected);
        assert new HorizontalRingShape(new Vec3(0, 0, 0), .5, 3, 1).intersects(target) == new HorizontalRingShape(new Vec3(0, 0, 0), .5, 3, 1).intersects(reflected);
        assert new AxisAlignedBoxShape(new Aabb(-2, -1, -2, 2, 1, 2)).intersects(target) == new AxisAlignedBoxShape(new Aabb(-2, -1, -2, 2, 1, 2)).intersects(reflected);
        LineShape line = new LineShape(new Vec3(-2, 0, -1), new Vec3(3, 1, 2), .5);
        assert line.intersects(target) == new LineShape(line.end(), line.start(), line.radius()).intersects(target);
        ConeShape cone = new ConeShape(new Vec3(0, 0, 0), new Vec3(1, .2, -.3), 4, .6);
        ConeShape reflectedCone = new ConeShape(new Vec3(0, 0, 0), new Vec3(1, .2, .3), 4, .6);
        assert cone.intersects(target) == reflectedCone.intersects(reflectZ(target));
    }

    private static void coneGuaranteedHitWitnesses(Random random) {
        for (int i = 0; i < 100; i++) {
            ConeShape cone = randomCone(random);
            double distance = cone.length() * between(random, .1, .9);
            Vec3 baseDirection = cone.forward().cross(new Vec3(0, 1, 0)).normalized();
            double radius = distance * Math.tan(cone.halfAngleRadians()) * .4;
            Vec3 inside = cone.origin().add(cone.forward().scale(distance)).add(baseDirection.scale(radius));
            Aabb witness = new Aabb(inside.x() - .01, inside.y() - .01, inside.z() - .01, inside.x() + .01, inside.y() + .01, inside.z() + .01);
            assert cone.intersects(witness);
            assert cone.broadPhaseBounds().overlaps(witness);
        }
    }

    private static void coneSupportProperties(Random random) {
        for (int i = 0; i < 40; i++) {
            ConeShape cone = randomCone(random);
            Vec3 firstPerpendicular = conePerpendicular(cone.forward());
            Vec3 secondPerpendicular = cone.forward().cross(firstPerpendicular).normalized();
            for (int directionIndex = 0; directionIndex < 6; directionIndex++) {
                Vec3 direction = cone.forward().scale(between(random, -2, 2))
                        .add(firstPerpendicular.scale(between(random, .1, 2)))
                        .add(secondPerpendicular.scale(between(random, -2, 2)));
                coneSupportOracle(cone, direction);
                assertVecNear(cone.support(direction), cone.support(direction.scale(1e200)),
                        CONE_SUPPORT_EPSILON);
                assertVecNear(cone.support(direction), cone.support(direction.scale(1e-200)),
                        CONE_SUPPORT_EPSILON);
            }
        }
    }

    /** Samples the apex and base rim; their convex hull is the complete finite solid cone. */
    private static void coneSupportOracle(ConeShape cone, Vec3 direction) {
        Vec3 support = cone.support(direction);
        double supportDot = support.dot(direction);
        assert supportDot + CONE_SUPPORT_EPSILON >= cone.origin().dot(direction);
        Vec3 base = cone.origin().add(cone.forward().scale(cone.length()));
        Vec3 firstPerpendicular = conePerpendicular(cone.forward());
        Vec3 secondPerpendicular = cone.forward().cross(firstPerpendicular).normalized();
        double radius = coneBaseRadius(cone);
        for (int i = 0; i < CONE_ORACLE_SAMPLES; i++) {
            double angle = 2.0 * Math.PI * i / CONE_ORACLE_SAMPLES;
            Vec3 candidate = base.add(firstPerpendicular.scale(radius * Math.cos(angle)))
                    .add(secondPerpendicular.scale(radius * Math.sin(angle)));
            double candidateDot = candidate.dot(direction);
            assert supportDot + CONE_SUPPORT_EPSILON >= candidateDot;
        }
    }

    private static double coneBaseRadius(ConeShape cone) {
        return cone.length() * Math.tan(cone.halfAngleRadians());
    }

    private static Vec3 projectedRadial(ConeShape cone, Vec3 direction) {
        Vec3 axis = cone.forward();
        Vec3 unitDirection = direction.normalized();
        double projection = unitDirection.dot(axis) / axis.dot(axis);
        return new Vec3(Math.fma(-axis.x(), projection, unitDirection.x()),
                Math.fma(-axis.y(), projection, unitDirection.y()),
                Math.fma(-axis.z(), projection, unitDirection.z()));
    }

    private static Vec3 conePerpendicular(Vec3 axis) {
        Vec3 reference = Math.abs(axis.x()) <= Math.abs(axis.y()) && Math.abs(axis.x()) <= Math.abs(axis.z())
                ? new Vec3(1, 0, 0)
                : Math.abs(axis.y()) <= Math.abs(axis.z()) ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
        return axis.cross(reference).normalized();
    }

    private static Aabb around(Vec3 point, double radius) {
        return new Aabb(point.x() - radius, point.y() - radius, point.z() - radius,
                point.x() + radius, point.y() + radius, point.z() + radius);
    }

    private static void assertVecNear(Vec3 actual, Vec3 expected, double epsilon) {
        assert actual.subtract(expected).length() <= epsilon;
    }

    private static void pureQueryPipeline() {
        record Candidate(String id, boolean eligible, Aabb bounds) { }
        CombatShape shape = new SphereShape(new Vec3(0, 0, 0), 2);
        Aabb[] providerBounds = new Aabb[1];
        List<Candidate> candidates = List.of(
                new Candidate("z", true, new Aabb(0, 0, 0, .1, .1, .1)),
                new Candidate("skip", false, new Aabb(0, 0, 0, .1, .1, .1)),
                new Candidate("miss", true, new Aabb(9, 0, 0, 10, 1, 1)),
                new Candidate("a", true, new Aabb(1, 0, 0, 2, 1, 1)));
        List<Candidate> result = CombatShapeQuery.query(shape, bounds -> {
            providerBounds[0] = bounds;
            return candidates;
        }, Candidate::eligible, Candidate::bounds, java.util.Comparator.comparing(Candidate::id));
        List<Candidate> reversed = CombatShapeQuery.query(shape, ignored -> List.of(
                candidates.get(3), candidates.get(2), candidates.get(1), candidates.get(0)),
                Candidate::eligible, Candidate::bounds, java.util.Comparator.comparing(Candidate::id));
        assert providerBounds[0].equals(shape.broadPhaseBounds());
        assert result.stream().map(Candidate::id).toList().equals(List.of("a", "z"));
        assert result.equals(reversed);
    }

    private static void bukkitAdapter() {
        BoundingBox bukkit = new BoundingBox(1, 2, 3, 4, 5, 6);
        Aabb pure = BukkitAabbAdapter.fromBukkit(bukkit);
        assert pure.equals(new Aabb(1, 2, 3, 4, 5, 6));
        BoundingBox roundTrip = BukkitAabbAdapter.toBukkit(pure);
        assert roundTrip.getMinX() == 1 && roundTrip.getMaxZ() == 6;
    }

    private static void coreHasNoBukkitImports() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java/io/github/gyai/projects/combat/shape"))) {
            assert paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("bukkit"))
                    .noneMatch(path -> {
                        try {
                            return Files.readString(path).contains("import org.bukkit");
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        }
    }

    private static boolean finite(Aabb box) {
        return Double.isFinite(box.minX()) && Double.isFinite(box.minY()) && Double.isFinite(box.minZ())
                && Double.isFinite(box.maxX()) && Double.isFinite(box.maxY()) && Double.isFinite(box.maxZ());
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x()) && Double.isFinite(vector.y()) && Double.isFinite(vector.z());
    }

    private static Aabb reflectXz(Aabb box) {
        return new Aabb(-box.maxX(), box.minY(), -box.maxZ(), -box.minX(), box.maxY(), -box.minZ());
    }

    private static Aabb reflectZ(Aabb box) {
        return new Aabb(box.minX(), box.minY(), -box.maxZ(), box.maxX(), box.maxY(), -box.minZ());
    }

    private static Vec3 reflectZ(Vec3 vector) {
        return new Vec3(vector.x(), vector.y(), -vector.z());
    }

    private static Aabb rotateY(Aabb box) {
        return new Aabb(box.minZ(), box.minY(), -box.maxX(), box.maxZ(), box.maxY(), -box.minX());
    }

    private static Vec3 rotateY(Vec3 vector) {
        return new Vec3(vector.z(), vector.y(), -vector.x());
    }

    private static boolean coneCenterOutside(ConeShape cone, Aabb box) {
        Vec3 offset = box.center().subtract(cone.origin());
        double along = offset.dot(cone.forward());
        if (along < 0 || along > cone.length()) return true;
        Vec3 radial = offset.subtract(cone.forward().scale(along));
        return radial.length() > along * Math.tan(cone.halfAngleRadians());
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }
}
