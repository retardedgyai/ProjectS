package io.github.gyai.projects.equipment.operation;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentTier;
import io.github.gyai.projects.transaction.TransactionRequest;

import java.util.List;
import java.util.Optional;

public final class TierPromotionService {
    public TierPromotionProposal propose(
            TierPromotionRequest request,
            TierPromotionCarryPolicy policy
    ) {
        if (!policy.complete()) return TierPromotionProposal.rejected("incomplete-carry-policy");
        if (!request.sourceFamilyId().equals(request.destinationFamilyId())) {
            return TierPromotionProposal.rejected("family-mismatch");
        }
        EquipmentTier expected = switch (request.source().tier()) {
            case T1 -> EquipmentTier.T2;
            case T2 -> EquipmentTier.T3;
            case T3 -> null;
        };
        if (expected == null) return TierPromotionProposal.rejected("tier-above-t3");
        EquipmentItemV1 source = request.source();
        EquipmentItemV1 destination = request.destinationTemplate();
        if (destination.tier() != expected
                || !destination.tier().contains(destination.itemLevel())) {
            return TierPromotionProposal.rejected("invalid-destination-tier-item-level");
        }
        if (source.category() != destination.category() || source.slot() != destination.slot()) {
            return TierPromotionProposal.rejected("equipment-type-mismatch");
        }
        if (source.broken()) return TierPromotionProposal.rejected("broken-source");
        var sourceId = source.instanceId();
        if (sourceId.isEmpty()) return TierPromotionProposal.rejected("source-instance-id-required");

        try {
            EquipmentItemV1 result = new EquipmentItemV1(
                    destination.schemaVersion(), destination.itemId(), destination.category(),
                    destination.slot(), destination.tier(), destination.itemLevel(),
                    destination.rarity(), choose(policy, TierPromotionCarryPolicy.CarryField.QUALITY,
                            source.quality(), destination.quality()),
                    destination.baseStatRolls(),
                    choose(policy, TierPromotionCarryPolicy.CarryField.MODS,
                            source.modSlots(), destination.modSlots()),
                    destination.crafter(),
                    choose(policy, TierPromotionCarryPolicy.CarryField.ENHANCEMENT,
                            source.enhancementLevel(), destination.enhancementLevel()),
                    false,
                    choose(policy, TierPromotionCarryPolicy.CarryField.BINDING,
                            source.binding(), destination.binding()),
                    destination.tradePolicy(), source.instanceId());
            EquipmentMutationProposal mutation = new EquipmentMutationProposal(
                    request.requestId(), request.playerId(), "projects:tier-promotion",
                    policy.policyId(), request.sourceFamilyId(), request.expectedRevision(),
                    result, request.extensions(), policy.resources(),
                    List.of(new TransactionRequest.InputRevision(
                            EquipmentMutationProposal.inputId(sourceId.orElseThrow()),
                            request.expectedRevision())));
            return new TierPromotionProposal(
                    TierPromotionProposal.Status.ACCEPTED, Optional.of(mutation), "");
        } catch (IllegalArgumentException incompatibleCarry) {
            return TierPromotionProposal.rejected(
                    "carry-incompatible-with-destination:" + incompatibleCarry.getMessage());
        }
    }

    private <T> T choose(
            TierPromotionCarryPolicy policy,
            TierPromotionCarryPolicy.CarryField field,
            T source,
            T destination
    ) {
        return policy.decisions().get(field)
                == TierPromotionCarryPolicy.FieldDecision.CARRY_SOURCE
                ? source : destination;
    }
}
