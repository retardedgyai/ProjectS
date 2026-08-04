package io.github.gyai.projects.transaction;

import io.github.gyai.projects.crafting.DefinitionCatalog;
import io.github.gyai.projects.crafting.OutputProposal;
import io.github.gyai.projects.crafting.RecipeDefinitionV1;
import io.github.gyai.projects.gathering.GatheringNode;
import io.github.gyai.projects.gathering.GatheringNodeRegistry;
import io.github.gyai.projects.gathering.ResourceDefinitionV1;
import io.github.gyai.projects.schema.SchemaVersions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class TrackDTransactionFoundationTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC);

    public static void main(String[] args) throws Exception {
        definitionsAreVersionedValidatedAndImmutable();
        quantitiesRejectOverflowAndInvalidNumbers();
        transactionSuccessIsOrderedAndIdempotent();
        everyFailureBoundaryIsSafe();
        activeConflictsAndBoundsAreEnforced();
        logoutStopAndRecoveryRollBack();
        gatheringNodesReserveCancelRespawnAndCleanUp();
    }

    private static void definitionsAreVersionedValidatedAndImmutable() {
        ResourceDefinitionV1 ore = resource("projects:iron-ore");
        ResourceDefinitionV1 catalyst = resource("projects:refining-catalyst");
        OutputProposal output = new OutputProposal("projects:iron-ingot", 1, false);
        RecipeDefinitionV1 recipe = recipe(
                "projects:refine/iron-direct",
                RecipeDefinitionV1.RecipeType.REFINE_DIRECT,
                List.of(input("projects:iron-ore", 2)),
                Optional.of(input("projects:refining-catalyst", 1)),
                output);
        DefinitionCatalog catalog = new DefinitionCatalog(
                List.of(ore, catalyst), List.of(recipe));

        assert recipe.schemaVersion() == SchemaVersions.RECIPE_DEFINITION;
        assert recipe.inputs().equals(List.of(input("projects:iron-ore", 2)));
        assert recipe.output().equals(output);
        assert catalog.resources().size() == 2;
        assert catalog.recipes().get(recipe.recipeId()).equals(recipe);
        expectUnsupported(() -> recipe.inputs().clear());
        expectUnsupported(() -> catalog.resources().clear());

        expectIllegal(() -> new DefinitionCatalog(
                List.of(ore, ore), List.of()));
        expectIllegal(() -> new DefinitionCatalog(
                List.of(ore, catalyst), List.of(recipe, recipe)));
        expectIllegal(() -> new DefinitionCatalog(
                List.of(ore), List.of(recipe)));
        expectIllegal(() -> resource("../unsafe"));
        expectIllegal(() -> recipe(
                "projects:refine/duplicate",
                RecipeDefinitionV1.RecipeType.REFINE_DIRECT,
                List.of(input("projects:iron-ore", 1),
                        input("projects:iron-ore", 2)),
                Optional.empty(), output));
        expectIllegal(() -> recipe(
                "projects:craft/not-equipment",
                RecipeDefinitionV1.RecipeType.CRAFT_EQUIPMENT_BASE,
                List.of(input("projects:iron-ore", 1)),
                Optional.empty(), output));
        assert new OutputProposal("projects:iron-ingot", 1, false).equals(output)
                : "Output proposals must be deterministic value objects";
    }

    private static void quantitiesRejectOverflowAndInvalidNumbers() {
        expectIllegal(() -> input("projects:iron-ore", 0));
        expectIllegal(() -> input("projects:iron-ore", -1));
        expectArithmetic(() -> QuantityMath.add(Long.MAX_VALUE, 1));
        expectArithmetic(() -> QuantityMath.multiply(Long.MAX_VALUE, 2));
        expectIllegal(() -> new RecipeDefinitionV1.FeePlaceholder(true, Double.NaN));
        expectIllegal(() -> new RecipeDefinitionV1.FeePlaceholder(
                true, Double.POSITIVE_INFINITY));
        expectIllegal(() -> new RecipeDefinitionV1.FeePlaceholder(false, 1));
        assert RecipeDefinitionV1.FeePlaceholder.unspecified().amount() == 0.0;
        expectIllegal(() -> new InventoryCapacityProposal(
                0, InventoryCapacityProposal.DeliveryMode.RESERVED_INVENTORY));
        expectIllegal(() -> new TransactionParticipant.Validation(
                true, "", Optional.empty()));
    }

    private static void transactionSuccessIsOrderedAndIdempotent() {
        TransactionEngine engine = new TransactionEngine(4, 2, CLOCK);
        TransactionRequest request = request(1);
        FakeParticipant participant = new FakeParticipant();
        TransactionAuditResult result = engine.execute(request, participant);

        assert result.outcome() == TransactionAuditResult.Outcome.COMMITTED;
        assert result.completedStages().equals(List.of(TransactionStage.values()));
        assert result.output().orElseThrow().equals(participant.output);
        assert participant.calls.equals(List.of(TransactionStage.values()));
        assert participant.rollbackCount == 0;

        TransactionAuditResult replay = engine.execute(request, new FakeParticipant());
        assert replay.outcome() == TransactionAuditResult.Outcome.COMMITTED;
        assert replay.replayed();
        assert replay.requestId().equals(result.requestId());

        TransactionRequest conflictingRequest = new TransactionRequest(
                request.requestId(), uuid(9_999), request.recipeId(),
                request.expectedRevision());
        assert engine.execute(conflictingRequest, new FakeParticipant()).outcome()
                == TransactionAuditResult.Outcome.REPLAY_CONFLICT;

        engine.execute(request(2), new FakeParticipant());
        engine.execute(request(3), new FakeParticipant());
        assert engine.committedResultCount() == 2 : "Committed cache must be bounded";
        expectUnsupported(() -> result.completedStages().clear());
        engine.close();
        engine.close();
    }

    private static void everyFailureBoundaryIsSafe() {
        for (TransactionStage failure : TransactionStage.values()) {
            TransactionEngine engine = new TransactionEngine(2, 8, CLOCK);
            FakeParticipant participant = new FakeParticipant();
            participant.failAt = failure;
            TransactionAuditResult result = engine.execute(
                    request(10 + failure.ordinal()), participant);
            if (failure == TransactionStage.VALIDATE
                    || failure == TransactionStage.RESERVE) {
                assert result.outcome() == TransactionAuditResult.Outcome.REJECTED;
                assert participant.rollbackCount == 0;
            } else {
                assert result.outcome() == TransactionAuditResult.Outcome.ROLLED_BACK;
                assert participant.rollbackCount == 1 : failure;
            }
            assert engine.activeCount() == 0;
        }

        TransactionEngine engine = new TransactionEngine(1, 4, CLOCK);
        FakeParticipant full = new FakeParticipant();
        full.validation = TransactionParticipant.Validation.deny("inventory-full");
        TransactionAuditResult result = engine.execute(request(30), full);
        assert result.outcome() == TransactionAuditResult.Outcome.REJECTED;
        assert result.reason().equals("inventory-full");
        assert full.calls.equals(List.of(TransactionStage.VALIDATE));
        assert full.rollbackCount == 0;

        FakeParticipant rollbackFailure = new FakeParticipant();
        rollbackFailure.failAt = TransactionStage.PRODUCE;
        rollbackFailure.rollbackFails = true;
        TransactionAuditResult rollbackFailed = engine.execute(
                request(31), rollbackFailure);
        assert rollbackFailed.outcome()
                == TransactionAuditResult.Outcome.ROLLBACK_FAILED;
        assert rollbackFailed.reason().contains("rollback=rollback-failed");
    }

    private static void activeConflictsAndBoundsAreEnforced() throws Exception {
        TransactionEngine engine = new TransactionEngine(1, 4, CLOCK);
        FakeParticipant blocking = new FakeParticipant();
        blocking.blockAt = TransactionStage.VALIDATE;
        var executor = Executors.newSingleThreadExecutor();
        try {
            TransactionRequest first = request(40);
            var future = executor.submit(() -> engine.execute(first, blocking));
            assert blocking.entered.await(5, TimeUnit.SECONDS);
            assert engine.activeCount() == 1;

            TransactionAuditResult duplicate = engine.execute(
                    first, new FakeParticipant());
            assert duplicate.outcome()
                    == TransactionAuditResult.Outcome.DUPLICATE_ACTIVE;

            TransactionAuditResult limited = engine.execute(
                    request(41), new FakeParticipant());
            assert limited.outcome() == TransactionAuditResult.Outcome.ACTIVE_LIMIT;

            blocking.release.countDown();
            assert future.get(5, TimeUnit.SECONDS).outcome()
                    == TransactionAuditResult.Outcome.COMMITTED;
        } finally {
            blocking.release.countDown();
            executor.shutdownNow();
        }
    }

    private static void logoutStopAndRecoveryRollBack() throws Exception {
        TransactionEngine logoutEngine = new TransactionEngine(2, 4, CLOCK);
        FakeParticipant logoutParticipant = new FakeParticipant();
        logoutParticipant.blockAt = TransactionStage.CONSUME;
        var logoutExecutor = Executors.newSingleThreadExecutor();
        try {
            TransactionRequest logoutRequest = request(50);
            var logoutFuture = logoutExecutor.submit(
                    () -> logoutEngine.execute(logoutRequest, logoutParticipant));
            assert logoutParticipant.entered.await(5, TimeUnit.SECONDS);
            assert logoutEngine.cancelForPlayer(logoutRequest.playerId()) == 1;
            logoutParticipant.release.countDown();
            assert logoutFuture.get(5, TimeUnit.SECONDS).outcome()
                    == TransactionAuditResult.Outcome.ROLLED_BACK;
            assert logoutParticipant.rollbackCount == 1;
        } finally {
            logoutParticipant.release.countDown();
            logoutExecutor.shutdownNow();
        }

        TransactionEngine stopEngine = new TransactionEngine(2, 4, CLOCK);
        FakeParticipant stopParticipant = new FakeParticipant();
        stopParticipant.blockAt = TransactionStage.CONSUME;
        var stopExecutor = Executors.newSingleThreadExecutor();
        try {
            var stopFuture = stopExecutor.submit(
                    () -> stopEngine.execute(request(51), stopParticipant));
            assert stopParticipant.entered.await(5, TimeUnit.SECONDS);
            stopEngine.close();
            stopEngine.close();
            stopParticipant.release.countDown();
            assert stopFuture.get(5, TimeUnit.SECONDS).outcome()
                    == TransactionAuditResult.Outcome.ROLLED_BACK;
            assert stopParticipant.rollbackCount == 1;
            assert stopEngine.execute(request(52), new FakeParticipant()).outcome()
                    == TransactionAuditResult.Outcome.CLOSED;
        } finally {
            stopParticipant.release.countDown();
            stopExecutor.shutdownNow();
        }

        TransactionEngine recoveryEngine = new TransactionEngine(1, 2, CLOCK);
        FakeParticipant recoveryParticipant = new FakeParticipant();
        TransactionRequest recoveryRequest = request(53);
        TransactionAuditResult recovered = recoveryEngine.recover(
                new TransactionRecoveryRecord(
                        recoveryRequest,
                        new ReservationToken("recovery-token"),
                        TransactionStage.PERSIST,
                        Optional.of(recoveryParticipant.output)),
                recoveryParticipant);
        assert recovered.outcome() == TransactionAuditResult.Outcome.ROLLED_BACK;
        assert recovered.reason().equals("recovered-incomplete-transaction");
        assert recoveryParticipant.rollbackCount == 1;
        TransactionAuditResult recoveryReplay = recoveryEngine.recover(
                new TransactionRecoveryRecord(
                        recoveryRequest,
                        new ReservationToken("recovery-token"),
                        TransactionStage.PERSIST,
                        Optional.of(recoveryParticipant.output)),
                recoveryParticipant);
        assert recoveryReplay.replayed();
        assert recoveryParticipant.rollbackCount == 1
                : "Recovery retry must not roll back twice";
    }

    private static void gatheringNodesReserveCancelRespawnAndCleanUp() {
        Instant start = CLOCK.instant();
        GatheringNode node = new GatheringNode(
                "projects:node/iron-1", "projects:iron-ore",
                "world-alpha", "10,64,20",
                (depletedAt, now) -> !now.isBefore(depletedAt.plusSeconds(5)));
        UUID firstReservation = uuid(70);
        UUID player = uuid(71);

        assert node.snapshot().state() == GatheringNode.State.AVAILABLE;
        assert node.reserve(firstReservation, player);
        assert !node.reserve(uuid(72), player) : "Only one reservation is allowed";
        assert !node.cancel(uuid(72));
        assert node.cancel(firstReservation);
        assert node.reserve(firstReservation, player);
        assert node.deplete(firstReservation, start);
        assert node.snapshot().state() == GatheringNode.State.DEPLETED;
        assert !node.refresh(start.plusSeconds(4));
        assert node.refresh(start.plusSeconds(5));
        assert node.snapshot().state() == GatheringNode.State.AVAILABLE;

        GatheringNodeRegistry registry = new GatheringNodeRegistry(1);
        registry.register(node);
        expectIllegalState(() -> registry.register(new GatheringNode(
                "projects:node/copper-1", "projects:copper-ore",
                "world-alpha", "11,64,20", (depleted, now) -> false)));
        assert registry.size() == 1;
        expectUnsupported(() -> registry.snapshot().clear());
        registry.close();
        registry.close();
        assert registry.size() == 0;
        assert node.snapshot().closed();
        assert !node.reserve(uuid(73), player);
    }

    private static ResourceDefinitionV1 resource(String id) {
        return new ResourceDefinitionV1(1, 0, id, "resource." + id.replace(':', '.'));
    }

    private static RecipeDefinitionV1 recipe(
            String id,
            RecipeDefinitionV1.RecipeType type,
            List<RecipeDefinitionV1.Input> inputs,
            Optional<RecipeDefinitionV1.Input> catalyst,
            OutputProposal output
    ) {
        return new RecipeDefinitionV1(
                SchemaVersions.RECIPE_DEFINITION, 0, id, type, inputs, catalyst,
                output, RecipeDefinitionV1.FeePlaceholder.unspecified(),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static RecipeDefinitionV1.Input input(String id, long quantity) {
        return new RecipeDefinitionV1.Input(
                id, quantity, RecipeDefinitionV1.InputDisposition.REFUNDABLE);
    }

    private static TransactionRequest request(int seed) {
        return new TransactionRequest(
                uuid(seed), uuid(1_000 + seed),
                "projects:craft/test-" + seed, seed);
    }

    private static UUID uuid(int seed) {
        return new UUID(0, seed);
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectIllegalState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected.
        }
    }

    private static void expectArithmetic(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected ArithmeticException");
        } catch (ArithmeticException expected) {
            // Expected.
        }
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static final class FakeParticipant implements TransactionParticipant {
        private final List<TransactionStage> calls = new java.util.ArrayList<>();
        private final OutputProposal output =
                new OutputProposal("projects:test-output", 1, false);
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private TransactionParticipant.Validation validation =
                TransactionParticipant.Validation.allow(
                        InventoryCapacityProposal.reservedInventory(1));
        private TransactionStage failAt;
        private TransactionStage blockAt;
        private int rollbackCount;
        private boolean rollbackFails;

        @Override
        public Validation validate(TransactionRequest request) {
            stage(TransactionStage.VALIDATE);
            return validation;
        }

        @Override
        public ReservationToken reserve(TransactionRequest request) {
            stage(TransactionStage.RESERVE);
            return new ReservationToken("reservation-" + request.requestId());
        }

        @Override
        public void consume(TransactionRequest request, ReservationToken token) {
            stage(TransactionStage.CONSUME);
        }

        @Override
        public OutputProposal produce(
                TransactionRequest request,
                ReservationToken token
        ) {
            stage(TransactionStage.PRODUCE);
            return output;
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
        public void commit(
                TransactionRequest request,
                ReservationToken token,
                OutputProposal output
        ) {
            stage(TransactionStage.COMMIT);
        }

        @Override
        public void rollback(
                TransactionRequest request,
                ReservationToken token,
                TransactionStage lastCompletedStage,
                OutputProposal output
        ) {
            rollbackCount++;
            if (rollbackFails) throw new IllegalStateException("rollback-failed");
        }

        private void stage(TransactionStage stage) {
            calls.add(stage);
            if (blockAt == stage) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test-timeout");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("test-interrupted", exception);
                }
            }
            if (failAt == stage) throw new IllegalStateException("fail-" + stage);
        }
    }
}
