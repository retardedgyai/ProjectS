package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.beta.activation.BetaActivationAudience;
import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.beta.activation.BetaActivationTargetScope;
import io.github.gyai.projects.beta.activation.BetaMutationPolicy;
import io.github.gyai.projects.enhancement.v2.EnhancementOutcome;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentTier;
import io.github.gyai.projects.transaction.TransactionAuditResult;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Track3EconomyOperationTest {
    private Track3EconomyOperationTest() {
    }

    public static void main(String[] args) {
        resourceRefineCraftAndPromotionUseTransactions();
        oneShotEnhancementNeverRerolls();
        breakAndRepairPreserveTargetAndConsumeDonor();
        fullInventoryAndEveryFailureBoundaryAreSafe();
        accessGatesAndLogoutFailClosed();
    }

    private static void resourceRefineCraftAndPromotionUseTransactions() {
        UUID player = uuid(1);
        try (var fixture = Track3TestFixtures.fixture(8)) {
            StagingOperationAccess access = Track3TestFixtures.access(player);
            assert committed(fixture.service().deliver(access, uuid(10),
                    StagingEconomyCatalog.IRON_ORE, 4));
            assert committed(action(fixture, uuid(11), access,
                    StagingEconomyOperationPort.OperationKind.REFINE));
            var afterRefine = fixture.inventory().snapshot(player);
            assert afterRefine.resources().get(StagingEconomyCatalog.IRON_ORE) == 2;
            assert afterRefine.resources().get(StagingEconomyCatalog.IRON_INGOT) == 1;

            assert committed(fixture.service().deliver(access, uuid(12),
                    StagingEconomyCatalog.IRON_INGOT, 2));
            var craft = action(fixture, uuid(13), access,
                    StagingEconomyOperationPort.OperationKind.CRAFT);
            assert committed(craft) : craft;
            EquipmentItemV1 t1 = craft.equipment().orElseThrow();
            UUID originalId = t1.instanceId().orElseThrow();
            assert t1.tier() == EquipmentTier.T1;
            assert fixture.inventory().snapshot(player).resources()
                    .getOrDefault(StagingEconomyCatalog.IRON_INGOT, 0L) == 0;

            assert committed(fixture.service().deliver(access, uuid(14),
                    StagingEconomyCatalog.IRON_INGOT, 2));
            var promoted = action(fixture, uuid(15), access,
                    StagingEconomyOperationPort.OperationKind.PROMOTE);
            assert committed(promoted) : promoted;
            assert promoted.equipment().orElseThrow().tier() == EquipmentTier.T2;
            assert promoted.equipment().orElseThrow().instanceId().orElseThrow()
                    .equals(originalId) : "promotion changed the source identity";
            assert fixture.inventory().snapshot(player).equipment().size() == 1;
            var conflict = fixture.service().deliver(access, uuid(15),
                    StagingEconomyCatalog.TEST_TOKEN, 1);
            assert conflict.status() == StagingEconomyOperationPort.Status.REJECTED;
            assert conflict.detail().contains("request-id-reused");
        }
    }

    private static void oneShotEnhancementNeverRerolls() {
        UUID player = uuid(2);
        try (var fixture = Track3TestFixtures.fixture(8)) {
            StagingOperationAccess access = Track3TestFixtures.access(player);
            seedBlade(fixture, player, blade(EquipmentTier.T1, 3, false, uuid(20)));
            fixture.service().selectEnhancementOutcome(access, EnhancementOutcome.SUCCESS);
            UUID requestId = uuid(21);
            var success = action(fixture, requestId, access,
                    StagingEconomyOperationPort.OperationKind.ENHANCE);
            assert committed(success);
            assert success.equipment().orElseThrow().enhancementLevel() == 4;
            assert fixture.outcomes().peek(player) == EnhancementOutcome.NO_CHANGE;

            fixture.service().selectEnhancementOutcome(access, EnhancementOutcome.BROKEN);
            var replay = action(fixture, requestId, access,
                    StagingEconomyOperationPort.OperationKind.ENHANCE);
            assert replay.status() == StagingEconomyOperationPort.Status.REPLAYED;
            assert fixture.outcomes().peek(player) == EnhancementOutcome.BROKEN
                    : "retry consumed or rerolled a later override";
            assert fixture.inventory().snapshot(player).equipment().getFirst()
                    .enhancementLevel() == 4;

            fixture.outcomes().logout(player);
            var noChange = action(fixture, uuid(22), access,
                    StagingEconomyOperationPort.OperationKind.ENHANCE);
            assert committed(noChange);
            assert noChange.equipment().orElseThrow().enhancementLevel() == 4;
        }

        try (var fixture = Track3TestFixtures.fixture(8)) {
            StagingOperationAccess access = Track3TestFixtures.access(player);
            seedBlade(fixture, player, blade(EquipmentTier.T1, 3, false, uuid(23)));
            fixture.service().selectEnhancementOutcome(access, EnhancementOutcome.SUCCESS);
            var uncertain = fixture.service().execute(
                    StagingEconomyOperationPort.OperationRequest.action(
                            uuid(24), access, StagingEconomyOperationPort.OperationKind.ENHANCE),
                    StagingFailurePoint.COMMIT);
            assert uncertain.status() == StagingEconomyOperationPort.Status.COMMIT_UNCERTAIN;
            assert fixture.outcomes().peek(player) == EnhancementOutcome.NO_CHANGE
                    : "reserved enhancement did not consume its one-shot outcome";
            var replay = action(fixture, uuid(24), access,
                    StagingEconomyOperationPort.OperationKind.ENHANCE);
            assert replay.status() == StagingEconomyOperationPort.Status.REPLAYED;
            assert replay.transaction().orElseThrow().outcome()
                    == TransactionAuditResult.Outcome.COMMIT_UNCERTAIN;
            fixture.service().logout(player);
            assert fixture.inventory().snapshot(player).equipment().getFirst()
                    .enhancementLevel() == 3 : "logout did not isolate uncertain custody";
        }
    }

    private static void breakAndRepairPreserveTargetAndConsumeDonor() {
        UUID player = uuid(3);
        try (var fixture = Track3TestFixtures.fixture(8)) {
            StagingOperationAccess access = Track3TestFixtures.access(player);
            EquipmentItemV1 target = blade(EquipmentTier.T1, 7, true, uuid(30));
            EquipmentItemV1 donor = blade(EquipmentTier.T1, 0, false, uuid(31));
            seedBlade(fixture, player, target);
            seedBlade(fixture, player, donor);
            var repaired = action(fixture, uuid(32), access,
                    StagingEconomyOperationPort.OperationKind.REPAIR);
            assert committed(repaired) : repaired;
            EquipmentItemV1 result = repaired.equipment().orElseThrow();
            assert !result.broken();
            assert result.instanceId().equals(target.instanceId());
            assert result.enhancementLevel() == target.enhancementLevel();
            assert result.quality() == target.quality();
            assert result.modSlots().equals(target.modSlots());
            assert result.baseStatRolls().equals(target.baseStatRolls());
            assert fixture.inventory().snapshot(player).equipment().size() == 1
                    : "repair donor was not consumed exactly once";

            var broken = action(fixture, uuid(33), access,
                    StagingEconomyOperationPort.OperationKind.BREAK);
            assert committed(broken);
            assert broken.equipment().orElseThrow().broken();
            assert broken.equipment().orElseThrow().instanceId().equals(target.instanceId());
        }
    }

    private static void fullInventoryAndEveryFailureBoundaryAreSafe() {
        UUID player = uuid(4);
        try (var fixture = Track3TestFixtures.fixture(1)) {
            StagingOperationAccess access = Track3TestFixtures.access(player);
            seedBlade(fixture, player, blade(EquipmentTier.T1, 0, false, uuid(40)));
            fixture.inventory().seedResource(player, StagingEconomyCatalog.IRON_INGOT, 3);
            var before = fixture.inventory().snapshot(player);
            var full = action(fixture, uuid(41), access,
                    StagingEconomyOperationPort.OperationKind.CRAFT);
            assert full.status() == StagingEconomyOperationPort.Status.REJECTED;
            assert fixture.inventory().snapshot(player).resources().equals(before.resources());
            assert fixture.inventory().snapshot(player).equipment().equals(before.equipment());
        }

        for (StagingFailurePoint point : StagingFailurePoint.values()) {
            if (point == StagingFailurePoint.NONE) continue;
            try (var fixture = Track3TestFixtures.fixture(4)) {
                StagingOperationAccess access = Track3TestFixtures.access(player);
                fixture.inventory().openSession(player);
                fixture.inventory().seedResource(player, StagingEconomyCatalog.IRON_ORE, 2);
                var result = fixture.service().execute(
                        StagingEconomyOperationPort.OperationRequest.action(
                                uuid(100 + point.ordinal()), access,
                                StagingEconomyOperationPort.OperationKind.REFINE), point);
                if (point == StagingFailurePoint.VALIDATE
                        || point == StagingFailurePoint.RESERVE) {
                    assert result.status() == StagingEconomyOperationPort.Status.REJECTED : point;
                } else if (point == StagingFailurePoint.COMMIT) {
                    assert result.status()
                            == StagingEconomyOperationPort.Status.COMMIT_UNCERTAIN : point;
                    assert fixture.inventory().snapshot(player).activeReservations() == 1;
                    fixture.service().logout(player);
                } else {
                    assert result.status() == StagingEconomyOperationPort.Status.ROLLED_BACK : point;
                }
                assert fixture.inventory().snapshot(player).resources()
                        .get(StagingEconomyCatalog.IRON_ORE) == 2 : point;
                assert fixture.inventory().snapshot(player).resources()
                        .getOrDefault(StagingEconomyCatalog.IRON_INGOT, 0L) == 0 : point;
            }
        }
    }

    private static void accessGatesAndLogoutFailClosed() {
        UUID player = uuid(5);
        try (var fixture = Track3TestFixtures.fixture(4)) {
            var defaults = new BetaActivationPolicy(
                    BetaActivationAudience.OFF,
                    BetaActivationTargetScope.TRAINING_DUMMY_ONLY,
                    BetaMutationPolicy.READ_ONLY,
                    Set.of(), Set.of(), true, false);
            var denied = new StagingOperationAccess(player, "world", false, defaults);
            assert fixture.service().deliver(denied, uuid(50),
                    StagingEconomyCatalog.IRON_ORE, 1).status()
                    == StagingEconomyOperationPort.Status.REJECTED;
            assert fixture.inventory().snapshot(player).resources().isEmpty();

            StagingOperationAccess access = Track3TestFixtures.access(player);
            fixture.service().selectEnhancementOutcome(access, EnhancementOutcome.BROKEN);
            fixture.service().logout(player);
            assert fixture.outcomes().peek(player) == EnhancementOutcome.NO_CHANGE;
            fixture.service().close();
            fixture.service().close();
        }
    }

    private static StagingEconomyOperationPort.OperationResult action(
            Track3TestFixtures.Fixture fixture,
            UUID requestId,
            StagingOperationAccess access,
            StagingEconomyOperationPort.OperationKind kind
    ) {
        return fixture.service().execute(
                StagingEconomyOperationPort.OperationRequest.action(requestId, access, kind));
    }

    private static boolean committed(StagingEconomyOperationPort.OperationResult result) {
        return result.status() == StagingEconomyOperationPort.Status.COMMITTED;
    }

    private static void seedBlade(
            Track3TestFixtures.Fixture fixture,
            UUID player,
            EquipmentItemV1 item
    ) {
        fixture.inventory().openSession(player);
        fixture.inventory().seedEquipment(player, item);
    }

    private static EquipmentItemV1 blade(
            EquipmentTier tier,
            int enhancement,
            boolean broken,
            UUID instanceId
    ) {
        EquipmentItemV1 base = StagingEconomyCatalog.previewBlade(tier);
        return new EquipmentItemV1(
                base.schemaVersion(), base.itemId(), base.category(), base.slot(),
                base.tier(), base.itemLevel(), base.rarity(), base.quality(),
                base.baseStatRolls(), base.modSlots(), base.crafter(), enhancement,
                broken, base.binding(), base.tradePolicy(), Optional.of(instanceId));
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }
}
