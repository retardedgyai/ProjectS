package io.github.gyai.projects.enhancement.v2;

import io.github.gyai.projects.equipment.*;
import io.github.gyai.projects.equipment.operation.*;
import io.github.gyai.projects.feature.FeatureFlagService;
import io.github.gyai.projects.feature.FeatureKey;
import io.github.gyai.projects.mod.ModEntry;
import io.github.gyai.projects.mod.ModRank;
import io.github.gyai.projects.mod.ModSource;
import io.github.gyai.projects.repair.*;
import io.github.gyai.projects.schema.SchemaVersions;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.SplittableRandom;

public final class TrackEFoundationTest {
    private static final UUID PLAYER = uuid(1);
    private TrackEFoundationTest() { }

    public static void main(String[] args) {
        policyValidationAndDeterministicOutcome();
        maximumLevelIsRejectedWithoutRng();
        tierPromotionRequiresSequentialTierFamilyAndCompletePolicy();
        tierPromotionNeverSilentlyRetiersMods();
        repairValidatesDonorAndPreservesEveryTargetField();
        immutableBoundariesAndFlagsRemainDisabled();
        purePublicApiContainsNoBukkitTypes();
    }

    private static void policyValidationAndDeterministicOutcome() {
        Map<EnhancementOutcome, Double> distribution = distribution(.5, .2, .2, .1);
        EnhancementPolicy policy = new EnhancementPolicy(
                new EnhancementPolicyRevision("projects:fixture-enhancement", 7), 10,
                distribution,
                new OperationResourcePlan(
                        List.of(new OperationResourcePlan.MaterialCost(
                                "projects:enhancement-stone", 2)), 50));
        EnhancementAttempt attempt = new EnhancementAttempt(
                uuid(2), PLAYER, "projects:warrior-sword", equipment(
                "warrior_sword_t1", EquipmentTier.T1, 10, 10, false, uuid(10), false),
                4, extensions());
        EnhancementResolver resolver = new EnhancementResolver();
        EnhancementProposal first = resolver.resolve(
                attempt, policy, new SplittableRandom(91)::nextDouble);
        EnhancementProposal second = resolver.resolve(
                attempt, policy, new SplittableRandom(91)::nextDouble);
        assert first.equals(second);
        assert first.outcome() != EnhancementOutcome.REJECTED;
        assert attempt.source().enhancementLevel() == 10 : "source mutated";
        expectUnsupported(() -> policy.probabilities().put(EnhancementOutcome.SUCCESS, 1.0));
        expectIllegal(() -> new EnhancementPolicy(
                policy.revision(), 10, distribution(.5, .2, .2, .2), policy.costs()));
        expectIllegal(() -> new EnhancementPolicy(
                policy.revision(), 10,
                distribution(Double.NaN, 0, 0, 1), policy.costs()));
        expectIllegal(() -> new OperationResourcePlan(List.of(), -1));
        expectIllegal(() -> resolver.resolve(attempt, policy, () -> Double.POSITIVE_INFINITY));
    }

    private static void maximumLevelIsRejectedWithoutRng() {
        EnhancementAttempt maximum = new EnhancementAttempt(
                uuid(3), PLAYER, "projects:warrior-sword",
                equipment("warrior_sword_t3", EquipmentTier.T3, 40, 30,
                        false, uuid(11), false), 8, extensions());
        EnhancementPolicy fixture = new EnhancementPolicy(
                new EnhancementPolicyRevision("projects:fixture-max", 1), 29,
                distribution(1, 0, 0, 0), OperationResourcePlan.none());
        AtomicInteger rolls = new AtomicInteger();
        var preparation = new EnhancementTransactionAdapter().prepare(
                maximum, fixture, () -> { rolls.incrementAndGet(); return 0; });
        assert preparation.status() == EnhancementTransactionAdapter.Status.REJECTED;
        assert preparation.reason().equals("maximum-level");
        assert rolls.get() == 0;
    }

    private static void tierPromotionRequiresSequentialTierFamilyAndCompletePolicy() {
        TierPromotionService service = new TierPromotionService();
        EquipmentItemV1 t1 = equipment(
                "warrior_sword_t1", EquipmentTier.T1, 15, 8, false, uuid(20), true);
        EquipmentItemV1 t2Template = equipment(
                "warrior_sword_t2", EquipmentTier.T2, 16, 0, false, uuid(999), true);
        TierPromotionCarryPolicy policy = completePromotionPolicy(
                TierPromotionCarryPolicy.FieldDecision.USE_DESTINATION_VALUE);
        TierPromotionRequest request = promotionRequest(t1, t2Template,
                "projects:warrior-sword", "projects:warrior-sword");
        TierPromotionProposal t1ToT2 = service.propose(request, policy);
        assert t1ToT2.status() == TierPromotionProposal.Status.ACCEPTED;
        EquipmentItemV1 promoted = t1ToT2.mutation().orElseThrow().proposedItem();
        assert promoted.tier() == EquipmentTier.T2 && promoted.itemLevel() == 16;
        assert promoted.instanceId().equals(t1.instanceId());
        assert t1.tier() == EquipmentTier.T1 && t1.enhancementLevel() == 8;
        assert t1ToT2.mutation().orElseThrow().resources().materials().getFirst().quantity() == 3;

        EquipmentItemV1 t3Template = equipment(
                "warrior_sword_t3", EquipmentTier.T3, 31, 0, false, uuid(998), true);
        assert service.propose(promotionRequest(
                promoted, t3Template, "projects:warrior-sword", "projects:warrior-sword"),
                policy).status() == TierPromotionProposal.Status.ACCEPTED;
        assert service.propose(promotionRequest(
                t3Template, t3Template, "projects:warrior-sword", "projects:warrior-sword"),
                policy).reason().equals("tier-above-t3");
        assert service.propose(promotionRequest(
                t1, t2Template, "projects:warrior-sword", "projects:mage-sword"),
                policy).reason().equals("family-mismatch");

        TierPromotionCarryPolicy incomplete = new TierPromotionCarryPolicy(
                "projects:incomplete-promotion", 1,
                Map.of(TierPromotionCarryPolicy.CarryField.QUALITY,
                        TierPromotionCarryPolicy.FieldDecision.CARRY_SOURCE),
                OperationResourcePlan.none());
        assert service.propose(request, incomplete).reason().equals("incomplete-carry-policy");
    }

    private static void tierPromotionNeverSilentlyRetiersMods() {
        EquipmentItemV1 t1 = equipment(
                "warrior_sword_t1", EquipmentTier.T1, 15, 2, false, uuid(30), true);
        EquipmentItemV1 t2 = equipment(
                "warrior_sword_t2", EquipmentTier.T2, 16, 0, false, uuid(31), true);
        EnumMap<TierPromotionCarryPolicy.CarryField,
                TierPromotionCarryPolicy.FieldDecision> decisions = new EnumMap<>(
                TierPromotionCarryPolicy.CarryField.class);
        for (var field : TierPromotionCarryPolicy.CarryField.values()) {
            decisions.put(field, TierPromotionCarryPolicy.FieldDecision.USE_DESTINATION_VALUE);
        }
        decisions.put(TierPromotionCarryPolicy.CarryField.MODS,
                TierPromotionCarryPolicy.FieldDecision.CARRY_SOURCE);
        TierPromotionProposal result = new TierPromotionService().propose(
                promotionRequest(t1, t2, "projects:warrior-sword", "projects:warrior-sword"),
                new TierPromotionCarryPolicy(
                        "projects:carry-incompatible-mod", 2, decisions,
                        OperationResourcePlan.none()));
        assert result.status() == TierPromotionProposal.Status.REJECTED;
        assert result.reason().startsWith("carry-incompatible-with-destination:");
    }

    private static void repairValidatesDonorAndPreservesEveryTargetField() {
        EquipmentItemV1 target = equipment(
                "warrior_sword_t2", EquipmentTier.T2, 22, 19, true, uuid(40), true);
        EquipmentItemV1 donor = equipment(
                "different_visual_item", EquipmentTier.T2, 20, 0, false, uuid(41), false);
        RepairRequest request = new RepairRequest(
                uuid(42), PLAYER, "projects:warrior-sword", "projects:warrior-sword",
                target, 10, donor, 11, extensions());
        RepairPolicy policy = new RepairPolicy(
                "projects:fixture-repair", 4,
                new OperationResourcePlan(List.of(new OperationResourcePlan.MaterialCost(
                        "projects:repair-crystal", 1)), 25));
        RepairProposal proposal = new RepairService().propose(request, policy);
        assert proposal.status() == RepairProposal.Status.ACCEPTED;
        EquipmentItemV1 repaired = proposal.mutation().orElseThrow().proposedItem();
        assert repaired.equals(EquipmentItems.repair(target));
        assert !repaired.broken();
        assert proposal.mutation().orElseThrow().extensions().equals(extensions());
        assert proposal.donorConsumptionInputId().orElseThrow().contains(
                donor.instanceId().orElseThrow().toString().replace("-", ""));
        assert proposal.mutation().orElseThrow().inputs().size() == 2;
        assert target.broken() && donor.enhancementLevel() == 0 : "source or donor mutated";

        RepairService service = new RepairService();
        assert service.propose(new RepairRequest(
                uuid(50), PLAYER, "projects:warrior-sword", "projects:warrior-sword",
                equipment("target_unbroken", EquipmentTier.T2, 22, 19,
                        false, uuid(51), true), 1, donor, 1, extensions()), policy)
                .reason().equals("target-not-broken");
        assert service.propose(new RepairRequest(
                uuid(43), PLAYER, "projects:warrior-sword", "projects:mage-sword",
                target, 1, donor, 1, extensions()), policy).reason().equals("family-mismatch");
        assert service.propose(new RepairRequest(
                uuid(44), PLAYER, "projects:warrior-sword", "projects:warrior-sword",
                target, 1, equipment("donor_t1", EquipmentTier.T1, 10, 0,
                        false, uuid(45), false), 1, extensions()), policy)
                .reason().equals("tier-mismatch");
        assert service.propose(new RepairRequest(
                uuid(46), PLAYER, "projects:warrior-sword", "projects:warrior-sword",
                target, 1, equipment("donor_plus_one", EquipmentTier.T2, 20, 1,
                        false, uuid(47), false), 1, extensions()), policy)
                .reason().equals("donor-enhanced");
        assert service.propose(new RepairRequest(
                uuid(48), PLAYER, "projects:warrior-sword", "projects:warrior-sword",
                target, 1, equipment("donor_broken", EquipmentTier.T2, 20, 0,
                        true, uuid(49), false), 1, extensions()), policy)
                .reason().equals("donor-broken");
    }

    private static void immutableBoundariesAndFlagsRemainDisabled() {
        assert !new FeatureFlagService().isEnabled(FeatureKey.TIER_PROMOTION);
        assert !new FeatureFlagService().isEnabled(FeatureKey.ENHANCEMENT_V2);
        assert !new FeatureFlagService().isEnabled(FeatureKey.REPAIR_V2);
        EquipmentExtensionSnapshot snapshot = extensions();
        expectUnsupported(() -> snapshot.values().put("projects:x", "x"));
        OperationResourcePlan resources = new OperationResourcePlan(
                List.of(new OperationResourcePlan.MaterialCost("projects:test", 1)), 0);
        expectUnsupported(() -> resources.materials().clear());
    }

    private static void purePublicApiContainsNoBukkitTypes() {
        List<Class<?>> publicTypes = List.of(
                EnhancementPolicy.class, EnhancementAttempt.class, EnhancementProposal.class,
                EnhancementResolver.class, EnhancementTransactionAdapter.class,
                TierPromotionRequest.class, TierPromotionCarryPolicy.class,
                TierPromotionProposal.class, TierPromotionService.class,
                RepairRequest.class, RepairPolicy.class, RepairProposal.class,
                RepairService.class, EquipmentOperationPlan.class,
                EquipmentOperationParticipant.class, EquipmentResourcePort.class,
                EquipmentOperationJournal.class, EquipmentMutationProposal.class);
        for (Class<?> type : publicTypes) {
            for (Method method : type.getMethods()) {
                assertNoBukkit(type, method.getReturnType());
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertNoBukkit(type, parameter);
                }
            }
        }
    }

    private static void assertNoBukkit(Class<?> owner, Class<?> type) {
        assert !type.getName().startsWith("org.bukkit.")
                : owner.getName() + " leaks " + type.getName();
    }

    private static TierPromotionCarryPolicy completePromotionPolicy(
            TierPromotionCarryPolicy.FieldDecision decision) {
        EnumMap<TierPromotionCarryPolicy.CarryField,
                TierPromotionCarryPolicy.FieldDecision> decisions = new EnumMap<>(
                TierPromotionCarryPolicy.CarryField.class);
        for (var field : TierPromotionCarryPolicy.CarryField.values()) {
            decisions.put(field, decision);
        }
        return new TierPromotionCarryPolicy(
                "projects:fixture-tier-promotion", 1, decisions,
                new OperationResourcePlan(List.of(new OperationResourcePlan.MaterialCost(
                        "projects:tier-material", 3)), 100));
    }

    private static TierPromotionRequest promotionRequest(
            EquipmentItemV1 source, EquipmentItemV1 destination,
            String sourceFamily, String destinationFamily) {
        return new TierPromotionRequest(
                uuid((int) source.instanceId().orElseThrow().getLeastSignificantBits() + 100),
                PLAYER, sourceFamily, destinationFamily, source, destination, 5, extensions());
    }

    private static EquipmentItemV1 equipment(
            String itemId, EquipmentTier tier, int itemLevel, int enhancement,
            boolean broken, UUID instanceId, boolean withMod) {
        EquipmentModSlot slot = withMod
                ? new EquipmentModSlot(0, Optional.of(new ModEntry(
                SchemaVersions.MOD_DEFINITION, "projects:fixture-mod",
                switch (tier) {
                    case T1 -> ModRank.RANK_1;
                    case T2 -> ModRank.RANK_2;
                    case T3 -> ModRank.RANK_3;
                }, 2.5, 1,
                new ModSource("projects:fixture-pack", "projects:track-e-test"), 0)))
                : EquipmentModSlot.empty(0);
        return new EquipmentItemV1(
                SchemaVersions.EQUIPMENT_ITEM, itemId, EquipmentCategory.WEAPON,
                EquipmentSlot.WEAPON, tier, itemLevel, EquipmentRarity.COMMON,
                EquipmentQuality.UNSPECIFIED,
                List.of(new BaseStatRoll("projects:physical-attack", 12.5)),
                List.of(slot), Optional.of(new CrafterIdentity(uuid(800), "Crafter-A")),
                enhancement, broken, BindingPolicy.PLAYER_BOUND,
                new TradePolicy(false, false, true), Optional.of(instanceId));
    }

    private static EquipmentExtensionSnapshot extensions() {
        return new EquipmentExtensionSnapshot(Map.of(
                "projects:display-name", "Storm Edge",
                "projects:engraving", "Never inferred from itemId"));
    }

    private static Map<EnhancementOutcome, Double> distribution(
            double success, double noChange, double downgrade, double broken) {
        return Map.of(
                EnhancementOutcome.SUCCESS, success,
                EnhancementOutcome.NO_CHANGE, noChange,
                EnhancementOutcome.DOWNGRADE, downgrade,
                EnhancementOutcome.BROKEN, broken);
    }

    private static UUID uuid(int seed) { return new UUID(0, seed); }

    private static void expectIllegal(Runnable operation) {
        try { operation.run(); throw new AssertionError("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }

    private static void expectUnsupported(Runnable operation) {
        try { operation.run(); throw new AssertionError("Expected UnsupportedOperationException"); }
        catch (UnsupportedOperationException expected) { }
    }
}
