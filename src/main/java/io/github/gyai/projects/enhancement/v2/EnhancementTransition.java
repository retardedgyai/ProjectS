package io.github.gyai.projects.enhancement.v2;

/** Explicit policy-supplied next state; no outcome implies a numeric transition. */
public record EnhancementTransition(int targetLevel, boolean broken) {
    public EnhancementTransition {
        if (targetLevel < 0 || targetLevel > 30) {
            throw new IllegalArgumentException("targetLevel must be 0..30");
        }
    }
}
