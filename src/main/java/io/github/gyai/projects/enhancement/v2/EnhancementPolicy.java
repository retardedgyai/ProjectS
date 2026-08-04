package io.github.gyai.projects.enhancement.v2;

import io.github.gyai.projects.equipment.operation.OperationResourcePlan;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record EnhancementPolicy(
        EnhancementPolicyRevision revision,
        int currentLevel,
        Map<EnhancementOutcome, Double> probabilities,
        OperationResourcePlan costs
) {
    private static final double DISTRIBUTION_EPSILON = 1.0e-12;

    public EnhancementPolicy {
        Objects.requireNonNull(revision, "revision");
        if (currentLevel < 0 || currentLevel > 29) {
            throw new IllegalArgumentException("fixture policy currentLevel must be 0..29");
        }
        EnumMap<EnhancementOutcome, Double> copy = new EnumMap<>(EnhancementOutcome.class);
        if (probabilities != null) copy.putAll(probabilities);
        double total = 0;
        for (EnhancementOutcome outcome : new EnhancementOutcome[]{
                EnhancementOutcome.SUCCESS, EnhancementOutcome.NO_CHANGE,
                EnhancementOutcome.DOWNGRADE, EnhancementOutcome.BROKEN}) {
            Double probability = copy.get(outcome);
            if (probability == null || !Double.isFinite(probability)
                    || probability < 0 || probability > 1) {
                throw new IllegalArgumentException("invalid probability for " + outcome);
            }
            total += probability;
        }
        if (copy.containsKey(EnhancementOutcome.REJECTED)
                || Math.abs(total - 1.0) > DISTRIBUTION_EPSILON) {
            throw new IllegalArgumentException("probability distribution must total 1 without REJECTED");
        }
        probabilities = Map.copyOf(copy);
        costs = Objects.requireNonNull(costs, "costs");
    }
}
