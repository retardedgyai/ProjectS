package io.github.gyai.projects.enhancement.v2;

import io.github.gyai.projects.equipment.operation.EquipmentMutationProposal;
import io.github.gyai.projects.equipment.operation.EquipmentOperationPlan;
import io.github.gyai.projects.transaction.TransactionRequest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class EnhancementTransactionAdapter {
    private final EnhancementResolver resolver = new EnhancementResolver();

    public Preparation prepare(
            EnhancementAttempt attempt,
            EnhancementPolicy policy,
            EnhancementResolver.ProbabilitySource random
    ) {
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(random, "random");
        if (attempt.source().enhancementLevel() >= 30) return Preparation.rejected("maximum-level");
        if (attempt.source().broken()) return Preparation.rejected("source-broken");
        if (attempt.source().enhancementLevel() != policy.currentLevel()) {
            return Preparation.rejected("policy-level-mismatch");
        }
        var sourceId = attempt.source().instanceId();
        if (sourceId.isEmpty()) return Preparation.rejected("source-instance-id-required");
        TransactionRequest request = new TransactionRequest(
                attempt.requestId(), attempt.playerId(), "projects:enhancement-v2",
                policy.revision().policyId(), attempt.expectedRevision(), 1,
                List.of(new TransactionRequest.InputRevision(
                        EquipmentMutationProposal.inputId(sourceId.orElseThrow()),
                        attempt.expectedRevision())));
        EquipmentOperationPlan plan = new EquipmentOperationPlan(
                request, policy.costs(), () -> resolver.resolve(attempt, policy, random)
                .mutation().orElseThrow(() -> new IllegalStateException("enhancement-rejected-after-reserve")));
        return new Preparation(Status.READY, Optional.of(plan), "");
    }

    public record Preparation(Status status, Optional<EquipmentOperationPlan> plan, String reason) {
        public Preparation {
            plan = plan == null ? Optional.empty() : plan;
            reason = reason == null ? "" : reason;
            if ((status == Status.READY) != plan.isPresent()) {
                throw new IllegalArgumentException("ready preparation requires plan");
            }
        }

        public static Preparation rejected(String reason) {
            return new Preparation(Status.REJECTED, Optional.empty(), reason);
        }
    }

    public enum Status { READY, REJECTED }
}
