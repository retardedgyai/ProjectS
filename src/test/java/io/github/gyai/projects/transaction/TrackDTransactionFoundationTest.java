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
import java.util.concurrent.ConcurrentHashMap;
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
        expectIllegal(() -> new TransactionRecoveryRecord(
                request(99), new ReservationToken("committed"),
                TransactionStage.COMMIT, Optional.empty()));
        expectIllegal(() -> new TransactionRecoveryRecord(
                request(98), new ReservationToken("not-reserved"),
                TransactionStage.VALIDATE, Optional.empty()));
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
        assert participant.reservedCapacity.equals(
                InventoryCapacityProposal.reservedInventory(1));

        TransactionAuditResult replay = engine.execute(request, new FakeParticipant());
        assert replay.outcome() == TransactionAuditResult.Outcome.COMMITTED;
        assert replay.replayed();
        assert replay.requestId().equals(result.requestId());

        TransactionRequest conflictingRequest = new TransactionRequest(
                request.requestId(), uuid(9_999), request.operationId(),
                request.recipeId(), request.expectedRevision(),
                request.expectedOutputUnits(), request.inputs());
        assert engine.execute(conflictingRequest, new FakeParticipant()).outcome()
                == TransactionAuditResult.Outcome.REPLAY_CONFLICT;

        assert engine.execute(request(2), new FakeParticipant()).outcome()
                == TransactionAuditResult.Outcome.COMMITTED;
        FakeParticipant rejectedByRetention = new FakeParticipant();
        assert engine.execute(request(3), rejectedByRetention).outcome()
                == TransactionAuditResult.Outcome.TERMINAL_LIMIT;
        assert rejectedByRetention.calls.isEmpty();
        assert engine.committedResultCount() == 2 : "Committed cache must be bounded";

        int callsBeforeRestart = participant.calls.size();
        TransactionEngine restarted = new TransactionEngine(2, 2, CLOCK);
        TransactionAuditResult durableReplay = restarted.execute(request, participant);
        assert durableReplay.replayed();
        assert participant.calls.size() == callsBeforeRestart
                : "Durable commit receipt must close the restart crash window";

        TransactionEngine postCommitFailureEngine =
                new TransactionEngine(1, 2, CLOCK);
        FakeParticipant postCommitFailure = new FakeParticipant();
        postCommitFailure.commitThrowsAfterRecord = true;
        TransactionAuditResult recoveredCommit = postCommitFailureEngine.execute(
                request(4), postCommitFailure);
        assert recoveredCommit.outcome()
                == TransactionAuditResult.Outcome.COMMITTED;
        assert postCommitFailure.rollbackCount == 0
                : "A durable commit must never enter rollback";
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
            } else if (failure == TransactionStage.COMMIT) {
                assert result.outcome()
                        == TransactionAuditResult.Outcome.COMMIT_UNCERTAIN;
                assert participant.rollbackCount == 0;
                FakeParticipant uncertainRetry = new FakeParticipant();
                assert engine.execute(
                        request(10 + failure.ordinal()), uncertainRetry).replayed();
                assert uncertainRetry.calls.isEmpty();
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

        FakeParticipant insufficientCapacity = new FakeParticipant();
        TransactionRequest twoUnitOutput = new TransactionRequest(
                uuid(32), uuid(1_032), "projects:operation/craft",
                "projects:craft/test-32", 32, 2,
                List.of(new TransactionRequest.InputRevision(
                        "projects:item/test-32", 32)));
        TransactionAuditResult capacityRejected = engine.execute(
                twoUnitOutput, insufficientCapacity);
        assert capacityRejected.outcome()
                == TransactionAuditResult.Outcome.REJECTED;
        assert capacityRejected.reason().contains("capacity is insufficient");
        assert insufficientCapacity.calls.equals(List.of(TransactionStage.VALIDATE));

        FakeParticipant rollbackFailure = new FakeParticipant();
        rollbackFailure.failAt = TransactionStage.PRODUCE;
        rollbackFailure.rollbackFails = true;
        TransactionAuditResult rollbackFailed = engine.execute(
                request(31), rollbackFailure);
        assert rollbackFailed.outcome()
                == TransactionAuditResult.Outcome.ROLLBACK_FAILED;
        assert rollbackFailed.reason().contains("rollback=rollback-failed");
        FakeParticipant retryAfterRollbackFailure = new FakeParticipant();
        TransactionAuditResult rollbackReplay = engine.execute(
                request(31), retryAfterRollbackFailure);
        assert rollbackReplay.replayed();
        assert rollbackReplay.outcome()
                == TransactionAuditResult.Outcome.ROLLBACK_FAILED;
        assert retryAfterRollbackFailure.calls.isEmpty();
        int rollbackCallsBeforeRestart = rollbackFailure.calls.size();
        TransactionEngine rollbackRestart = new TransactionEngine(1, 4, CLOCK);
        assert rollbackRestart.execute(request(31), rollbackFailure).replayed();
        assert rollbackFailure.calls.size() == rollbackCallsBeforeRestart;
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

        TransactionEngine inputEngine = new TransactionEngine(2, 4, CLOCK);
        FakeParticipant firstInput = new FakeParticipant();
        firstInput.blockAt = TransactionStage.VALIDATE;
        var inputExecutor = Executors.newSingleThreadExecutor();
        try {
            TransactionRequest first = requestWithInput(
                    42, "projects:item/shared-input");
            var future = inputExecutor.submit(
                    () -> inputEngine.execute(first, firstInput));
            assert firstInput.entered.await(5, TimeUnit.SECONDS);
            FakeParticipant competitor = new FakeParticipant();
            TransactionAuditResult conflict = inputEngine.execute(
                    requestWithInput(43, "projects:item/shared-input"), competitor);
            assert conflict.outcome()
                    == TransactionAuditResult.Outcome.INPUT_CONFLICT;
            assert competitor.calls.isEmpty();
            firstInput.release.countDown();
            assert future.get(5, TimeUnit.SECONDS).outcome()
                    == TransactionAuditResult.Outcome.COMMITTED;
        } finally {
            firstInput.release.countDown();
            inputExecutor.shutdownNow();
        }

        TransactionEngine terminalSlotEngine = new TransactionEngine(2, 1, CLOCK);
        FakeParticipant terminalBlocking = new FakeParticipant();
        terminalBlocking.blockAt = TransactionStage.VALIDATE;
        var terminalExecutor = Executors.newSingleThreadExecutor();
        try {
            var first = terminalExecutor.submit(() -> terminalSlotEngine.execute(
                    request(44), terminalBlocking));
            assert terminalBlocking.entered.await(5, TimeUnit.SECONDS);
            FakeParticipant noReservedTerminalSlot = new FakeParticipant();
            TransactionAuditResult limited = terminalSlotEngine.execute(
                    request(45), noReservedTerminalSlot);
            assert limited.outcome()
                    == TransactionAuditResult.Outcome.TERMINAL_LIMIT;
            assert noReservedTerminalSlot.calls.isEmpty();
            terminalBlocking.release.countDown();
            assert first.get(5, TimeUnit.SECONDS).outcome()
                    == TransactionAuditResult.Outcome.COMMITTED;
        } finally {
            terminalBlocking.release.countDown();
            terminalExecutor.shutdownNow();
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
            var closeExecutor = Executors.newSingleThreadExecutor();
            var closeFuture = closeExecutor.submit(() -> {
                stopEngine.close();
                return null;
            });
            Thread.sleep(25);
            assert !closeFuture.isDone() : "close must drain active rollback";
            stopParticipant.release.countDown();
            closeFuture.get(5, TimeUnit.SECONDS);
            closeExecutor.shutdownNow();
            stopEngine.close();
            assert stopFuture.get(5, TimeUnit.SECONDS).outcome()
                    == TransactionAuditResult.Outcome.ROLLED_BACK;
            assert stopParticipant.rollbackCount == 1;
            assert stopEngine.activeCount() == 0;
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

        TransactionEngine failedRecoveryEngine = new TransactionEngine(1, 2, CLOCK);
        FakeParticipant failedRecoveryParticipant = new FakeParticipant();
        failedRecoveryParticipant.rollbackFails = true;
        TransactionRecoveryRecord failedRecoveryRecord =
                new TransactionRecoveryRecord(
                        request(55), new ReservationToken("failed-recovery"),
                        TransactionStage.PERSIST,
                        Optional.of(failedRecoveryParticipant.output));
        TransactionAuditResult failedRecovery = failedRecoveryEngine.recover(
                failedRecoveryRecord, failedRecoveryParticipant);
        assert failedRecovery.outcome()
                == TransactionAuditResult.Outcome.ROLLBACK_FAILED;
        assert failedRecovery.reason().contains("recovery-rollback=rollback-failed");
        assert failedRecoveryEngine.recover(
                failedRecoveryRecord, failedRecoveryParticipant).replayed();
        assert failedRecoveryParticipant.rollbackCount == 1;

        TransactionEngine concurrentRecoveryEngine =
                new TransactionEngine(2, 4, CLOCK);
        FakeParticipant concurrentRecovery = new FakeParticipant();
        concurrentRecovery.blockRollback = true;
        TransactionRecoveryRecord concurrentRecord = new TransactionRecoveryRecord(
                request(54), new ReservationToken("concurrent-recovery"),
                TransactionStage.PERSIST, Optional.of(concurrentRecovery.output));
        var recoveryExecutor = Executors.newSingleThreadExecutor();
        try {
            var firstRecovery = recoveryExecutor.submit(() ->
                    concurrentRecoveryEngine.recover(
                            concurrentRecord, concurrentRecovery));
            assert concurrentRecovery.rollbackEntered.await(5, TimeUnit.SECONDS);
            TransactionAuditResult duplicateRecovery =
                    concurrentRecoveryEngine.recover(
                            concurrentRecord, concurrentRecovery);
            assert duplicateRecovery.outcome()
                    == TransactionAuditResult.Outcome.DUPLICATE_ACTIVE;
            concurrentRecovery.rollbackRelease.countDown();
            assert firstRecovery.get(5, TimeUnit.SECONDS).outcome()
                    == TransactionAuditResult.Outcome.ROLLED_BACK;
            assert concurrentRecovery.rollbackCount == 1;
        } finally {
            concurrentRecovery.rollbackRelease.countDown();
            recoveryExecutor.shutdownNow();
        }
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
        assert node.snapshot().state() == GatheringNode.State.CLOSED;
        assert node.snapshot().reservation().isEmpty();
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
        return requestWithInput(seed, "projects:item/test-" + seed);
    }

    private static TransactionRequest requestWithInput(int seed, String inputId) {
        return new TransactionRequest(
                uuid(seed), uuid(1_000 + seed),
                "projects:operation/craft", "projects:craft/test-" + seed, seed,
                1,
                List.of(new TransactionRequest.InputRevision(inputId, seed)));
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
        private final CountDownLatch rollbackEntered = new CountDownLatch(1);
        private final CountDownLatch rollbackRelease = new CountDownLatch(1);
        private final ConcurrentHashMap<UUID, TransactionAuditResult> terminals =
                new ConcurrentHashMap<>();
        private TransactionParticipant.Validation validation =
                TransactionParticipant.Validation.allow(
                        InventoryCapacityProposal.reservedInventory(1));
        private TransactionStage failAt;
        private TransactionStage blockAt;
        private int rollbackCount;
        private boolean rollbackFails;
        private boolean blockRollback;
        private boolean commitThrowsAfterRecord;
        private InventoryCapacityProposal reservedCapacity;

        @Override
        public Optional<TransactionAuditResult> findTerminal(
                TransactionRequest request
        ) {
            return Optional.ofNullable(terminals.get(request.requestId()));
        }

        @Override
        public Validation validate(TransactionRequest request) {
            stage(TransactionStage.VALIDATE);
            return validation;
        }

        @Override
        public ReservationToken reserve(
                TransactionRequest request,
                InventoryCapacityProposal capacityProposal
        ) {
            stage(TransactionStage.RESERVE);
            reservedCapacity = capacityProposal;
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
        public TransactionAuditResult commit(
                TransactionRequest request,
                ReservationToken token,
                OutputProposal output,
                TransactionAuditResult proposedCommittedResult
        ) {
            stage(TransactionStage.COMMIT);
            terminals.put(request.requestId(), proposedCommittedResult);
            if (commitThrowsAfterRecord) {
                throw new IllegalStateException("post-commit-failure");
            }
            return proposedCommittedResult;
        }

        @Override
        public void recordTerminal(TransactionAuditResult terminalResult) {
            terminals.putIfAbsent(terminalResult.requestId(), terminalResult);
        }

        @Override
        public void rollback(
                TransactionRequest request,
                ReservationToken token,
                TransactionStage lastCompletedStage,
                OutputProposal output
        ) {
            rollbackCount++;
            if (blockRollback) {
                rollbackEntered.countDown();
                try {
                    if (!rollbackRelease.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("rollback-timeout");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "rollback-interrupted", exception);
                }
            }
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
