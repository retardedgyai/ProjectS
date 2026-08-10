package io.github.gyai.projects.ability;

import java.util.Objects;
import java.util.Set;

/**
 * Pure, bounded motion metadata.  Shape sampling remains a client production concern;
 * this type is the server's validation and transport authority.
 */
public record MotionSpec(MotionMode mode, MotionDirection direction, MotionEasing easing,
                         double phase, double trailFraction) {
    public static final MotionSpec LEGACY_DEFAULT =
            new MotionSpec(MotionMode.REVEAL, MotionDirection.FORWARD, MotionEasing.LINEAR, 0.0, 0.0);

    private static final Set<AbilityVisualDefinition.PrimitiveType> REVERSE_REVEAL =
            Set.of(AbilityVisualDefinition.PrimitiveType.LINE,
                    AbilityVisualDefinition.PrimitiveType.ARC,
                    AbilityVisualDefinition.PrimitiveType.CIRCLE,
                    AbilityVisualDefinition.PrimitiveType.SPIRAL,
                    AbilityVisualDefinition.PrimitiveType.WAVE,
                    AbilityVisualDefinition.PrimitiveType.BEZIER);
    private static final Set<AbilityVisualDefinition.PrimitiveType> TRAVEL =
            Set.of(AbilityVisualDefinition.PrimitiveType.LINE,
                    AbilityVisualDefinition.PrimitiveType.ARC,
                    AbilityVisualDefinition.PrimitiveType.SPIRAL,
                    AbilityVisualDefinition.PrimitiveType.WAVE,
                    AbilityVisualDefinition.PrimitiveType.BEZIER);

    public MotionSpec {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(easing, "easing");
        if (!Double.isFinite(phase) || !Double.isFinite(trailFraction)
                || phase < 0.0 || phase > 1.0 || trailFraction < 0.0 || trailFraction > 1.0) {
            throw new IllegalArgumentException("Motion values must be finite and within [0,1]");
        }
    }

    public static MotionSpec legacyDefault() {
        return LEGACY_DEFAULT;
    }

    public boolean isLegacyDefault() {
        return equals(LEGACY_DEFAULT);
    }

    /** Validates the mode/type capability matrix, including canonical STATIC fields. */
    public void validateFor(AbilityVisualDefinition.PrimitiveType type) {
        Objects.requireNonNull(type, "type");
        switch (mode) {
            case STATIC -> {
                if (direction != MotionDirection.FORWARD || easing != MotionEasing.LINEAR
                        || phase != 0.0 || trailFraction != 0.0) {
                    throw new IllegalArgumentException("STATIC motion must be canonical");
                }
            }
            case REVEAL -> {
                if (trailFraction != 0.0) {
                    throw new IllegalArgumentException("REVEAL does not allow a trail");
                }
                if (direction == MotionDirection.REVERSE && !REVERSE_REVEAL.contains(type)) {
                    throw new IllegalArgumentException("REVEAL reverse is unsupported for " + type);
                }
            }
            case TRAVEL -> {
                if (!TRAVEL.contains(type)) {
                    throw new IllegalArgumentException("TRAVEL is unsupported for " + type);
                }
            }
        }
    }

    public boolean supports(AbilityVisualDefinition.PrimitiveType type) {
        try {
            validateFor(type);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
