package io.github.gyai.projects.combat.shape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Finite swept segment (capsule), including both rounded endpoints. */
public record LineShape(Vec3 start, Vec3 end, double radius) implements CombatShape {
    public LineShape {
        start = GeometryDomain.requirePosition(Objects.requireNonNull(start, "start"), "start");
        end = GeometryDomain.requirePosition(Objects.requireNonNull(end, "end"), "end");
        GeometryDomain.requireExtent(radius, "radius");
        if (radius <= 0 || start.equals(end)) {
            throw new IllegalArgumentException("line endpoints must be distinct and radius positive");
        }
        GeometryDomain.requireExtent(end.subtract(start).length(), "line length");
        double padding = radius + GeometryTolerance.LENGTH;
        GeometryDomain.requireRange(Math.min(start.x(), end.x()) - padding,
                Math.max(start.x(), end.x()) + padding, "line.x");
        GeometryDomain.requireRange(Math.min(start.y(), end.y()) - padding,
                Math.max(start.y(), end.y()) + padding, "line.y");
        GeometryDomain.requireRange(Math.min(start.z(), end.z()) - padding,
                Math.max(start.z(), end.z()) + padding, "line.z");
    }
    public Aabb broadPhaseBounds() {
        return new Aabb(Math.min(start.x(), end.x()) - radius,
                Math.min(start.y(), end.y()) - radius,
                Math.min(start.z(), end.z()) - radius,
                Math.max(start.x(), end.x()) + radius,
                Math.max(start.y(), end.y()) + radius,
                Math.max(start.z(), end.z()) + radius).expand(GeometryTolerance.LENGTH);
    }
    /**
     * Exact segment-to-box distance: slab crossings partition the segment into
     * intervals on which the point-to-box squared distance is a quadratic.
     */
    public boolean intersects(Aabb box) {
        Objects.requireNonNull(box, "box");
        Vec3 delta = end.subtract(start);
        List<Double> cuts = new ArrayList<>(8);
        cuts.add(0.0);
        cuts.add(1.0);
        addCut(cuts, box.minX(), start.x(), delta.x());
        addCut(cuts, box.maxX(), start.x(), delta.x());
        addCut(cuts, box.minY(), start.y(), delta.y());
        addCut(cuts, box.maxY(), start.y(), delta.y());
        addCut(cuts, box.minZ(), start.z(), delta.z());
        addCut(cuts, box.maxZ(), start.z(), delta.z());
        try {
            cuts.sort(Comparator.naturalOrder());
            double limit = SphereShape.sq(radius + GeometryTolerance.LENGTH);
            for (int i = 0; i < cuts.size() - 1; i++) {
                double low = cuts.get(i);
                double high = cuts.get(i + 1);
                if (low == high) {
                    continue;
                }
                double mid = low + (high - low) * 0.5;
                double[] x = quadratic(start.x(), delta.x(), mid, box.minX(), box.maxX());
                double[] y = quadratic(start.y(), delta.y(), mid, box.minY(), box.maxY());
                double[] z = quadratic(start.z(), delta.z(), mid, box.minZ(), box.maxZ());
                double a = x[0] + y[0] + z[0];
                double b = x[1] + y[1] + z[1];
                double c = x[2] + y[2] + z[2];
                if (!finite(a, b, c, limit)) {
                    return true;
                }
                if (atMost(evaluate(a, b, c, low), limit)
                        || atMost(evaluate(a, b, c, high), limit)) {
                    return true;
                }
                if (a > 0.0) {
                    double stationary = -b / (2.0 * a);
                    if (!Double.isFinite(stationary)) {
                        return true;
                    }
                    if (stationary > low && stationary < high
                            && atMost(evaluate(a, b, c, stationary), limit)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (RuntimeException ignored) {
            return true;
        }
    }
    private static double[] quadratic(double start, double delta, double mid,
                                      double min, double max) {
        double value = start + delta * mid;
        if (value < min) return coefficients(start - min, delta);
        if (value > max) return coefficients(start - max, delta);
        return new double[] {0, 0, 0};
    }
    private static double[] coefficients(double offset, double delta) {
        return new double[] {delta * delta, 2 * delta * offset, offset * offset};
    }
    private static double evaluate(double a, double b, double c, double t) {
        return a * t * t + b * t + c;
    }
    private static boolean atMost(double value, double limit) {
        return !Double.isFinite(value) || value <= limit;
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
    private static void addCut(List<Double> cuts, double plane, double origin, double delta) {
        if (delta == 0.0) return;
        double t = (plane - origin) / delta;
        if (t > 0.0 && t < 1.0 && Double.isFinite(t)) cuts.add(t);
    }
}
