package io.github.gyai.projects.beta;

import io.github.gyai.projects.ProjectSPlugin;
import io.github.gyai.projects.crafting.OutputProposal;
import io.github.gyai.projects.enhancement.v2.EnhancementAttempt;
import io.github.gyai.projects.enhancement.v2.EnhancementOutcome;
import io.github.gyai.projects.enhancement.v2.EnhancementPolicy;
import io.github.gyai.projects.enhancement.v2.EnhancementPolicyRevision;
import io.github.gyai.projects.enhancement.v2.EnhancementTransactionAdapter;
import io.github.gyai.projects.enhancement.v2.EnhancementTransition;
import io.github.gyai.projects.enhancement.v2.TrackEFoundationTest;
import io.github.gyai.projects.equipment.BaseStatRoll;
import io.github.gyai.projects.equipment.BindingPolicy;
import io.github.gyai.projects.equipment.EquipmentCategory;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentModSlot;
import io.github.gyai.projects.equipment.EquipmentQuality;
import io.github.gyai.projects.equipment.EquipmentRarity;
import io.github.gyai.projects.equipment.EquipmentSlot;
import io.github.gyai.projects.equipment.EquipmentTier;
import io.github.gyai.projects.equipment.EquipmentWriteBoundary;
import io.github.gyai.projects.equipment.TradePolicy;
import io.github.gyai.projects.equipment.operation.EquipmentExtensionSnapshot;
import io.github.gyai.projects.equipment.operation.EquipmentMutationProposal;
import io.github.gyai.projects.equipment.operation.EquipmentOperationJournal;
import io.github.gyai.projects.equipment.operation.EquipmentOperationParticipant;
import io.github.gyai.projects.equipment.operation.EquipmentOperationPlan;
import io.github.gyai.projects.equipment.operation.EquipmentResourcePort;
import io.github.gyai.projects.equipment.operation.OperationResourcePlan;
import io.github.gyai.projects.equipment.operation.TierPromotionService;
import io.github.gyai.projects.feature.FeatureFlagService;
import io.github.gyai.projects.feature.FeatureKey;
import io.github.gyai.projects.manager.EnhancementManager;
import io.github.gyai.projects.monster.editor.MobDefinition;
import io.github.gyai.projects.participation.EncounterId;
import io.github.gyai.projects.participation.ParticipationEvent;
import io.github.gyai.projects.participation.ParticipationKey;
import io.github.gyai.projects.participation.ParticipationLedger;
import io.github.gyai.projects.participation.ParticipationRecord;
import io.github.gyai.projects.party.PartyMember;
import io.github.gyai.projects.party.PartyService;
import io.github.gyai.projects.player.progress.PlayerProgressBuilder;
import io.github.gyai.projects.player.progress.PlayerProgressSnapshot;
import io.github.gyai.projects.quest.QuestProgressService;
import io.github.gyai.projects.repair.RepairService;
import io.github.gyai.projects.reward.RewardClaimKey;
import io.github.gyai.projects.reward.RewardClaimRequest;
import io.github.gyai.projects.reward.RewardClaimService;
import io.github.gyai.projects.reward.RewardDeliveryReceipt;
import io.github.gyai.projects.reward.RewardTransactionIdentity;
import io.github.gyai.projects.reward.TransactionRewardDeliveryPort;
import io.github.gyai.projects.schema.SchemaId;
import io.github.gyai.projects.schema.SchemaVersions;
import io.github.gyai.projects.transaction.InventoryCapacityProposal;
import io.github.gyai.projects.transaction.ReservationToken;
import io.github.gyai.projects.transaction.TransactionAuditResult;
import io.github.gyai.projects.transaction.TransactionEngine;
import io.github.gyai.projects.transaction.TransactionParticipant;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.transaction.TransactionStage;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Cross-track integration gate for the disabled Wave 2 foundations. */
public final class Wave2IntegratedFoundationTest {
    private static final UUID PLAYER = UUID.fromString(
            "40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private Wave2IntegratedFoundationTest() {
    }

    public static void main(String[] args) throws Exception {
        schemasAndAllWaveFlagsRemainClosed();
        componentSmokeContractsCoexist();
        enhancementAndRewardShareTrackDWithoutCrossTalk();
        aggregatesSharePlayerIdentityWithoutOwningEachOther();
        purePublicApisRemainBukkitFree();
        gameplayEntrypointRemainsDisconnected();
    }

    private static void schemasAndAllWaveFlagsRemainClosed() {
        assert SchemaVersions.PLAYER_DATA == 1;
        assert SchemaVersions.EQUIPMENT_ITEM == 1;
        assert SchemaVersions.MOD_DEFINITION == 1;
        assert SchemaVersions.RECIPE_DEFINITION == 1;
        assert MobDefinition.SCHEMA_VERSION == 1;
        assert SchemaVersions.MOB_DEFINITION == 2;
        assert SchemaVersions.supportedReadVersions(SchemaId.MOB_DEFINITION)
                .equals(Set.of(1, 2));
        assert SchemaVersions.CLIENT_PROTOCOL == 1;
        assert SchemaVersions.currentVersion(SchemaId.CLIENT_PROTOCOL)
                .orElseThrow() == 1;

        FeatureFlagService flags = new FeatureFlagService();
        for (FeatureKey key : List.of(
                FeatureKey.PLAYER_PERSISTENCE,
                FeatureKey.EQUIPMENT_V2,
                FeatureKey.MOD_SYSTEM,
                FeatureKey.FIRE_SYSTEM,
                FeatureKey.ICE_SYSTEM,
                FeatureKey.LIGHTNING_SYSTEM,
                FeatureKey.GATHERING,
                FeatureKey.REFINING,
                FeatureKey.CRAFTING,
                FeatureKey.TIER_PROMOTION,
                FeatureKey.ENHANCEMENT_V2,
                FeatureKey.REPAIR_V2,
                FeatureKey.PARTY,
                FeatureKey.QUESTS,
                FeatureKey.REWARD_V2)) {
            assert !flags.isEnabled(key) : key + " must remain disabled";
        }
    }

    /** Reuses the focused public-API contracts instead of copying domain logic. */
    private static void componentSmokeContractsCoexist() throws Exception {
        TrackEFoundationTest.main(new String[0]);
        TrackFPartyQuestRewardFoundationTest.main(new String[0]);
    }

    private static void enhancementAndRewardShareTrackDWithoutCrossTalk() {
        SharedInfrastructure infrastructure = new SharedInfrastructure();
        try (TransactionEngine engine = new TransactionEngine(8, 64, CLOCK)) {
            AtomicInteger rolls = new AtomicInteger();
            EnhancementScenario enhancement = enhancementScenario(10, infrastructure, rolls);
            TransactionAuditResult first = engine.execute(
                    enhancement.plan().transactionRequest(), enhancement.participant());
            TransactionAuditResult replay = engine.execute(
                    enhancement.plan().transactionRequest(), enhancement.participant());
            assert first.outcome() == TransactionAuditResult.Outcome.COMMITTED;
            assert replay.replayed();
            assert rolls.get() == 1 : "Enhancement outcome resolved more than once";
            assert infrastructure.equipmentWrites.get() == 1;
            EquipmentMutationProposal resolved = infrastructure.resolved.get(
                    enhancement.plan().transactionRequest().requestId());
            assert resolved != null;
            assert resolved.proposedItem().enhancementLevel() == 1;
            assert enhancement.source().enhancementLevel() == 0 : "Source equipment mutated";

            RewardClaimRequest rewardClaim = rewardClaim(20);
            UUID rewardRequestId = RewardTransactionIdentity.requestId(rewardClaim.key());
            RewardParticipant rewardParticipant = new RewardParticipant(
                    infrastructure, null);
            TransactionRewardDeliveryPort rewardPort = rewardPort(
                    engine, rewardParticipant, rewardRequestId);
            assert rewardPort.deliver(rewardClaim).status()
                    == RewardDeliveryReceipt.Status.DELIVERED;
            assert rewardPort.deliver(rewardClaim).status()
                    == RewardDeliveryReceipt.Status.DELIVERED;
            assert rewardParticipant.count(TransactionStage.COMMIT) == 1;

            UUID enhancementRequestId = enhancement.plan().transactionRequest().requestId();
            assert !enhancementRequestId.equals(rewardRequestId);
            assert !infrastructure.tokens.get(enhancementRequestId)
                    .equals(infrastructure.tokens.get(rewardRequestId));
            assert infrastructure.terminals.containsKey(enhancementRequestId);
            assert infrastructure.terminals.containsKey(rewardRequestId);

            AtomicInteger failedRolls = new AtomicInteger();
            EnhancementScenario failedEnhancement = enhancementScenario(
                    30, infrastructure, failedRolls);
            EquipmentOperationPlan failingPlan = new EquipmentOperationPlan(
                    failedEnhancement.plan().transactionRequest(),
                    failedEnhancement.plan().resources(), () -> {
                failedRolls.incrementAndGet();
                throw new IllegalStateException("fixture-enhancement-resolution-failure");
            });
            EquipmentOperationParticipant failingParticipant =
                    new EquipmentOperationParticipant(
                            failingPlan, infrastructure, infrastructure, infrastructure);
            TransactionAuditResult equipmentFailure = engine.execute(
                    failingPlan.transactionRequest(), failingParticipant);
            assert equipmentFailure.outcome() == TransactionAuditResult.Outcome.REJECTED;
            assert infrastructure.rollbackCount(failingPlan.transactionRequest().requestId()) == 1;
            assert infrastructure.rollbackCount(rewardRequestId) == 0;
            assert engine.execute(
                    enhancement.plan().transactionRequest(), enhancement.participant()).replayed();

            RewardClaimRequest failedRewardClaim = rewardClaim(40);
            UUID failedRewardId = RewardTransactionIdentity.requestId(failedRewardClaim.key());
            RewardParticipant failedReward = new RewardParticipant(
                    infrastructure, TransactionStage.PERSIST);
            TransactionRewardDeliveryPort failedRewardPort = rewardPort(
                    engine, failedReward, failedRewardId);
            assert failedRewardPort.deliver(failedRewardClaim).status()
                    == RewardDeliveryReceipt.Status.PERSIST_FAILURE;
            assert failedRewardPort.deliver(failedRewardClaim).status()
                    == RewardDeliveryReceipt.Status.PERSIST_FAILURE;
            assert failedReward.count(TransactionStage.PERSIST) == 1;
            assert infrastructure.rollbackCount(failedRewardId) == 1;
            assert infrastructure.rollbackCount(enhancementRequestId) == 0;

            RewardClaimRequest uncertainClaim = rewardClaim(50);
            UUID uncertainId = RewardTransactionIdentity.requestId(uncertainClaim.key());
            RewardParticipant uncertain = new RewardParticipant(
                    infrastructure, TransactionStage.COMMIT);
            TransactionRewardDeliveryPort uncertainPort = rewardPort(
                    engine, uncertain, uncertainId);
            assert uncertainPort.deliver(uncertainClaim).status()
                    == RewardDeliveryReceipt.Status.COMMIT_UNCERTAIN;
            assert uncertainPort.deliver(uncertainClaim).status()
                    == RewardDeliveryReceipt.Status.COMMIT_UNCERTAIN;
            assert uncertain.count(TransactionStage.COMMIT) == 1
                    : "COMMIT_UNCERTAIN was automatically rerun";

            assert Set.of(enhancementRequestId, rewardRequestId, failedRewardId, uncertainId)
                    .size() == 4 : "Track D terminal namespaces collided";
        }
    }

    private static EnhancementScenario enhancementScenario(
            int seed,
            SharedInfrastructure infrastructure,
            AtomicInteger rolls
    ) {
        EquipmentItemV1 source = equipment(uuid(seed + 1), 0, false);
        EnhancementAttempt attempt = new EnhancementAttempt(
                uuid(seed), PLAYER, "projects:warrior-sword", source, 1,
                EquipmentExtensionSnapshot.empty());
        EnhancementPolicy policy = enhancementPolicy();
        var preparation = new EnhancementTransactionAdapter().prepare(
                attempt, policy, () -> {
                    rolls.incrementAndGet();
                    return 0.0;
                });
        assert preparation.status() == EnhancementTransactionAdapter.Status.READY;
        EquipmentOperationPlan plan = preparation.plan().orElseThrow();
        return new EnhancementScenario(source, plan,
                new EquipmentOperationParticipant(
                        plan, infrastructure, infrastructure, infrastructure));
    }

    private static EnhancementPolicy enhancementPolicy() {
        EnumMap<EnhancementOutcome, Double> probabilities =
                new EnumMap<>(EnhancementOutcome.class);
        probabilities.put(EnhancementOutcome.SUCCESS, 1.0);
        probabilities.put(EnhancementOutcome.NO_CHANGE, 0.0);
        probabilities.put(EnhancementOutcome.DOWNGRADE, 0.0);
        probabilities.put(EnhancementOutcome.BROKEN, 0.0);
        return new EnhancementPolicy(
                new EnhancementPolicyRevision("projects:wave2-enhancement-fixture", 1),
                0, probabilities, Map.of(
                EnhancementOutcome.SUCCESS, new EnhancementTransition(1, false),
                EnhancementOutcome.NO_CHANGE, new EnhancementTransition(0, false),
                EnhancementOutcome.DOWNGRADE, new EnhancementTransition(0, false),
                EnhancementOutcome.BROKEN, new EnhancementTransition(0, true)),
                new OperationResourcePlan(List.of(
                        new OperationResourcePlan.MaterialCost(
                                "projects:enhancement-stone", 1)), 10));
    }

    private static TransactionRewardDeliveryPort rewardPort(
            TransactionEngine engine,
            RewardParticipant participant,
            UUID requestId
    ) {
        return new TransactionRewardDeliveryPort(
                engine,
                claim -> new TransactionRequest(
                        requestId, claim.key().playerId(),
                        "projects:operation/reward-claim",
                        claim.key().rewardDefinitionId(),
                        claim.key().rewardRevision(), 1,
                        List.of(new TransactionRequest.InputRevision(
                                "projects:reward-source/"
                                        + claim.key().rewardSourceInstanceId(),
                                claim.key().rewardRevision()))),
                ignored -> participant);
    }

    private static void aggregatesSharePlayerIdentityWithoutOwningEachOther() {
        PlayerProgressSnapshot player = new PlayerProgressBuilder(PLAYER).build();
        TransactionRequest transaction = new TransactionRequest(
                uuid(100), PLAYER, "projects:operation/fixture", "projects:recipe/fixture",
                1, 1, List.of(new TransactionRequest.InputRevision(
                "projects:input/fixture", 1)));
        EnhancementAttempt enhancement = new EnhancementAttempt(
                uuid(101), PLAYER, "projects:warrior-sword",
                equipment(uuid(102), 0, false), 1, EquipmentExtensionSnapshot.empty());
        PartyMember partyMember = new PartyMember(PLAYER, 0, true, Optional.empty());
        ParticipationEvent event = new ParticipationEvent(
                new ParticipationKey(new EncounterId(uuid(103)), PLAYER,
                        "projects:wave2-integration", 1),
                1.0, ParticipationEvent.ContributionSemantics.DELTA, NOW);
        ParticipationRecord participation = new ParticipationRecord(event, 1.0, NOW);
        RewardClaimKey reward = new RewardClaimKey(
                PLAYER, "projects:source/quest", uuid(104),
                "projects:reward/fixture", 1);

        assert player.playerId().equals(PLAYER);
        assert transaction.playerId().equals(PLAYER);
        assert enhancement.playerId().equals(PLAYER);
        assert partyMember.playerId().equals(PLAYER);
        assert participation.event().key().playerId().equals(PLAYER);
        assert reward.playerId().equals(PLAYER);

        assertRecordDoesNotOwn(player.getClass(), "party.", "participation.", "reward.");
        assertRecordDoesNotOwn(transaction.getClass(), "enhancement.", "party.", "reward.");
        assertRecordDoesNotOwn(enhancement.getClass(), "party.", "participation.", "reward.");
        assertRecordDoesNotOwn(partyMember.getClass(), "player.progress.", "transaction.",
                "enhancement.", "participation.", "reward.");
        assertRecordDoesNotOwn(participation.getClass(), "player.progress.", "transaction.",
                "enhancement.", "party.", "reward.");
        assertRecordDoesNotOwn(reward.getClass(), "player.progress.", "transaction.",
                "enhancement.", "party.", "participation.");
    }

    private static void assertRecordDoesNotOwn(Class<?> type, String... fragments) {
        for (RecordComponent component : type.getRecordComponents()) {
            String signature = component.getGenericType().getTypeName();
            for (String fragment : fragments) {
                assert !signature.contains(fragment)
                        : type.getSimpleName() + " owns " + signature;
            }
        }
    }

    private static void purePublicApisRemainBukkitFree() {
        List<Class<?>> pureTypes = List.of(
                PlayerProgressSnapshot.class,
                TransactionRequest.class,
                EnhancementAttempt.class,
                EnhancementPolicy.class,
                EquipmentOperationPlan.class,
                EquipmentOperationParticipant.class,
                TierPromotionService.class,
                RepairService.class,
                PartyMember.class,
                PartyService.class,
                ParticipationRecord.class,
                ParticipationLedger.class,
                QuestProgressService.class,
                RewardClaimKey.class,
                RewardClaimService.class,
                TransactionRewardDeliveryPort.class);
        for (Class<?> type : pureTypes) {
            assert !type.getPackageName().contains(".bukkit.") : type;
            assertNoBukkitTypes(type);
        }
    }

    private static void assertNoBukkitTypes(Class<?> type) {
        for (Field field : type.getFields()) {
            assertNotBukkit(type, field.getGenericType().getTypeName());
        }
        for (Constructor<?> constructor : type.getConstructors()) {
            assertNotBukkit(type, constructor.toGenericString());
        }
        for (Method method : type.getMethods()) {
            if (method.getDeclaringClass() == Object.class) continue;
            assertNotBukkit(type, method.toGenericString());
        }
    }

    private static void assertNotBukkit(Class<?> owner, String signature) {
        for (String forbidden : List.of(
                "org.bukkit.entity.Player", "org.bukkit.inventory.ItemStack",
                "org.bukkit.World", "org.bukkit.Location")) {
            assert !signature.contains(forbidden)
                    : owner.getName() + " exposes " + signature;
        }
    }

    private static void gameplayEntrypointRemainsDisconnected() throws IOException {
        Set<Class<?>> disconnected = Set.of(
                EnhancementPolicy.class,
                TierPromotionService.class,
                RepairService.class,
                PartyService.class,
                ParticipationLedger.class,
                QuestProgressService.class,
                RewardClaimService.class);
        for (Field field : ProjectSPlugin.class.getDeclaredFields()) {
            assert !disconnected.contains(field.getType()) : field;
        }
        for (Method method : ProjectSPlugin.class.getDeclaredMethods()) {
            assert !disconnected.contains(method.getReturnType()) : method;
            for (Class<?> parameter : method.getParameterTypes()) {
                assert !disconnected.contains(parameter) : method;
            }
        }

        byte[] pluginClass;
        try (var stream = ProjectSPlugin.class.getResourceAsStream("ProjectSPlugin.class")) {
            assert stream != null;
            pluginClass = stream.readAllBytes();
        }
        String constantPool = new String(pluginClass, StandardCharsets.ISO_8859_1);
        for (Class<?> type : disconnected) {
            String internalName = type.getName().replace('.', '/');
            assert !constantPool.contains(internalName)
                    : "ProjectSPlugin startup references " + type.getName();
        }
        assert constantPool.contains(EnhancementManager.class.getName().replace('.', '/'))
                : "Legacy EnhancementManager wiring disappeared";
    }

    private static EquipmentItemV1 equipment(
            UUID instanceId,
            int enhancement,
            boolean broken
    ) {
        return new EquipmentItemV1(
                SchemaVersions.EQUIPMENT_ITEM, "warrior_sword_t1",
                EquipmentCategory.WEAPON, EquipmentSlot.WEAPON, EquipmentTier.T1, 12,
                EquipmentRarity.COMMON, EquipmentQuality.UNSPECIFIED,
                List.of(new BaseStatRoll("projects:physical-attack", 10)),
                List.of(EquipmentModSlot.empty(0)), Optional.empty(), enhancement, broken,
                BindingPolicy.UNBOUND, TradePolicy.DENY_ALL, Optional.of(instanceId));
    }

    private static RewardClaimRequest rewardClaim(int seed) {
        return new RewardClaimRequest(uuid(seed), new RewardClaimKey(
                PLAYER, "projects:source/quest", uuid(seed + 1),
                "projects:reward/fixture", 1), NOW);
    }

    private static UUID uuid(long seed) {
        return new UUID(0, seed);
    }

    private record EnhancementScenario(
            EquipmentItemV1 source,
            EquipmentOperationPlan plan,
            EquipmentOperationParticipant participant
    ) {
    }

    private static final class SharedInfrastructure implements
            EquipmentResourcePort, EquipmentWriteBoundary, EquipmentOperationJournal {
        private final Map<UUID, TransactionAuditResult> terminals =
                new ConcurrentHashMap<>();
        private final Map<UUID, EquipmentMutationProposal> resolved =
                new ConcurrentHashMap<>();
        private final Map<UUID, String> tokens = new ConcurrentHashMap<>();
        private final Map<UUID, AtomicInteger> rollbacks = new ConcurrentHashMap<>();
        private final AtomicInteger equipmentWrites = new AtomicInteger();

        @Override
        public Optional<InventoryCapacityProposal> validate(
                TransactionRequest request,
                OperationResourcePlan resources
        ) {
            return Optional.of(InventoryCapacityProposal.reservedInventory(1));
        }

        @Override
        public ReservationToken reserve(
                TransactionRequest request,
                OperationResourcePlan resources,
                InventoryCapacityProposal capacity
        ) {
            return token(request, "equipment");
        }

        private ReservationToken token(TransactionRequest request, String subject) {
            String value = subject + ":" + request.requestId();
            tokens.put(request.requestId(), value);
            return new ReservationToken(value);
        }

        @Override
        public void consume(
                TransactionRequest request,
                OperationResourcePlan resources,
                ReservationToken reservation
        ) {
            assert tokens.get(request.requestId()).equals(reservation.value());
        }

        @Override
        public void rollback(
                TransactionRequest request,
                OperationResourcePlan resources,
                ReservationToken reservation,
                TransactionStage lastCompletedStage
        ) {
            recordRollback(request.requestId());
        }

        @Override
        public WriteResult write(WriteRequest request) {
            equipmentWrites.incrementAndGet();
            return new WriteResult(true, request.expectedRevision() + 1, "committed");
        }

        @Override
        public Optional<TransactionAuditResult> findTerminal(UUID requestId) {
            return Optional.ofNullable(terminals.get(requestId));
        }

        @Override
        public Optional<EquipmentMutationProposal> findResolvedProposal(UUID requestId) {
            return Optional.ofNullable(resolved.get(requestId));
        }

        @Override
        public void recordResolvedProposal(EquipmentMutationProposal proposal) {
            resolved.putIfAbsent(proposal.requestId(), proposal);
        }

        @Override
        public void persistProposal(EquipmentMutationProposal proposal) {
            assert resolved.get(proposal.requestId()).equals(proposal);
        }

        @Override
        public void recordTerminal(TransactionAuditResult result) {
            terminals.putIfAbsent(result.requestId(), result);
        }

        @Override
        public void rollbackProposal(UUID requestId) {
            // Resource rollback owns the single observable rollback count.
        }

        private void recordRollback(UUID requestId) {
            rollbacks.computeIfAbsent(requestId, ignored -> new AtomicInteger())
                    .incrementAndGet();
        }

        private int rollbackCount(UUID requestId) {
            AtomicInteger value = rollbacks.get(requestId);
            return value == null ? 0 : value.get();
        }
    }

    private static final class RewardParticipant implements TransactionParticipant {
        private final SharedInfrastructure infrastructure;
        private final TransactionStage failureStage;
        private final List<TransactionStage> calls = new ArrayList<>();

        private RewardParticipant(
                SharedInfrastructure infrastructure,
                TransactionStage failureStage
        ) {
            this.infrastructure = infrastructure;
            this.failureStage = failureStage;
        }

        @Override
        public Optional<TransactionAuditResult> findTerminal(TransactionRequest request) {
            return Optional.ofNullable(infrastructure.terminals.get(request.requestId()));
        }

        @Override
        public Validation validate(TransactionRequest request) {
            stage(TransactionStage.VALIDATE);
            return Validation.allow(InventoryCapacityProposal.reservedInventory(1));
        }

        @Override
        public ReservationToken reserve(
                TransactionRequest request,
                InventoryCapacityProposal capacityProposal
        ) {
            stage(TransactionStage.RESERVE);
            return infrastructure.token(request, "reward");
        }

        @Override
        public void consume(TransactionRequest request, ReservationToken token) {
            stage(TransactionStage.CONSUME);
            assert infrastructure.tokens.get(request.requestId()).equals(token.value());
        }

        @Override
        public OutputProposal produce(TransactionRequest request, ReservationToken token) {
            stage(TransactionStage.PRODUCE);
            return new OutputProposal("projects:reward/fixture", 1, false);
        }

        @Override
        public void persist(
                TransactionRequest request,
                ReservationToken token,
                OutputProposal output
        ) {
            stage(TransactionStage.PERSIST);
        }

        @Override
        public TransactionAuditResult commit(
                TransactionRequest request,
                ReservationToken token,
                OutputProposal output,
                TransactionAuditResult proposedCommittedResult
        ) {
            stage(TransactionStage.COMMIT);
            infrastructure.terminals.putIfAbsent(request.requestId(), proposedCommittedResult);
            return proposedCommittedResult;
        }

        @Override
        public void recordTerminal(TransactionAuditResult terminalResult) {
            infrastructure.terminals.putIfAbsent(terminalResult.requestId(), terminalResult);
        }

        @Override
        public void rollback(
                TransactionRequest request,
                ReservationToken token,
                TransactionStage lastCompletedStage,
                OutputProposal output
        ) {
            infrastructure.recordRollback(request.requestId());
        }

        private void stage(TransactionStage stage) {
            calls.add(stage);
            if (stage == failureStage) {
                throw new IllegalStateException("fixture-reward-" + stage);
            }
        }

        private long count(TransactionStage stage) {
            return calls.stream().filter(stage::equals).count();
        }
    }
}
