package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.operation.EquipmentMutationProposal;
import io.github.gyai.projects.transaction.TransactionAuditResult;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.transaction.TransactionStage;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleContext;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleId;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.feature.FeatureFlagSnapshot;
import io.github.gyai.projects.feature.FeatureKey;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Durable finalized output is replayable only when committed; uncertainty is quarantined. */
public final class Track3FinalizedOutputRecoveryTest {
    private Track3FinalizedOutputRecoveryTest() { }
    public static void runAll() { committedFinalizedOutputReplaysWithoutExposure(); uncertainOutputIsQuarantinedExactly(); rolledBackFinalizedOutputIsNotRestored(); resourceIntentBlocksCrashAfterLiveCommitBeforeTerminal(); }
    private static void committedFinalizedOutputReplaysWithoutExposure() {
        Path root = temp(); UUID player = new UUID(0, 1001), request = new UUID(0, 1002);
        try {
            StagingEconomyPaths paths = StagingEconomyPaths.under(root.resolve("ProjectS"));
            StagingTransactionJournalRepository repository = new StagingTransactionJournalRepository(paths.transactionsDirectory());
            FileStagingTransactionAuditSink sink = new FileStagingTransactionAuditSink(paths, repository);
            BoundedStagingInventory inventory = new BoundedStagingInventory(); inventory.openSession(player); inventory.seedResource(player, StagingEconomyCatalog.IRON_INGOT, 3);
            BoundedStagingOperationJournal journal = new BoundedStagingOperationJournal(64, sink);
            StagingEconomyService service = service(inventory, journal, () -> new UUID(0, 1003));
            EquipmentItemV1 item = service.execute(StagingEconomyOperationPort.OperationRequest.action(request, Track3TestFixtures.access(player), StagingEconomyOperationPort.OperationKind.CRAFT)).equipment().orElseThrow();
            service.close();
            BoundedStagingOperationJournal restored = new BoundedStagingOperationJournal(64);
            try (StagingTransactionRecoveryService recovery = new StagingTransactionRecoveryService(repository, restored)) {
                check(recovery.recover().terminalReplayed() == 1, "committed terminal not restored");
                BoundedStagingInventory fresh = new BoundedStagingInventory();
                StagingEconomyService replayService = service(fresh, restored, () -> new UUID(0, 1999));
                var replay = replayService.execute(StagingEconomyOperationPort.OperationRequest.action(request, Track3TestFixtures.access(player), StagingEconomyOperationPort.OperationKind.CRAFT));
                check(replay.status() == StagingEconomyOperationPort.Status.REPLAYED && replay.equipment().orElseThrow().equals(item), "committed output did not replay exactly");
                check(fresh.snapshot(player).equipment().isEmpty(), "replay exposed duplicate item"); replayService.close();
            }
        } finally { delete(root); }
    }
    private static void uncertainOutputIsQuarantinedExactly() {
        Path root = temp(); UUID player = new UUID(0, 1010), request = new UUID(0, 1011);
        try {
            StagingEconomyPaths paths = StagingEconomyPaths.under(root.resolve("ProjectS"));
            StagingTransactionJournalRepository repository = new StagingTransactionJournalRepository(paths.transactionsDirectory());
            EquipmentItemV1 item = finalizedItem(player, request);
            String retained = "equipment:" + Base64.getUrlEncoder().withoutPadding().encodeToString(new StagingEquipmentCodec().encode(item, 0).payload());
            repository.save(new StagingTransactionJournalRepository.Entry(request, StagingTransactionJournalRepository.Stage.COMMIT_UNCERTAIN,
                    player, "projects:staging-craft", List.of("projects:resource-iron-ingot@0"), StagingTransactionJournalRepository.ReservationState.UNKNOWN,
                    retained, StagingTransactionJournalRepository.TerminalOutcome.COMMIT_UNCERTAIN, StagingEconomyCatalog.CRAFT_RECIPE_ID, 0, 1,
                    List.of("VALIDATE", "RESERVE", "CONSUME", "PRODUCE", "PERSIST"), true, "ack lost", 1));
            try (StagingTransactionRecoveryService recovery = new StagingTransactionRecoveryService(repository, new BoundedStagingOperationJournal(64))) {
                var result = recovery.recover(); check(result.quarantined() == 1 && recovery.blocked(request, player + ":projects:staging-craft"), "uncertain request not blocked");
                try (var files = Files.list(paths.transactionsDirectory().resolve("quarantine"))) {
                    Path file = files.findFirst().orElseThrow(() -> new AssertionError("uncertain durable payload was not quarantined"));
                    String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(retained.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    check(Files.readString(file).contains(encoded), "quarantine lost finalized equipment payload");
                    byte[] payload = Base64.getUrlDecoder().decode(retained.substring("equipment:".length()));
                    check(new StagingEquipmentCodec().decode(payload).item().equals(item), "quarantine payload UUID/MOD changed");
                }
            }
        } catch (Exception failure) { throw new AssertionError(failure); } finally { delete(root); }
    }
    private static void rolledBackFinalizedOutputIsNotRestored() {
        Path root = temp(); UUID player = new UUID(0, 1020), request = new UUID(0, 1021), legacyRequest = new UUID(0, 1022);
        try {
            StagingEconomyPaths paths = StagingEconomyPaths.under(root.resolve("ProjectS"));
            StagingTransactionJournalRepository repository = new StagingTransactionJournalRepository(paths.transactionsDirectory());
            FileStagingTransactionAuditSink sink = new FileStagingTransactionAuditSink(paths, repository);
            EquipmentItemV1 item = finalizedItem(player, request);
            check(item.instanceId().orElseThrow().equals(new UUID(0, 1012)), "fixture UUID changed");
            List<TransactionRequest.InputRevision> inputs = List.of(
                    new TransactionRequest.InputRevision("projects:resource-iron-ingot", 0));
            sink.resolved(new EquipmentMutationProposal(request, player, "projects:staging-craft",
                    StagingEconomyCatalog.CRAFT_RECIPE_ID, StagingEconomyCatalog.TEST_BLADE_FAMILY,
                    0, item, null, null, inputs));
            sink.finalized(request, item);
            TransactionAuditResult rolledBackAudit = new TransactionAuditResult(request, player,
                    "projects:staging-craft", StagingEconomyCatalog.CRAFT_RECIPE_ID, 0, 1,
                    inputs, TransactionAuditResult.Outcome.ROLLED_BACK,
                    List.of(TransactionStage.VALIDATE, TransactionStage.RESERVE,
                            TransactionStage.CONSUME, TransactionStage.PRODUCE,
                            TransactionStage.PERSIST), java.util.Optional.empty(), "rolled back",
                    false, Instant.parse("2026-08-05T00:00:00Z"));
            sink.terminal(rolledBackAudit);
            StagingTransactionJournalRepository.Entry durable = repository.load(request).orElseThrow();
            check(durable.proposedOutputIdentity().isBlank()
                    && !durable.proposedOutputIdentity().startsWith("equipment:"),
                    "rolled-back durable entry retained equipment");
            check(durable.stage() == StagingTransactionJournalRepository.Stage.ROLLED_BACK
                    && durable.terminalOutcome() == StagingTransactionJournalRepository.TerminalOutcome.ROLLED_BACK,
                    "rolled-back durable terminal state changed");
            String legacyEquipment = "equipment:" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(new StagingEquipmentCodec().encode(item, 0).payload());
            repository.save(new StagingTransactionJournalRepository.Entry(legacyRequest,
                    StagingTransactionJournalRepository.Stage.ROLLED_BACK, player,
                    "projects:staging-craft", inputs.stream().map(input -> input.inputId()
                    + "@" + input.revision()).toList(),
                    StagingTransactionJournalRepository.ReservationState.RELEASED, legacyEquipment,
                    StagingTransactionJournalRepository.TerminalOutcome.ROLLED_BACK,
                    StagingEconomyCatalog.CRAFT_RECIPE_ID, 0, 1,
                    List.of("VALIDATE", "RESERVE", "CONSUME", "PRODUCE", "PERSIST"),
                    true, "legacy rolled back", 1));
            BoundedStagingOperationJournal restored = new BoundedStagingOperationJournal(64);
            try (StagingTransactionRecoveryService recovery = new StagingTransactionRecoveryService(repository, restored)) {
                check(recovery.recover().terminalReplayed() == 2, "rolled-back terminals not restored");
                check(restored.finalizedEquipment(request).isEmpty(), "rolled-back output restored");
                check(restored.finalizedEquipment(legacyRequest).isEmpty(),
                        "legacy rolled-back output restored");
                check(restored.findTerminal(request).orElseThrow().outcome()
                        == TransactionAuditResult.Outcome.ROLLED_BACK, "wrong rolled-back replay outcome");
                BoundedStagingInventory fresh = new BoundedStagingInventory();
                fresh.openSession(player); fresh.seedResource(player, StagingEconomyCatalog.IRON_INGOT, 3);
                AtomicInteger generated = new AtomicInteger();
                StagingEconomyService replayService = service(fresh, restored, () -> {
                    generated.incrementAndGet(); return new UUID(0, 1999);
                });
                try {
                    var replay = replayService.execute(StagingEconomyOperationPort.OperationRequest.action(
                            request, Track3TestFixtures.access(player), StagingEconomyOperationPort.OperationKind.CRAFT));
                    check(replay.status() == StagingEconomyOperationPort.Status.ROLLED_BACK
                            && replay.equipment().isEmpty(), "rolled-back request replayed equipment");
                    var legacyReplay = replayService.execute(StagingEconomyOperationPort.OperationRequest.action(
                            legacyRequest, Track3TestFixtures.access(player), StagingEconomyOperationPort.OperationKind.CRAFT));
                    check(legacyReplay.status() == StagingEconomyOperationPort.Status.ROLLED_BACK
                            && legacyReplay.equipment().isEmpty(),
                            "legacy rolled-back request replayed equipment");
                    check(generated.get() == 0 && fresh.snapshot(player).equipment().isEmpty()
                            && fresh.snapshot(player).resources().get(StagingEconomyCatalog.IRON_INGOT) == 3,
                            "rolled-back replay generated UUID or mutated output");
                } finally { replayService.close(); }
            }
        } finally { delete(root); }
    }
    /**
     * The physical resource mutation has succeeded, but the process loses the
     * terminal write. The durable RESERVED intent must stop startup from
     * treating the request as safe to execute again.
     */
    private static void resourceIntentBlocksCrashAfterLiveCommitBeforeTerminal() {
        Path root = temp(); UUID player = new UUID(0, 1030), request = new UUID(0, 1031);
        try {
            StagingEconomyPaths paths = StagingEconomyPaths.under(root.resolve("ProjectS"));
            StagingTransactionJournalRepository repository = new StagingTransactionJournalRepository(paths.transactionsDirectory());
            FileStagingTransactionAuditSink durable = new FileStagingTransactionAuditSink(paths, repository);
            StagingTransactionAuditSink crashAfterCommit = new StagingTransactionAuditSink() {
                @Override public void resolved(EquipmentMutationProposal proposal) { durable.resolved(proposal); }
                @Override public void resourceIntent(TransactionRequest transaction, io.github.gyai.projects.crafting.OutputProposal output) { durable.resourceIntent(transaction, output); }
                @Override public void terminal(TransactionAuditResult result) { throw new IllegalStateException("simulated process crash before terminal persistence"); }
            };
            BoundedStagingInventory inventory = new BoundedStagingInventory(); inventory.openSession(player);
            inventory.seedResource(player, StagingEconomyCatalog.IRON_ORE, 2);
            BoundedStagingOperationJournal journal = new BoundedStagingOperationJournal(64, crashAfterCommit);
            StagingEconomyService service = service(inventory, journal, () -> new UUID(0, 1032));
            var result = service.execute(StagingEconomyOperationPort.OperationRequest.action(request,
                    Track3TestFixtures.access(player), StagingEconomyOperationPort.OperationKind.REFINE));
            check(result.status() == StagingEconomyOperationPort.Status.COMMIT_UNCERTAIN,
                    "lost terminal acknowledgement became success");
            check(inventory.snapshot(player).resources().getOrDefault(StagingEconomyCatalog.IRON_ORE, 0L) == 0
                    && inventory.snapshot(player).resources().getOrDefault(StagingEconomyCatalog.IRON_INGOT, 0L) == 1,
                    "fixture did not reach the post-live-commit state");
            StagingTransactionJournalRepository.Entry intent = repository.load(request).orElseThrow();
            check(intent.stage() == StagingTransactionJournalRepository.Stage.RESERVED
                    && intent.reservationState() == StagingTransactionJournalRepository.ReservationState.HELD
                    && intent.proposedOutputIdentity().equals(StagingEconomyCatalog.IRON_INGOT + ":1")
                    && intent.inputIdentities().equals(List.of("projects:resource-iron-ore@0")),
                    "pre-terminal resource intent is incomplete");
            service.close();

            BoundedStagingOperationJournal restored = new BoundedStagingOperationJournal(64);
            try (StagingTransactionRecoveryService recovery = new StagingTransactionRecoveryService(repository, restored)) {
                var recovered = recovery.recover();
                check(recovered.recoveryRequired() == 1
                        && recovery.blocked(request, player + ":projects:staging-resource"),
                        "post-commit resource intent was not retained as blocking");
                BoundedStagingInventory restartInventory = new BoundedStagingInventory();
                restartInventory.openSession(player);
                restartInventory.seedResource(player, StagingEconomyCatalog.IRON_INGOT, 1);
                StagingEconomyService restart = new StagingEconomyService(restartInventory, restored,
                        new StagingInventoryTransactionAdapter(restartInventory, restored,
                                Track3TestFixtures.CLOCK, () -> new UUID(0, 1033)),
                        new StagingEnhancementOutcomeRegistry());
                try {
                    Track3RuntimeModule module = new Track3RuntimeModule(
                            BetaRuntimeModuleId.GATHERING_CRAFTING,
                            StagingEconomyService.OperationGroup.GATHERING_CRAFTING,
                            restart, recovery);
                    check(!module.prepare(new BetaRuntimeModuleContext(
                                    Track3TestFixtures.access(player).activationPolicy(),
                                    FeatureFlagSnapshot.of(java.util.Map.of(
                                            FeatureKey.GATHERING, true,
                                            FeatureKey.REFINING, true,
                                            FeatureKey.CRAFTING, true)),
                                    java.util.Set.of("track3.staging-inventory",
                                            "track3.staging-transaction-journal"),
                                    Track3TestFixtures.CLOCK, false)).success()
                                    && module.state() == BetaRuntimeModuleState.BLOCKED,
                            "recovery allowed the resource module to restart");
                    var retry = restart.execute(StagingEconomyOperationPort.OperationRequest.action(request,
                            Track3TestFixtures.access(player), StagingEconomyOperationPort.OperationKind.REFINE));
                    check(retry.status() == StagingEconomyOperationPort.Status.REJECTED
                                    && restartInventory.snapshot(player).resources().getOrDefault(
                                    StagingEconomyCatalog.IRON_INGOT, 0L) == 1,
                            "recovered ambiguous request re-executed live resource output");
                } finally { restart.close(); }
            }
        } finally { delete(root); }
    }
    private static EquipmentItemV1 finalizedItem(UUID player, UUID request) {
        BoundedStagingInventory inventory = new BoundedStagingInventory(); inventory.openSession(player); inventory.seedResource(player, StagingEconomyCatalog.IRON_INGOT, 3);
        BoundedStagingOperationJournal journal = new BoundedStagingOperationJournal(64); StagingEconomyService service = service(inventory, journal, () -> new UUID(0, 1012));
        try { return service.execute(StagingEconomyOperationPort.OperationRequest.action(request, Track3TestFixtures.access(player), StagingEconomyOperationPort.OperationKind.CRAFT)).equipment().orElseThrow(); } finally { service.close(); }
    }
    private static StagingEconomyService service(BoundedStagingInventory inventory, BoundedStagingOperationJournal journal, java.util.function.Supplier<UUID> ids) {
        StagingEconomyService service = new StagingEconomyService(inventory, journal, new StagingInventoryTransactionAdapter(inventory, journal, Track3TestFixtures.CLOCK, ids), new StagingEnhancementOutcomeRegistry()); service.setGroupRunning(StagingEconomyService.OperationGroup.GATHERING_CRAFTING, true); return service;
    }
    private static Path temp() { try { return Files.createTempDirectory("projects-finalized-"); } catch (Exception e) { throw new AssertionError(e); } }
    private static void delete(Path root) { if (root == null) return; try (var entries = Files.walk(root)) { entries.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception failure) { throw new RuntimeException(failure); } }); } catch (Exception failure) { throw new AssertionError(failure); } }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
