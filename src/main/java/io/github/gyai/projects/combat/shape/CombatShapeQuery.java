package io.github.gyai.projects.combat.shape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/** Small pure broad-phase to narrow-phase query pipeline. */
public final class CombatShapeQuery {
    private CombatShapeQuery() {
    }

    public static <T> List<T> query(
            CombatShape shape,
            Function<? super Aabb, ? extends Iterable<T>> broadProvider,
            Predicate<? super T> eligible,
            Function<? super T, Aabb> boundsMapper,
            Comparator<? super T> comparator
    ) {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(broadProvider, "broadProvider");
        Objects.requireNonNull(eligible, "eligible");
        Objects.requireNonNull(boundsMapper, "boundsMapper");
        Objects.requireNonNull(comparator, "comparator");

        Iterable<T> candidates = Objects.requireNonNull(
                broadProvider.apply(shape.broadPhaseBounds()), "broadProvider result");
        List<T> results = new ArrayList<>();
        for (T candidate : candidates) {
            if (eligible.test(candidate) && shape.intersects(
                    Objects.requireNonNull(boundsMapper.apply(candidate), "candidate bounds"))) {
                results.add(candidate);
            }
        }
        results.sort(comparator);
        return List.copyOf(results);
    }
}
