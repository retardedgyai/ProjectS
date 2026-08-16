package io.github.gyai.projects.combat.shape;

/** Shared conservative length tolerance for combat geometry. */
public final class GeometryTolerance {
    public static final double LENGTH = 1.0e-5;
    static final double UNIT_PROJECTION_ERROR = 128.0 * Math.ulp(1.0);
    static final double CONE_SUPPORT_ERROR_BUDGET = LENGTH * 0.25;

    private GeometryTolerance() {
    }

    static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
