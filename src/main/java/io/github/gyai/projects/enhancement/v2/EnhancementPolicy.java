package io.github.gyai.projects.enhancement.v2;

import io.github.gyai.projects.equipment.operation.OperationResourcePlan;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record EnhancementPolicy(
        EnhancementPolicyRevision revision,
        int currentLevel,
        Map<EnhancementOutcome, Double> probabilities,
        Map<EnhancementOutcome, EnhancementTransition> transitions,
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
        EnumMap<EnhancementOutcome, EnhancementTransition> transitionCopy =
                new EnumMap<>(EnhancementOutcome.class);
        if (transitions != null) transitionCopy.putAll(transitions);
        if (transitionCopy.containsKey(EnhancementOutcome.REJECTED)) {
            throw new IllegalArgumentException("REJECTED cannot define a transition");
        }
        for (EnhancementOutcome outcome : new EnhancementOutcome[]{
                EnhancementOutcome.SUCCESS, EnhancementOutcome.NO_CHANGE,
                EnhancementOutcome.DOWNGRADE, EnhancementOutcome.BROKEN}) {
            EnhancementTransition transition = transitionCopy.get(outcome);
            if (transition == null) {
                throw new IllegalArgumentException("missing transition for " + outcome);
            }
            validateTransition(currentLevel, outcome, transition);
        }
        transitions = Map.copyOf(transitionCopy);
        costs = Objects.requireNonNull(costs, "costs");
    }

    private static void validateTransition(
            int currentLevel,
            EnhancementOutcome outcome,
            EnhancementTransition transition
    ) {
        switch (outcome) {
            case SUCCESS -> {
                if (transition.broken() || transition.targetLevel() <= currentLevel) {
                    throw new IllegalArgumentException("SUCCESS must increase level without breaking");
                }
            }
            case NO_CHANGE -> {
                if (transition.broken() || transition.targetLevel() != currentLevel) {
                    throw new IllegalArgumentException("NO_CHANGE must preserve level and unbroken state");
                }
            }
            case DOWNGRADE -> {
                if (transition.broken() || transition.targetLevel() > currentLevel) {
                    throw new IllegalArgumentException("DOWNGRADE cannot increase level or break");
                }
            }
            case BROKEN -> {
                if (!transition.broken()) {
                    throw new IllegalArgumentException("BROKEN must set broken state");
                }
            }
            case REJECTED -> throw new IllegalArgumentException("REJECTED has no transition");
        }
    }
}
