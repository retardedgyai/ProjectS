package io.github.gyai.projects.beta.activation.track3;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic craft failure coverage: no UUID/MOD retry or leaked equipment. */
public final class Track3CraftFailureSafetyTest {
    private Track3CraftFailureSafetyTest() { }
    public static void runAll() {
        for (StagingFailurePoint point : StagingFailurePoint.values()) {
            if (point != StagingFailurePoint.NONE) injectedFailureIsStable(point);
        }
        finalizedAuditFailureDoesNotLeakIdentityOrEquipment();
        terminalAuditFailureIsUncertainAndNeverReplayedAsSuccess();
    }
    private static void injectedFailureIsStable(StagingFailurePoint point) {
        UUID player = new UUID(0, 800 + point.ordinal());
        UUID request = new UUID(0, 900 + point.ordinal());
        AtomicInteger rng = new AtomicInteger(), ids = new AtomicInteger();
        BoundedStagingInventory inventory = new BoundedStagingInventory(); inventory.openSession(player);
        inventory.seedResource(player, StagingEconomyCatalog.IRON_INGOT, 3);
        BoundedStagingOperationJournal journal = new BoundedStagingOperationJournal(64);
        StagingInventoryTransactionAdapter adapter = new StagingInventoryTransactionAdapter(inventory, journal,
                Track3TestFixtures.CLOCK, () -> new UUID(0, ids.incrementAndGet()),
                new StagingModRollService(StagingModRollService.defaultCandidates(), () -> { rng.incrementAndGet(); return .25; }));
        StagingEconomyService service = new StagingEconomyService(inventory, journal, adapter, new StagingEnhancementOutcomeRegistry());
        service.setGroupRunning(StagingEconomyService.OperationGroup.GATHERING_CRAFTING, true);
        try {
            var result = service.execute(StagingEconomyOperationPort.OperationRequest.action(request,
                    Track3TestFixtures.access(player), StagingEconomyOperationPort.OperationKind.CRAFT), point);
            check(result.status() != StagingEconomyOperationPort.Status.COMMITTED, point + " committed");
            check(ids.get() <= 1 && rng.get() <= 2, point + " rerolled identity/MOD");
            check(inventory.snapshot(player).equipment().isEmpty(), point + " leaked blade");
            if (result.status() != StagingEconomyOperationPort.Status.COMMIT_UNCERTAIN) {
                check(inventory.snapshot(player).resources().getOrDefault(StagingEconomyCatalog.IRON_INGOT, 0L) == 3L,
                        point + " did not restore ingots");
            }
            if (rng.get() > 0 || ids.get() > 0) {
                int beforeRng = rng.get(), beforeIds = ids.get();
                service.execute(StagingEconomyOperationPort.OperationRequest.action(request,
                        Track3TestFixtures.access(player), StagingEconomyOperationPort.OperationKind.CRAFT));
                check(rng.get() == beforeRng && ids.get() == beforeIds, point + " replay/retry rerolled");
            }
        } finally { service.close(); }
    }
    private static void finalizedAuditFailureDoesNotLeakIdentityOrEquipment() {
        UUID player = new UUID(0, 990), request = new UUID(0, 991); AtomicInteger ids = new AtomicInteger();
        BoundedStagingInventory inventory = new BoundedStagingInventory(); inventory.openSession(player);
        inventory.seedResource(player, StagingEconomyCatalog.IRON_INGOT, 3);
        StagingTransactionAuditSink failing = new StagingTransactionAuditSink() {
            public void resolved(io.github.gyai.projects.equipment.operation.EquipmentMutationProposal proposal) { }
            public void terminal(io.github.gyai.projects.transaction.TransactionAuditResult result) { }
            public void finalized(UUID id, io.github.gyai.projects.equipment.EquipmentItemV1 item) { throw new IllegalStateException("terminal audit failure"); }
        };
        BoundedStagingOperationJournal journal = new BoundedStagingOperationJournal(64, failing);
        StagingEconomyService service = new StagingEconomyService(inventory, journal,
                new StagingInventoryTransactionAdapter(inventory, journal, Track3TestFixtures.CLOCK,
                        () -> new UUID(0, ids.incrementAndGet()), new StagingModRollService(StagingModRollService.defaultCandidates(), () -> .5)),
                new StagingEnhancementOutcomeRegistry());
        service.setGroupRunning(StagingEconomyService.OperationGroup.GATHERING_CRAFTING, true);
        try {
            var result = service.execute(StagingEconomyOperationPort.OperationRequest.action(request, Track3TestFixtures.access(player), StagingEconomyOperationPort.OperationKind.CRAFT));
            check(result.status() != StagingEconomyOperationPort.Status.COMMITTED && ids.get() == 1, "finalize failure identity behavior");
            check(inventory.snapshot(player).equipment().isEmpty() && journal.finalizedEquipment(request).isEmpty(), "finalize failure leaked output");
        } finally { service.close(); }
    }
    private static void terminalAuditFailureIsUncertainAndNeverReplayedAsSuccess() {
        UUID player = new UUID(0, 995), request = new UUID(0, 996); AtomicInteger ids = new AtomicInteger();
        BoundedStagingInventory inventory = new BoundedStagingInventory(); inventory.openSession(player);
        inventory.seedResource(player, StagingEconomyCatalog.IRON_INGOT, 3);
        StagingTransactionAuditSink failing = new StagingTransactionAuditSink() {
            public void resolved(io.github.gyai.projects.equipment.operation.EquipmentMutationProposal proposal) { }
            public void finalized(UUID id, io.github.gyai.projects.equipment.EquipmentItemV1 item) { }
            public void terminal(io.github.gyai.projects.transaction.TransactionAuditResult result) { throw new IllegalStateException("terminal sink unavailable"); }
        };
        BoundedStagingOperationJournal journal = new BoundedStagingOperationJournal(64, failing);
        StagingEconomyService service = new StagingEconomyService(inventory, journal,
                new StagingInventoryTransactionAdapter(inventory, journal, Track3TestFixtures.CLOCK,
                        () -> new UUID(0, ids.incrementAndGet()), new StagingModRollService(StagingModRollService.defaultCandidates(), () -> .5)),
                new StagingEnhancementOutcomeRegistry());
        service.setGroupRunning(StagingEconomyService.OperationGroup.GATHERING_CRAFTING, true);
        try {
            var first = service.execute(StagingEconomyOperationPort.OperationRequest.action(request, Track3TestFixtures.access(player), StagingEconomyOperationPort.OperationKind.CRAFT));
            check(first.status() == StagingEconomyOperationPort.Status.COMMIT_UNCERTAIN, "terminal sink failure was ambiguous success: " + first);
            var intended = journal.finalizedEquipment(request).orElseThrow();
            check(ids.get() == 1 && inventory.snapshot(player).equipment().size() == 1, "uncertain craft identity/exposure incorrect");
            var retry = service.execute(StagingEconomyOperationPort.OperationRequest.action(request, Track3TestFixtures.access(player), StagingEconomyOperationPort.OperationKind.CRAFT));
            check(retry.status() != StagingEconomyOperationPort.Status.COMMITTED
                            && retry.status() != StagingEconomyOperationPort.Status.REPLAYED && ids.get() == 1,
                    "uncertain retry became success or allocated identity: " + retry);
            check(journal.finalizedEquipment(request).orElseThrow().equals(intended) && inventory.snapshot(player).equipment().size() == 1,
                    "uncertain retry rerolled or duplicated blade");
        } finally { service.close(); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
