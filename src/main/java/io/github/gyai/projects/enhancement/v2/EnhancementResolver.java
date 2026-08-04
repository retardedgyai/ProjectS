package io.github.gyai.projects.enhancement.v2;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.operation.EquipmentItems;
import io.github.gyai.projects.equipment.operation.EquipmentMutationProposal;
import io.github.gyai.projects.transaction.TransactionRequest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class EnhancementResolver {
    @FunctionalInterface
    public interface ProbabilitySource {
        double nextUnit();
    }

    public EnhancementProposal resolve(
            EnhancementAttempt attempt,
            EnhancementPolicy policy,
            ProbabilitySource random
    ) {
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(random, "random");
        EquipmentItemV1 source = attempt.source();
        if (source.enhancementLevel() >= 30) return EnhancementProposal.rejected("maximum-level");
        if (source.broken()) return EnhancementProposal.rejected("source-broken");
        if (source.enhancementLevel() != policy.currentLevel()) {
            return EnhancementProposal.rejected("policy-level-mismatch");
        }
        double roll = random.nextUnit();
        if (!Double.isFinite(roll) || roll < 0 || roll >= 1) {
            throw new IllegalArgumentException("RNG value must be finite in [0,1)");
        }
        EnhancementOutcome outcome = select(policy, roll);
        int nextLevel = switch (outcome) {
            case SUCCESS -> source.enhancementLevel() + 1;
            case DOWNGRADE -> Math.max(0, source.enhancementLevel() - 1);
            default -> source.enhancementLevel();
        };
        boolean broken = outcome == EnhancementOutcome.BROKEN || source.broken();
        EquipmentItemV1 replacement = EquipmentItems.replaceMutableState(
                source, source.tier(), source.itemLevel(), source.quality(),
                source.modSlots(), nextLevel, broken, source.binding());
        var sourceId = source.instanceId().orElseThrow(() ->
                new IllegalArgumentException("enhancement source requires instance identity"));
        EquipmentMutationProposal mutation = new EquipmentMutationProposal(
                attempt.requestId(), attempt.playerId(), "projects:enhancement-v2",
                policy.revision().policyId(), attempt.canonicalFamilyId(),
                attempt.expectedRevision(), replacement, attempt.extensions(),
                policy.costs(), List.of(new TransactionRequest.InputRevision(
                        EquipmentMutationProposal.inputId(sourceId), attempt.expectedRevision())));
        return new EnhancementProposal(outcome, Optional.of(mutation), "");
    }

    private EnhancementOutcome select(EnhancementPolicy policy, double roll) {
        double boundary = 0;
        for (EnhancementOutcome outcome : new EnhancementOutcome[]{
                EnhancementOutcome.SUCCESS, EnhancementOutcome.NO_CHANGE,
                EnhancementOutcome.DOWNGRADE, EnhancementOutcome.BROKEN}) {
            boundary += policy.probabilities().get(outcome);
            if (roll < boundary) return outcome;
        }
        return EnhancementOutcome.BROKEN;
    }
}
