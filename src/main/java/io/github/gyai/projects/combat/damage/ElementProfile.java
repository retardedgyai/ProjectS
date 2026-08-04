package io.github.gyai.projects.combat.damage;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable elemental values and per-attack scaling rates. */
public record ElementProfile(
        Map<DamageElement, Double> values,
        Map<DamageElement, Double> scalingRates
) {
    public static final ElementProfile EMPTY =
            new ElementProfile(Map.of(), Map.of());

    public ElementProfile {
        values = immutableNonNegative(values, "element value");
        scalingRates = immutableNonNegative(
                scalingRates, "element scaling rate");
    }

    public double value(DamageElement element) {
        return values.getOrDefault(
                Objects.requireNonNull(element, "element"), 0.0);
    }

    public double scalingRate(DamageElement element) {
        return scalingRates.getOrDefault(
                Objects.requireNonNull(element, "element"), 0.0);
    }

    private static Map<DamageElement, Double> immutableNonNegative(
            Map<DamageElement, Double> input,
            String description
    ) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        EnumMap<DamageElement, Double> copy =
                new EnumMap<>(DamageElement.class);
        for (Map.Entry<DamageElement, Double> entry : input.entrySet()) {
            DamageElement element = Objects.requireNonNull(
                    entry.getKey(), description + " element");
            Double boxed = Objects.requireNonNull(
                    entry.getValue(), description + " for " + element);
            double value = boxed;
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException(
                        description + " must be finite and non-negative");
            }
            copy.put(element, value);
        }
        return Collections.unmodifiableMap(copy);
    }
}
