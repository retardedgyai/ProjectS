package io.github.gyai.projects.equipment.operation;

import io.github.gyai.projects.equipment.*;
import io.github.gyai.projects.enhancement.v2.*;
import io.github.gyai.projects.schema.SchemaVersions;
import io.github.gyai.projects.transaction.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class EnhancementTransactionSafetyTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC);
    private EnhancementTransactionSafetyTest() { }

    public static void main(String[] args) {
        reservationPrecedesSingleResolutionAndCommit();
        duplicateRequestReplaysWithoutRerollOrRewrite();
        recordedResolutionSurvivesParticipantRetryWithoutReroll();
        inventoryFullRejectsBeforeResolution();
        resolutionFailureRollsBackReservation();
        consumeAndPersistFailuresRollbackWithoutReroll();
        commitUncertainIsIsolatedAndTerminalOnRetry();
    }

    private static void reservationPrecedesSingleResolutionAndCommit() {
        Scenario scenario = scenario(100);
        try (TransactionEngine engine = new TransactionEngine(8, 64, CLOCK)) {
            TransactionAuditResult result = engine.execute(
                    scenario.plan.transactionRequest(), scenario.participant);
            assert result.outcome() == TransactionAuditResult.Outcome.COMMITTED;
            assert result.completedStages().equals(List.of(TransactionStage.values()));
            assert scenario.rolls.get() == 1;
            assert scenario.writer.writes == 1;
            assert scenario.resources.consumeCalls == 1;
            assert scenario.resources.rollbackCalls == 0;
            assert scenario.events.equals(List.of(
                    "validate", "reserve", "resolve", "record-resolution", "consume",
                    "persist", "write", "terminal"));
            assert scenario.participant.resolved();
        }
    }

    private static void recordedResolutionSurvivesParticipantRetryWithoutReroll() {
        Scenario scenario = scenario(115);
        InventoryCapacityProposal capacity = scenario.resources.validate(
                scenario.plan.transactionRequest(), scenario.plan.resources()).orElseThrow();
        scenario.participant.reserve(scenario.plan.transactionRequest(), capacity);
        assert scenario.rolls.get() == 1;

        AtomicInteger retryRolls = new AtomicInteger();
        EquipmentOperationPlan retryPlan = new EquipmentOperationPlan(
                scenario.plan.transactionRequest(), scenario.plan.resources(), () -> {
            retryRolls.incrementAndGet();
            return scenario.resolvedProposal;
        });
        EquipmentOperationParticipant retry = new EquipmentOperationParticipant(
                retryPlan, scenario.resources, scenario.writer, scenario.journal);
        retry.reserve(retryPlan.transactionRequest(), capacity);
        assert retry.resolved();
        assert retryRolls.get() == 0 : "durable resolved proposal was rerolled";
    }

    private static void duplicateRequestReplaysWithoutRerollOrRewrite() {
        Scenario scenario = scenario(110);
        try (TransactionEngine engine = new TransactionEngine(8, 64, CLOCK)) {
            TransactionAuditResult first = engine.execute(
                    scenario.plan.transactionRequest(), scenario.participant);
            TransactionAuditResult replay = engine.execute(
                    scenario.plan.transactionRequest(), scenario.participant);
            assert first.outcome() == TransactionAuditResult.Outcome.COMMITTED;
            assert replay.outcome() == TransactionAuditResult.Outcome.COMMITTED && replay.replayed();
            assert scenario.rolls.get() == 1;
            assert scenario.writer.writes == 1;
            assert scenario.resources.reserveCalls == 1;
            assert scenario.resources.consumeCalls == 1;
        }
    }

    private static void inventoryFullRejectsBeforeResolution() {
        Scenario scenario = scenario(120);
        scenario.resources.capacityAvailable = false;
        try (TransactionEngine engine = new TransactionEngine(8, 64, CLOCK)) {
            TransactionAuditResult result = engine.execute(
                    scenario.plan.transactionRequest(), scenario.participant);
            assert result.outcome() == TransactionAuditResult.Outcome.REJECTED;
            assert result.reason().contains("output-capacity");
            assert scenario.rolls.get() == 0;
            assert scenario.resources.reserveCalls == 0;
            assert scenario.writer.writes == 0;
        }
    }

    private static void resolutionFailureRollsBackReservation() {
        Scenario scenario = scenario(130, () -> {
            scenarioCounter.incrementAndGet();
            throw new IllegalStateException("fixture-resolution-failure");
        });
        int before = scenarioCounter.get();
        try (TransactionEngine engine = new TransactionEngine(8, 64, CLOCK)) {
            TransactionAuditResult result = engine.execute(
                    scenario.plan.transactionRequest(), scenario.participant);
            assert result.outcome() == TransactionAuditResult.Outcome.REJECTED;
            assert scenarioCounter.get() == before + 1;
            assert scenario.resources.reserveCalls == 1;
            assert scenario.resources.rollbackCalls == 1
                    : "reserve-acquired resources were not restored";
            assert scenario.resources.lastRollbackStage == TransactionStage.VALIDATE;
            assert scenario.resources.consumeCalls == 0;
        }
    }

    private static void consumeAndPersistFailuresRollbackWithoutReroll() {
        Scenario consume = scenario(140);
        consume.resources.failConsume = true;
        try (TransactionEngine engine = new TransactionEngine(8, 64, CLOCK)) {
            TransactionAuditResult result = engine.execute(
                    consume.plan.transactionRequest(), consume.participant);
            assert result.outcome() == TransactionAuditResult.Outcome.ROLLED_BACK;
            assert consume.rolls.get() == 1;
            assert consume.resources.rollbackCalls == 1;
            assert consume.resources.lastRollbackStage == TransactionStage.RESERVE;
            assert consume.writer.writes == 0;
        }

        Scenario persist = scenario(150);
        persist.journal.failPersist = true;
        try (TransactionEngine engine = new TransactionEngine(8, 64, CLOCK)) {
            TransactionAuditResult result = engine.execute(
                    persist.plan.transactionRequest(), persist.participant);
            assert result.outcome() == TransactionAuditResult.Outcome.ROLLED_BACK;
            assert persist.rolls.get() == 1;
            assert persist.resources.rollbackCalls == 1;
            assert persist.resources.lastRollbackStage == TransactionStage.PRODUCE;
            assert persist.journal.rollbackCalls == 1;
            assert persist.writer.writes == 0;
        }
    }

    private static void commitUncertainIsIsolatedAndTerminalOnRetry() {
        Scenario scenario = scenario(160);
        scenario.writer.committed = false;
        try (TransactionEngine engine = new TransactionEngine(8, 64, CLOCK)) {
            TransactionAuditResult uncertain = engine.execute(
                    scenario.plan.transactionRequest(), scenario.participant);
            assert uncertain.outcome() == TransactionAuditResult.Outcome.COMMIT_UNCERTAIN;
            assert scenario.resources.rollbackCalls == 0
                    : "uncertain commit must not restore possibly consumed inputs";
            assert scenario.rolls.get() == 1;
            assert scenario.writer.writes == 1;
            TransactionAuditResult replay = engine.execute(
                    scenario.plan.transactionRequest(), scenario.participant);
            assert replay.outcome() == TransactionAuditResult.Outcome.COMMIT_UNCERTAIN;
            assert replay.replayed();
            assert scenario.rolls.get() == 1 && scenario.writer.writes == 1;
        }
    }

    private static final AtomicInteger scenarioCounter = new AtomicInteger();

    private static Scenario scenario(int seed) {
        ArrayList<String> events = new ArrayList<>();
        AtomicInteger rolls = new AtomicInteger();
        Scenario[] holder = new Scenario[1];
        EquipmentOperationPlan.ProposalResolver resolver = () -> {
            events.add("resolve");
            rolls.incrementAndGet();
            return holder[0].resolvedProposal;
        };
        Scenario scenario = scenario(seed, resolver, events, rolls);
        holder[0] = scenario;
        return scenario;
    }

    private static Scenario scenario(
            int seed,
            EquipmentOperationPlan.ProposalResolver resolver) {
        return scenario(seed, resolver, new ArrayList<>(), new AtomicInteger());
    }

    private static Scenario scenario(
            int seed,
            EquipmentOperationPlan.ProposalResolver resolver,
            ArrayList<String> events,
            AtomicInteger rolls) {
        UUID requestId = uuid(seed);
        UUID player = uuid(seed + 1);
        EquipmentItemV1 source = equipment(uuid(seed + 2));
        EquipmentItemV1 replacement = EquipmentItems.replaceMutableState(
                source, source.tier(), source.itemLevel(), source.quality(),
                source.modSlots(), source.enhancementLevel() + 1, false, source.binding());
        OperationResourcePlan resources = new OperationResourcePlan(
                List.of(new OperationResourcePlan.MaterialCost(
                        "projects:enhancement-stone", 1)), 20);
        EquipmentMutationProposal proposal = new EquipmentMutationProposal(
                requestId, player, "projects:enhancement-v2", "projects:fixture-policy",
                "projects:warrior-sword", 3, replacement,
                new EquipmentExtensionSnapshot(Map.of(
                        "projects:display-name", "Original name")), resources,
                List.of(new TransactionRequest.InputRevision(
                        EquipmentMutationProposal.inputId(source.instanceId().orElseThrow()), 3)));
        EquipmentOperationPlan plan = new EquipmentOperationPlan(
                proposal.transactionRequest(), resources, resolver);
        FakeResources resourcePort = new FakeResources(events);
        FakeWriter writer = new FakeWriter(events);
        FakeJournal journal = new FakeJournal(events);
        EquipmentOperationParticipant participant = new EquipmentOperationParticipant(
                plan, resourcePort, writer, journal);
        return new Scenario(
                plan, proposal, participant, resourcePort, writer, journal, events, rolls);
    }

    private static EquipmentItemV1 equipment(UUID instanceId) {
        return new EquipmentItemV1(
                SchemaVersions.EQUIPMENT_ITEM, "warrior_sword_t1",
                EquipmentCategory.WEAPON, EquipmentSlot.WEAPON, EquipmentTier.T1, 12,
                EquipmentRarity.COMMON, EquipmentQuality.UNSPECIFIED,
                List.of(new BaseStatRoll("projects:physical-attack", 10)),
                List.of(EquipmentModSlot.empty(0)), Optional.empty(), 5, false,
                BindingPolicy.UNBOUND, TradePolicy.DENY_ALL, Optional.of(instanceId));
    }

    private static UUID uuid(int seed) { return new UUID(0, seed); }

    private record Scenario(
            EquipmentOperationPlan plan,
            EquipmentMutationProposal resolvedProposal,
            EquipmentOperationParticipant participant,
            FakeResources resources,
            FakeWriter writer,
            FakeJournal journal,
            ArrayList<String> events,
            AtomicInteger rolls) { }

    private static final class FakeResources implements EquipmentResourcePort {
        private final ArrayList<String> events;
        private boolean capacityAvailable = true;
        private boolean failConsume;
        private int reserveCalls;
        private int consumeCalls;
        private int rollbackCalls;
        private TransactionStage lastRollbackStage;
        private FakeResources(ArrayList<String> events) { this.events = events; }
        @Override public Optional<InventoryCapacityProposal> validate(
                TransactionRequest request, OperationResourcePlan resources) {
            events.add("validate");
            return capacityAvailable
                    ? Optional.of(InventoryCapacityProposal.reservedInventory(1))
                    : Optional.empty();
        }
        @Override public ReservationToken reserve(
                TransactionRequest request, OperationResourcePlan resources,
                InventoryCapacityProposal capacity) {
            events.add("reserve");
            reserveCalls++;
            return new ReservationToken("reservation-" + request.requestId());
        }
        @Override public void consume(
                TransactionRequest request, OperationResourcePlan resources,
                ReservationToken reservation) {
            events.add("consume");
            consumeCalls++;
            if (failConsume) throw new IllegalStateException("consume-failure");
        }
        @Override public void rollback(
                TransactionRequest request, OperationResourcePlan resources,
                ReservationToken reservation, TransactionStage lastCompletedStage) {
            events.add("rollback-resources");
            rollbackCalls++;
            lastRollbackStage = lastCompletedStage;
        }
    }

    private static final class FakeWriter implements EquipmentWriteBoundary {
        private final ArrayList<String> events;
        private int writes;
        private boolean committed = true;
        private FakeWriter(ArrayList<String> events) { this.events = events; }
        @Override public WriteResult write(WriteRequest request) {
            events.add("write");
            writes++;
            return new WriteResult(committed, request.expectedRevision() + 1,
                    committed ? "committed" : "uncertain");
        }
    }

    private static final class FakeJournal implements EquipmentOperationJournal {
        private final ArrayList<String> events;
        private final Map<UUID, TransactionAuditResult> terminals = new HashMap<>();
        private final Map<UUID, EquipmentMutationProposal> resolved = new HashMap<>();
        private boolean failPersist;
        private int rollbackCalls;
        private FakeJournal(ArrayList<String> events) { this.events = events; }
        @Override public Optional<TransactionAuditResult> findTerminal(UUID requestId) {
            return Optional.ofNullable(terminals.get(requestId));
        }
        @Override public Optional<EquipmentMutationProposal> findResolvedProposal(UUID requestId) {
            return Optional.ofNullable(resolved.get(requestId));
        }
        @Override public void recordResolvedProposal(EquipmentMutationProposal proposal) {
            events.add("record-resolution");
            resolved.putIfAbsent(proposal.requestId(), proposal);
        }
        @Override public void persistProposal(EquipmentMutationProposal proposal) {
            events.add("persist");
            if (failPersist) throw new IllegalStateException("persist-failure");
        }
        @Override public void recordTerminal(TransactionAuditResult result) {
            events.add("terminal");
            terminals.putIfAbsent(result.requestId(), result);
        }
        @Override public void rollbackProposal(UUID requestId) {
            events.add("rollback-journal");
            rollbackCalls++;
        }
    }
}
