package io.github.gyai.projects.dev;

import io.github.gyai.projects.beta.activation.track3.StagingEconomyCatalog;
import io.github.gyai.projects.beta.activation.track3.StagingEconomyOperationPort;
import io.github.gyai.projects.beta.activation.track3.StagingInventoryPort;
import io.github.gyai.projects.beta.activation.track3.StagingOperationAccess;
import io.github.gyai.projects.beta.activation.track3.BoundedStagingInventory;
import io.github.gyai.projects.beta.activation.track3.BoundedStagingOperationJournal;
import io.github.gyai.projects.beta.activation.track3.StagingEconomyService;
import io.github.gyai.projects.beta.activation.track3.StagingEnhancementOutcomeRegistry;
import io.github.gyai.projects.beta.activation.track3.StagingInventoryTransactionAdapter;
import io.github.gyai.projects.equipment.EquipmentItemV1;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Holder-only staging state survives an intentional refresh and is erased on close. */
public final class DevMenuStagingHolderStateTest {
    private DevMenuStagingHolderStateTest() { }
    public static void runAll() {
        UUID player = new UUID(0, 1100), firstId = new UUID(0, 1101), secondId = new UUID(0, 1102);
        EquipmentItemV1 first = identified(firstId), second = identified(secondId);
        StagingWorkbenchPresenter presenter = new StagingWorkbenchPresenter(new FakePort(List.of(first, second)), () -> true, () -> true, () -> true);
        StagingOperationAccess access = new StagingOperationAccess(player, "staging_world", true,
                new io.github.gyai.projects.beta.activation.BetaActivationPolicy(
                        io.github.gyai.projects.beta.activation.BetaActivationAudience.ALLOWLIST,
                        io.github.gyai.projects.beta.activation.BetaActivationTargetScope.TRAINING_DUMMY_ONLY,
                        io.github.gyai.projects.beta.activation.BetaMutationPolicy.STAGING_WRITE,
                        java.util.Set.of(player), java.util.Set.of("staging_world"), true, false));
        DevMenuHolder holder = new DevMenuHolder(DevMenuManager.Page.STAGING, 0);
        check(presenter.inspect(access, Optional.ofNullable(holder.selectedEquipment())).contains(firstId.toString()), "initial inspect did not use first item");
        holder.selectEquipment(secondId);
        DevMenuHolder refreshed = new DevMenuHolder(DevMenuManager.Page.STAGING, 0);
        refreshed.selectEquipment(holder.selectedEquipment());
        check(presenter.inspect(access, Optional.ofNullable(refreshed.selectedEquipment())).contains(secondId.toString()), "refresh lost second selection");
        UUID request = refreshed.requestId("craft"); check(request.equals(refreshed.requestId("craft")), "request was not stable");
        check(refreshed.begin("craft") && !refreshed.begin("craft"), "in-flight did not reject duplicate");
        refreshed.clearStagingState();
        check(refreshed.selectedEquipment() == null && refreshed.begin("craft"), "close did not clear selection/in-flight");
        check(!request.equals(refreshed.requestId("craft")), "close did not clear request IDs");
        everyOutcomeLabelSurvivesStagingRefresh();
        holderLifecycleRotatesSafeRequestsAndRetainsUncertainty();
        finalNavigationRefreshIsPageAware();
        quitCleanupClearsOpenStagingStateAndLogsOutIdempotently();
        workbenchRegistrationIsOwnedAndSupersessionSafe();
    }

    private static void everyOutcomeLabelSurvivesStagingRefresh() {
        for (StagingEconomyOperationPort.Status status : StagingEconomyOperationPort.Status.values()) {
            DevMenuHolder current = new DevMenuHolder(DevMenuManager.Page.STAGING, 0);
            String exact = DevMenuManager.operationOutcome(new StagingEconomyOperationPort.OperationResult(
                    status, "precise-detail", Optional.empty(), Optional.empty()));
            current.stagingOutcome(exact);
            DevMenuHolder refreshed = new DevMenuHolder(DevMenuManager.Page.STAGING, 0);
            refreshed.copyStagingStateFrom(current);
            check(exact.equals(refreshed.stagingOutcome())
                            && exact.equals(status.name() + ": precise-detail"),
                    "staging refresh lost exact " + status + " outcome text");
            refreshed.clearStagingState();
            check(refreshed.stagingOutcome().isBlank(), "close retained stale " + status + " outcome");
        }
    }

    /** Exercises the same holder request lifecycle used by the GUI action callback. */
    private static void holderLifecycleRotatesSafeRequestsAndRetainsUncertainty() {
        UUID player = new UUID(0, 1200);
        StagingOperationAccess access = access(player);
        BoundedStagingInventory inventory = new BoundedStagingInventory();
        BoundedStagingOperationJournal journal = new BoundedStagingOperationJournal(32);
        StagingEconomyService service = new StagingEconomyService(inventory, journal,
                new StagingInventoryTransactionAdapter(inventory, journal,
                        java.time.Clock.systemUTC(), () -> new UUID(0, 1201)),
                new StagingEnhancementOutcomeRegistry());
        service.setGroupRunning(StagingEconomyService.OperationGroup.GATHERING_CRAFTING, true);
        StagingWorkbenchPresenter presenter = new StagingWorkbenchPresenter(
                service, () -> true, () -> true, () -> true);
        try {
            DevMenuHolder holder = new DevMenuHolder(DevMenuManager.Page.STAGING, 0);
            dispatch(holder, presenter, access, "give", StagingEconomyOperationPort.OperationKind.GIVE,
                    Optional.of(StagingEconomyCatalog.IRON_ORE), 10);
            java.util.HashSet<UUID> refineIds = new java.util.HashSet<>();
            for (int index = 0; index < 3; index++) {
                UUID id = dispatch(holder, presenter, access, "refine",
                        StagingEconomyOperationPort.OperationKind.REFINE, Optional.empty(), 0);
                refineIds.add(id);
            }
            check(refineIds.size() == 3, "safe refine terminals reused a GUI request ID");
            check(inventory.snapshot(player).resources().getOrDefault(StagingEconomyCatalog.IRON_ORE, 0L) == 4
                            && inventory.snapshot(player).resources().getOrDefault(StagingEconomyCatalog.IRON_INGOT, 0L) == 3,
                    "holder-driven refine sequence did not reach 4 ore / 3 ingots");
            dispatch(holder, presenter, access, "craft", StagingEconomyOperationPort.OperationKind.CRAFT,
                    Optional.empty(), 0);
            check(inventory.snapshot(player).resources().getOrDefault(StagingEconomyCatalog.IRON_INGOT, 0L) == 0
                            && inventory.snapshot(player).equipment().size() == 1,
                    "holder-driven craft did not consume ingots once");

            UUID uncertain = holder.requestId("uncertain");
            check(holder.begin("uncertain") && !holder.begin("uncertain"),
                    "in-flight click debounce allowed a double dispatch");
            holder.finish("uncertain");
            holder.completeRequest("uncertain", StagingEconomyOperationPort.Status.COMMIT_UNCERTAIN);
            check(uncertain.equals(holder.requestId("uncertain")),
                    "commit uncertainty rotated the request ID");
        } finally { service.close(); }
    }

    /** Covers the final navigation-layer action, after page rendering has completed. */
    private static void finalNavigationRefreshIsPageAware() {
        java.util.concurrent.atomic.AtomicInteger staging = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger generic = new java.util.concurrent.atomic.AtomicInteger();
        DevMenuManager.refreshActionFor(DevMenuManager.Page.STAGING,
                (player, click) -> staging.incrementAndGet(),
                (player, click) -> generic.incrementAndGet()).run(null, null);
        check(staging.get() == 1 && generic.get() == 0,
                "final staging slot-49 refresh selected the generic action");
        DevMenuManager.refreshActionFor(DevMenuManager.Page.MAIN,
                (player, click) -> staging.incrementAndGet(),
                (player, click) -> generic.incrementAndGet()).run(null, null);
        check(staging.get() == 1 && generic.get() == 1,
                "non-staging slot-49 refresh stopped using generic action");
    }

    private static void quitCleanupClearsOpenStagingStateAndLogsOutIdempotently() {
        UUID player = new UUID(0, 1300);
        FakePort port = new FakePort(List.of());
        DevMenuHolder holder = new DevMenuHolder(DevMenuManager.Page.STAGING, 0);
        holder.selectEquipment(new UUID(0, 1301));
        UUID request = holder.requestId("refine");
        holder.stagingOutcome("COMMIT_UNCERTAIN: retained until quit");
        check(holder.begin("refine"), "quit fixture failed to arm in-flight state");
        DevMenuManager.clearStagingStateAndLogout(holder, port, player);
        check(holder.selectedEquipment() == null && holder.stagingOutcome().isBlank()
                        && holder.begin("refine") && !request.equals(holder.requestId("refine"))
                        && port.logoutCalls == 1 && player.equals(port.lastLogout),
                "quit did not clear holder state and invoke staging logout");
        DevMenuManager.clearStagingStateAndLogout(holder, port, player);
        check(port.logoutCalls == 2, "close-after-quit cleanup was not safely repeatable");
    }

    private static void workbenchRegistrationIsOwnedAndSupersessionSafe() {
        FakePort port = new FakePort(List.of());
        try {
            AutoCloseable first = DevMenuManager.installStagingWorkbench(port,
                    player -> access(player.getUniqueId()));
            check(workbenchPresent(), "install did not register the workbench");
            first.close();
            check(!workbenchPresent(), "closed root left the workbench registered");

            AutoCloseable oldRoot = DevMenuManager.installStagingWorkbench(port,
                    player -> access(player.getUniqueId()));
            AutoCloseable newRoot = DevMenuManager.installStagingWorkbench(port,
                    player -> access(player.getUniqueId()));
            oldRoot.close();
            check(workbenchPresent(), "closing an old root removed the newer registration");
            newRoot.close();
            check(!workbenchPresent(), "closing the current root did not unregister its workbench");
        } catch (Exception failure) {
            throw new AssertionError("workbench registration lifecycle failed", failure);
        }
    }

    private static boolean workbenchPresent() {
        try {
            var field = DevMenuManager.class.getDeclaredField("stagingWorkbench");
            field.setAccessible(true);
            return field.get(null) != null;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("unable to inspect workbench registration", failure);
        }
    }

    private static UUID dispatch(DevMenuHolder holder, StagingWorkbenchPresenter presenter,
                                 StagingOperationAccess access, String action,
                                 StagingEconomyOperationPort.OperationKind kind,
                                 Optional<String> item, long quantity) {
        check(holder.begin(action), "holder rejected a completed " + action + " click");
        UUID requestId = holder.requestId(action);
        var dispatched = presenter.action(requestId, access, kind, item, quantity);
        holder.finish(action);
        var result = dispatched.result().orElseThrow(() -> new AssertionError(dispatched.denial()));
        check(result.status() == StagingEconomyOperationPort.Status.COMMITTED,
                "holder-dispatched " + action + " failed: " + result);
        holder.completeRequest(action, result.status());
        return requestId;
    }

    private static StagingOperationAccess access(UUID player) {
        return new StagingOperationAccess(player, "staging_world", true,
                new io.github.gyai.projects.beta.activation.BetaActivationPolicy(
                        io.github.gyai.projects.beta.activation.BetaActivationAudience.ALLOWLIST,
                        io.github.gyai.projects.beta.activation.BetaActivationTargetScope.TRAINING_DUMMY_ONLY,
                        io.github.gyai.projects.beta.activation.BetaMutationPolicy.STAGING_WRITE,
                        java.util.Set.of(player), java.util.Set.of("staging_world"), true, false));
    }
    private static EquipmentItemV1 identified(UUID id) {
        EquipmentItemV1 source = StagingEconomyCatalog.previewBlade(io.github.gyai.projects.equipment.EquipmentTier.T1);
        return new EquipmentItemV1(source.schemaVersion(), source.itemId(), source.category(), source.slot(), source.tier(), source.itemLevel(), source.rarity(), source.quality(), source.baseStatRolls(), source.modSlots(), source.crafter(), source.enhancementLevel(), source.broken(), source.binding(), source.tradePolicy(), Optional.of(id));
    }
    private static final class FakePort implements StagingEconomyOperationPort {
        private final List<EquipmentItemV1> items; FakePort(List<EquipmentItemV1> items) { this.items = items; }
        private int logoutCalls;
        private UUID lastLogout;
        public OperationResult execute(OperationRequest request) { return OperationResult.rejected("unused"); }
        public void selectEnhancementOutcome(StagingOperationAccess access, io.github.gyai.projects.enhancement.v2.EnhancementOutcome outcome) { }
        public StagingInventoryPort.InventorySnapshot status(UUID playerId) { return new StagingInventoryPort.InventorySnapshot(0, Map.of(), items, 0); }
        public void logout(UUID playerId) { logoutCalls++; lastLogout = playerId; }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
