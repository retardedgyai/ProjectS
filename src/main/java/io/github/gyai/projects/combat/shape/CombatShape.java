package io.github.gyai.projects.combat.shape;

/** Pure world-space combat volume. Broad bounds are only candidate bounds. */
public interface CombatShape {
    Aabb broadPhaseBounds();
    boolean intersects(Aabb targetBounds);
}
