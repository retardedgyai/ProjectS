package io.github.gyai.projects.repair;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.operation.EquipmentItems;
import io.github.gyai.projects.equipment.operation.EquipmentMutationProposal;
import io.github.gyai.projects.transaction.TransactionRequest;

import java.util.List;
import java.util.Optional;

public final class RepairService {
    public RepairProposal propose(RepairRequest request, RepairPolicy policy) {
        EquipmentItemV1 target = request.target();
        EquipmentItemV1 donor = request.donor();
        if (!target.broken()) return RepairProposal.rejected("target-not-broken");
        if (!request.targetFamilyId().equals(request.donorFamilyId())) {
            return RepairProposal.rejected("family-mismatch");
        }
        if (target.tier() != donor.tier()) return RepairProposal.rejected("tier-mismatch");
        if (target.category() != donor.category() || target.slot() != donor.slot()) {
            return RepairProposal.rejected("equipment-type-mismatch");
        }
        if (donor.enhancementLevel() != 0) return RepairProposal.rejected("donor-enhanced");
        if (donor.broken()) return RepairProposal.rejected("donor-broken");
        var targetId = target.instanceId();
        var donorId = donor.instanceId();
        if (targetId.isEmpty() || donorId.isEmpty() || targetId.equals(donorId)) {
            return RepairProposal.rejected("distinct-instance-identities-required");
        }
        EquipmentItemV1 repaired = EquipmentItems.repair(target);
        String targetInput = EquipmentMutationProposal.inputId(targetId.orElseThrow());
        String donorInput = EquipmentMutationProposal.inputId(donorId.orElseThrow());
        EquipmentMutationProposal mutation = new EquipmentMutationProposal(
                request.requestId(), request.playerId(), "projects:repair-v2",
                policy.policyId(), request.targetFamilyId(), request.targetRevision(),
                repaired, request.targetExtensions(), policy.resources(), List.of(
                        new TransactionRequest.InputRevision(targetInput, request.targetRevision()),
                        new TransactionRequest.InputRevision(donorInput, request.donorRevision())));
        return new RepairProposal(
                RepairProposal.Status.ACCEPTED, Optional.of(mutation),
                Optional.of(donorInput), "");
    }
}
