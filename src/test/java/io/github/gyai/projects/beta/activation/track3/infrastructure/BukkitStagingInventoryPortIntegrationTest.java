package io.github.gyai.projects.beta.activation.track3.infrastructure;

import io.github.gyai.projects.beta.activation.BetaActivationAudience;
import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.beta.activation.BetaActivationTargetScope;
import io.github.gyai.projects.beta.activation.BetaMutationPolicy;
import io.github.gyai.projects.beta.activation.Track3ToTrack4Ports;
import io.github.gyai.projects.beta.activation.track1.bukkit.BukkitEquipmentInventoryReader;
import io.github.gyai.projects.beta.activation.track1.equipment.EquipmentInspectionService;
import io.github.gyai.projects.beta.activation.track3.BoundedStagingOperationJournal;
import io.github.gyai.projects.beta.activation.track3.BoundedStagingInventory;
import io.github.gyai.projects.beta.activation.track3.StagingEconomyCatalog;
import io.github.gyai.projects.beta.activation.track3.StagingEquipmentInspectionFormatter;
import io.github.gyai.projects.beta.activation.track3.StagingEconomyOperationPort;
import io.github.gyai.projects.beta.activation.track3.StagingEconomyService;
import io.github.gyai.projects.beta.activation.track3.StagingEquipmentCodec;
import io.github.gyai.projects.beta.activation.track3.StagingEquipmentDocument;
import io.github.gyai.projects.beta.activation.track3.StagingEnhancementOutcomeRegistry;
import io.github.gyai.projects.beta.activation.track3.StagingInventoryTransactionAdapter;
import io.github.gyai.projects.beta.activation.track3.StagingOperationAccess;
import io.github.gyai.projects.beta.activation.track3.StagingTransactionAuditSink;
import io.github.gyai.projects.beta.activation.track4.StagingItemDeliveryPort;
import io.github.gyai.projects.enhancement.v2.EnhancementOutcome;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentTier;
import io.github.gyai.projects.equipment.operation.EquipmentMutationProposal;
import io.github.gyai.projects.equipment.operation.OperationResourcePlan;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.reward.RewardClaimKey;
import io.github.gyai.projects.reward.RewardClaimRequest;
import io.github.gyai.projects.reward.RewardDeliveryReceipt;
import io.github.gyai.projects.dev.StagingWorkbenchPresenter;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Server-free, production-equivalent exercise of the Bukkit inventory adapter. */
public final class BukkitStagingInventoryPortIntegrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC);

    private BukkitStagingInventoryPortIntegrationTest() { }

    public static void runAll() {
        workbenchOperationPathCommitsAndReplaysThroughLiveStorage();
        liveStorageConflictsAndFailuresDoNotExposePartialResults();
        liveEquipmentInputsAreReplacedAtomically();
        workbenchAccessMatrixUsesPresenterBoundary();
        productionAdapterCommittedLoreAndEnvelopeRoundTrips();
        freshDefaultOffLiveSnapshotAndTrack1Inspect();
        vanillaMaterialsAreNotStagingResources();
        tokenDeliveryCommitsReplaysAndRejectsFullLiveStorage();
        track4TokenDeliveryUsesLiveTrack3StorageAndPreservesUncertainty();
        nullModRollServiceIsRejectedAtConstruction();
    }

    private static void productionAdapterCommittedLoreAndEnvelopeRoundTrips() {
        NamespacedKey marker = new NamespacedKey("projects", "beta_staging_equipment");
        NamespacedKey payload = new NamespacedKey("projects", "beta_staging_equipment_payload");
        NamespacedKey revision = new NamespacedKey("projects", "beta_staging_equipment_revision");
        NamespacedKey itemId = new NamespacedKey("projects", "beta_staging_item_id");
        BukkitStagingEquipmentItemAdapter adapter = new BukkitStagingEquipmentItemAdapter(
                marker, payload, revision, itemId, RenderedTestStack::new);
        UUID instanceId = new UUID(0, 730);
        StagingEquipmentDocument document = finalizedDocument(instanceId, 17);

        ItemStack rendered = adapter.committed(document);
        check(adapter.read(rendered).orElseThrow().equals(document), "production adapter lost document envelope");
        ItemMeta meta = rendered.getItemMeta();
        check(meta.displayName() != null, "production adapter omitted display name");
        check(String.valueOf(meta.displayName()).contains("[STAGING]"), "production adapter omitted staging display marker");
        String lore = meta.lore().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("\n"));
        for (String field : List.of("Beta staging fixture", instanceId.toString(), "T1 / ILv 1",
                "Enhancement +3", "MOD projects:staging-keen-edge R1 1.25")) {
            check(lore.contains(field), "production adapter lore omitted " + field);
        }
        PersistentDataContainer envelope = meta.getPersistentDataContainer();
        check(envelope.has(marker, PersistentDataType.BYTE)
                        && envelope.has(payload, PersistentDataType.BYTE_ARRAY)
                        && envelope.has(revision, PersistentDataType.LONG)
                        && envelope.has(itemId, PersistentDataType.STRING),
                "production adapter omitted staging envelope fields");

        UUID player = new UUID(0, 731);
        MemoryAccess live = new MemoryAccess(player, new ItemStack[]{resource(StagingEconomyCatalog.IRON_INGOT, 3), null, null});
        BukkitStagingInventoryPort inventory = new BukkitStagingInventoryPort(new BukkitStagingInventoryBridge(live), adapter);
        AtomicInteger ids = new AtomicInteger(731);
        StagingEconomyService service = service(inventory, new BoundedStagingOperationJournal(32),
                () -> new UUID(0, ids.incrementAndGet()));
        try {
            StagingOperationAccess access = allowed(player);
            EquipmentItemV1 crafted = execute(service, request(732, access,
                    StagingEconomyOperationPort.OperationKind.CRAFT, null, 0)).equipment().orElseThrow();
            ItemStack liveOutput = Arrays.stream(live.copy()).filter(stack -> stack != null
                    && stack.getType() == Material.IRON_SWORD).findFirst().orElseThrow();
            StagingEquipmentDocument liveDocument = adapter.read(liveOutput).orElseThrow();
            check(liveDocument.item().equals(crafted), "craft output was not adapter-readable");
            PersistentDataContainer liveEnvelope = liveOutput.getItemMeta().getPersistentDataContainer();
            check(liveEnvelope.has(marker, PersistentDataType.BYTE)
                            && liveEnvelope.has(payload, PersistentDataType.BYTE_ARRAY)
                            && liveEnvelope.has(revision, PersistentDataType.LONG)
                            && liveEnvelope.has(itemId, PersistentDataType.STRING),
                    "craft output omitted production metadata");
        } finally { service.close(); }
    }

    private static void freshDefaultOffLiveSnapshotAndTrack1Inspect() {
        NamespacedKey marker = new NamespacedKey("projects", "beta_staging_equipment");
        NamespacedKey payload = new NamespacedKey("projects", "beta_staging_equipment_payload");
        NamespacedKey revision = new NamespacedKey("projects", "beta_staging_equipment_revision");
        NamespacedKey itemId = new NamespacedKey("projects", "beta_staging_item_id");
        BukkitStagingEquipmentItemAdapter adapter = new BukkitStagingEquipmentItemAdapter(
                marker, payload, revision, itemId, RenderedTestStack::new);
        UUID player = new UUID(0, 740);
        UUID instanceId = new UUID(0, 741);
        StagingEquipmentDocument document = finalizedDocument(instanceId, 19);
        MemoryAccess live = new MemoryAccess(player, new ItemStack[]{
                resource(StagingEconomyCatalog.IRON_ORE, 7), resource(StagingEconomyCatalog.IRON_INGOT, 2), adapter.committed(document)});
        ItemStack[] beforeReads = live.copy();
        AtomicInteger generatedIds = new AtomicInteger();
        BukkitStagingInventoryPort inventory = new BukkitStagingInventoryPort(new BukkitStagingInventoryBridge(live), adapter);
        BoundedStagingOperationJournal journal = new BoundedStagingOperationJournal(32);
        StagingEconomyService service = new StagingEconomyService(inventory, journal,
                new StagingInventoryTransactionAdapter(inventory, journal, CLOCK,
                        () -> { generatedIds.incrementAndGet(); return new UUID(0, 742); }),
                new StagingEnhancementOutcomeRegistry());
        try {
            var status = service.status(player);
            check(status.resources().equals(Map.of(StagingEconomyCatalog.IRON_ORE, 7L,
                    StagingEconomyCatalog.IRON_INGOT, 2L)), "fresh status did not read live resources");
            check(status.equipment().equals(List.of(document.item())), "fresh status did not read live equipment");
            check(generatedIds.get() == 0 && same(beforeReads, live.copy()),
                    "status generated identity or mutated live storage");

            BetaActivationPolicy off = new BetaActivationPolicy(BetaActivationAudience.OFF,
                    BetaActivationTargetScope.TRAINING_DUMMY_ONLY, BetaMutationPolicy.READ_ONLY,
                    Set.of(), Set.of("staging_world"), true, false);
            StagingOperationAccess readOnly = new StagingOperationAccess(player, "staging_world", true, off);
            StagingWorkbenchPresenter presenter = new StagingWorkbenchPresenter(service, () -> false, () -> false, () -> false);
            var view = presenter.view(readOnly, Optional.of(instanceId));
            check(view.snapshot().resources().equals(status.resources()) && view.snapshot().equipment().equals(List.of(document.item())),
                    "default-off workbench view was not readable");
            check(!presenter.action(new UUID(0, 743), readOnly, StagingEconomyOperationPort.OperationKind.GIVE,
                    Optional.of(StagingEconomyCatalog.IRON_ORE), 1).accepted(), "default-off action was enabled");
            check(generatedIds.get() == 0 && same(beforeReads, live.copy()),
                    "default-off reads mutated storage or generated identity");

            EquipmentInspectionService inspection = new EquipmentInspectionService(CLOCK);
            inspection.start();
            try {
                var projected = inspection.inspect(player, new BukkitEquipmentInventoryReader().scan(player(player, live.copy())))
                        .items().stream().flatMap(value -> value.projection().stream()).findFirst().orElseThrow();
                String display = StagingEquipmentInspectionFormatter.format(projected);
                check(projected.equals(document.item()) && display.contains("UUID=" + instanceId)
                                && display.contains("projects:staging-keen-edge") && display.contains("value=1.25"),
                        "Track 1 inspection lost staging UUID, MOD, or display");
            } finally { inspection.close(); }
            check(generatedIds.get() == 0 && same(beforeReads, live.copy()),
                    "Track 1 inspection mutated storage or generated identity");
        } finally { service.close(); }
    }

    private static void workbenchOperationPathCommitsAndReplaysThroughLiveStorage() {
        UUID player = new UUID(0, 701);
        MemoryAccess live = new MemoryAccess(player, new ItemStack[9]);
        BukkitStagingInventoryPort inventory = inventory(live);
        BoundedStagingOperationJournal journal = new BoundedStagingOperationJournal(64);
        AtomicInteger ids = new AtomicInteger(900);
        StagingEconomyService service = service(inventory, journal,
                () -> new UUID(0, ids.incrementAndGet()));
        StagingOperationAccess access = allowed(player);
        StagingWorkbenchPresenter presenter = new StagingWorkbenchPresenter(
                service, () -> true, () -> true, () -> true);
        try {
            presenterExecute(presenter, new UUID(0, 1), access,
                    StagingEconomyOperationPort.OperationKind.GIVE,
                    StagingEconomyCatalog.IRON_ORE, 10);
            presenterExecute(presenter, new UUID(0, 2), access,
                    StagingEconomyOperationPort.OperationKind.REFINE, null, 0);
            presenterExecute(presenter, new UUID(0, 3), access,
                    StagingEconomyOperationPort.OperationKind.REFINE, null, 0);
            presenterExecute(presenter, new UUID(0, 4), access,
                    StagingEconomyOperationPort.OperationKind.REFINE, null, 0);
            UUID craftRequest = new UUID(0, 705);
            var crafted = presenterExecute(presenter, craftRequest, access,
                    StagingEconomyOperationPort.OperationKind.CRAFT, null, 0);
            EquipmentItemV1 item = crafted.equipment().orElseThrow();
            check(item.instanceId().orElseThrow().equals(new UUID(0, 901)), "commit UUID missing");
            check(item.modSlots().size() == 1 && item.modSlots().getFirst().entry().isPresent(), "staging MOD missing");
            String formatted = StagingEquipmentInspectionFormatter.format(item);
            for (String field : java.util.List.of("ID=", "UUID=", "Tier=", "ILv=", "Rarity=", "Quality=", "Category=", "Slot=", "Enhancement=", "Broken=", "Binding=", "Trade=", "MOD slots=", "Keen Edge (Staging)")) {
                check(formatted.contains(field), "inspection formatter omitted " + field);
            }
            Map<String, Long> resources = service.status(player).resources();
            check(resources.get(StagingEconomyCatalog.IRON_ORE) == 4L, "raw iron cost incorrect");
            check(!resources.containsKey(StagingEconomyCatalog.IRON_INGOT), "ingots were not consumed");
            ItemStack[] committedStorage = live.copy();
            check(count(committedStorage, Material.RAW_IRON) == 4 && count(committedStorage, Material.IRON_INGOT) == 0,
                    "live resource storage diverged");
            check(stagingStacks(committedStorage) == 1, "live equipment output missing");

            var replay = presenter.action(craftRequest, access,
                    StagingEconomyOperationPort.OperationKind.CRAFT, Optional.empty(), 0)
                    .result().orElseThrow();
            check(replay.status() == StagingEconomyOperationPort.Status.REPLAYED, "craft was not replayed");
            check(replay.equipment().orElseThrow().equals(item), "replay changed UUID or MOD roll");
            check(same(committedStorage, live.copy()), "replay mutated live storage");
            service.logout(player);
            check(service.status(player).equipment().size() == 1, "logout refresh duplicated or lost staging equipment");
            execute(service, request(8, access, StagingEconomyOperationPort.OperationKind.GIVE,
                    StagingEconomyCatalog.IRON_ORE, 1));
            check(service.status(player).equipment().size() == 1, "subsequent write after refresh duplicated staging equipment");
            committedStorage = live.copy();

            EquipmentInspectionService inspection = new EquipmentInspectionService(CLOCK);
            inspection.start();
            try {
                Player playerProxy = player(player, live.copy());
                var projection = inspection.inspect(player, new BukkitEquipmentInventoryReader().scan(playerProxy));
                var projected = projection.items().stream().filter(value -> value.projection().isPresent())
                        .findFirst().orElseThrow().projection().orElseThrow();
                check(projected.equals(item), "Track 1 projection lost staging item identity");
            } finally { inspection.close(); }

            StagingOperationAccess denied = new StagingOperationAccess(player, "other_world", true,
                    access.activationPolicy());
            check(service.execute(request(9, denied, StagingEconomyOperationPort.OperationKind.GIVE,
                    StagingEconomyCatalog.IRON_ORE, 1)).status() == StagingEconomyOperationPort.Status.REJECTED,
                    "wrong-world write was accepted");
            check(same(committedStorage, live.copy()), "denied action mutated storage");
        } finally { service.close(); }
    }

    private static void vanillaMaterialsAreNotStagingResources() {
        UUID player = new UUID(0, 760);
        MemoryAccess live = new MemoryAccess(player, new ItemStack[]{
                new TestStack(Material.RAW_IRON, 9), resource(StagingEconomyCatalog.IRON_ORE, 2),
                new TestStack(Material.IRON_INGOT, 4), null});
        StagingEconomyService service = service(inventory(live), new BoundedStagingOperationJournal(32),
                () -> new UUID(0, 761));
        try {
            check(service.status(player).resources().equals(Map.of(StagingEconomyCatalog.IRON_ORE, 2L)),
                    "vanilla materials leaked into the staging snapshot");
            execute(service, request(762, allowed(player), StagingEconomyOperationPort.OperationKind.REFINE,
                    null, 0));
            check(count(live.copy(), Material.RAW_IRON) == 9,
                    "refine consumed normal Minecraft raw iron");
            check(count(live.copy(), Material.IRON_INGOT) == 5,
                    "refine did not preserve normal ingots and add only the staging output");
        } finally { service.close(); }
    }

    private static void nullModRollServiceIsRejectedAtConstruction() {
        try {
            new StagingInventoryTransactionAdapter(new BoundedStagingInventory(),
                    new BoundedStagingOperationJournal(4), CLOCK, UUID::randomUUID, null);
            throw new AssertionError("null MOD roll service was accepted");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("input missing"), "null MOD roll rejection was not explicit");
        }
    }

    private static void track4TokenDeliveryUsesLiveTrack3StorageAndPreservesUncertainty() {
        UUID player = new UUID(0, 780);
        BetaActivationPolicy policy = allowed(player).activationPolicy();
        StagingItemDeliveryPort.DeliveryContext context = new StagingItemDeliveryPort.DeliveryContext(
                "staging_world", true, true);
        MemoryAccess live = new MemoryAccess(player, new ItemStack[2]);
        StagingEconomyService service = service(inventory(live), new BoundedStagingOperationJournal(32),
                () -> new UUID(0, 781));
        StagingItemDeliveryPort delivery = Track3ToTrack4Ports.delivery(service, policy, () -> true);
        RewardClaimRequest claim = claim(player, 782);
        try {
            RewardDeliveryReceipt first = delivery.deliver(claim, StagingEconomyCatalog.TEST_TOKEN, 1, context);
            check(first.status() == RewardDeliveryReceipt.Status.DELIVERED && first.durable(),
                    "Track 4 first token delivery did not reach live Track 3 storage");
            ItemStack[] committed = live.copy();
            RewardDeliveryReceipt replay = delivery.deliver(claim, StagingEconomyCatalog.TEST_TOKEN, 1, context);
            check(replay.status() == RewardDeliveryReceipt.Status.DELIVERED && replay.durable(),
                    "Track 4 same-claim delivery did not replay as delivered");
            check(same(committed, live.copy()) && count(live.copy(), Material.PAPER) == 1,
                    "Track 4 replay duplicated a token in live storage");
        } finally { service.close(); }

        MemoryAccess full = new MemoryAccess(player, new ItemStack[]{
                new TestStack(Material.STONE, 1), new TestStack(Material.DIRT, 1)});
        AtomicInteger fullIntents = new AtomicInteger();
        AtomicInteger fullTerminals = new AtomicInteger();
        BoundedStagingOperationJournal fullJournal = new BoundedStagingOperationJournal(32,
                new StagingTransactionAuditSink() {
                    @Override public void resolved(EquipmentMutationProposal proposal) { }
                    @Override public void resourceIntent(TransactionRequest request, io.github.gyai.projects.crafting.OutputProposal output) {
                        fullIntents.incrementAndGet();
                    }
                    @Override public void terminal(io.github.gyai.projects.transaction.TransactionAuditResult result) {
                        fullTerminals.incrementAndGet();
                    }
                });
        StagingEconomyService fullService = service(inventory(full), fullJournal,
                () -> new UUID(0, 783));
        RewardClaimRequest fullClaim = claim(player, 784);
        try {
            RewardDeliveryReceipt receipt = Track3ToTrack4Ports.delivery(fullService, policy, () -> true)
                    .deliver(fullClaim, StagingEconomyCatalog.TEST_TOKEN, 1, context);
            check(receipt.status() == RewardDeliveryReceipt.Status.FULL_INVENTORY,
                    "known-full live storage was not classified as FULL_INVENTORY: " + receipt);
            check(same(new ItemStack[]{new TestStack(Material.STONE, 1), new TestStack(Material.DIRT, 1)}, full.copy()),
                    "full Track 4 delivery changed live storage");
            check(fullJournal.size() == 0 && fullIntents.get() == 0 && fullTerminals.get() == 0
                            && fullService.status(player).activeReservations() == 0,
                    "full Track 4 delivery entered a resource or terminal journal stage");

            full.storage[1] = null;
            StagingItemDeliveryPort retryDelivery = Track3ToTrack4Ports.delivery(fullService, policy, () -> true);
            RewardDeliveryReceipt delivered = retryDelivery.deliver(fullClaim,
                    StagingEconomyCatalog.TEST_TOKEN, 1, context);
            check(delivered.status() == RewardDeliveryReceipt.Status.DELIVERED && delivered.durable()
                            && count(full.copy(), Material.PAPER) == 1,
                    "same Track 4 claim did not commit exactly one token after freeing a slot: " + delivered);
            ItemStack[] committed = full.copy();
            RewardDeliveryReceipt replay = retryDelivery.deliver(fullClaim,
                    StagingEconomyCatalog.TEST_TOKEN, 1, context);
            check(replay.status() == RewardDeliveryReceipt.Status.DELIVERED && replay.durable()
                            && same(committed, full.copy()) && fullIntents.get() == 1 && fullTerminals.get() == 1,
                    "successful Track 4 claim replay duplicated a token or rewrote custody: " + replay);
        } finally { fullService.close(); }

        MemoryAccess uncertain = new MemoryAccess(player, new ItemStack[2]);
        StagingTransactionAuditSink losingAcknowledgement = new StagingTransactionAuditSink() {
            @Override public void resolved(EquipmentMutationProposal proposal) { }
            @Override public void terminal(io.github.gyai.projects.transaction.TransactionAuditResult result) {
                throw new IllegalStateException("durable acknowledgement lost");
            }
        };
        StagingEconomyService uncertainService = service(inventory(uncertain),
                new BoundedStagingOperationJournal(32, losingAcknowledgement), () -> new UUID(0, 785));
        StagingItemDeliveryPort uncertainDelivery = Track3ToTrack4Ports.delivery(
                uncertainService, policy, () -> true);
        RewardClaimRequest uncertainClaim = claim(player, 786);
        try {
            RewardDeliveryReceipt first = uncertainDelivery.deliver(uncertainClaim,
                    StagingEconomyCatalog.TEST_TOKEN, 1, context);
            check(first.status() == RewardDeliveryReceipt.Status.COMMIT_UNCERTAIN,
                    "lost durable acknowledgement was not retained as commit uncertainty");
            ItemStack[] exposed = uncertain.copy();
            RewardDeliveryReceipt retry = uncertainDelivery.deliver(uncertainClaim,
                    StagingEconomyCatalog.TEST_TOKEN, 1, context);
            check(retry.status() == RewardDeliveryReceipt.Status.REJECTED
                            && same(exposed, uncertain.copy()) && count(uncertain.copy(), Material.PAPER) == 1,
                    "uncertain token delivery was blindly retried or duplicated: first=" + first
                            + ", retry=" + retry + ", papers=" + count(uncertain.copy(), Material.PAPER));
        } finally { uncertainService.close(); }
    }

    private static RewardClaimRequest claim(UUID player, long id) {
        return new RewardClaimRequest(new UUID(0, id), new RewardClaimKey(player,
                "projects:staging-quest", new UUID(0, id + 10_000),
                "projects:staging-token", 1), Instant.parse("2026-08-05T00:00:00Z"));
    }

    private static void tokenDeliveryCommitsReplaysAndRejectsFullLiveStorage() {
        UUID player = new UUID(0, 770);
        StagingOperationAccess access = allowed(player);
        MemoryAccess live = new MemoryAccess(player, new ItemStack[2]);
        StagingEconomyService service = service(inventory(live), new BoundedStagingOperationJournal(32),
                () -> new UUID(0, 771));
        try {
            UUID requestId = new UUID(0, 772);
            var first = service.deliver(access, requestId, StagingEconomyCatalog.TEST_TOKEN, 1);
            check(first.status() == StagingEconomyOperationPort.Status.COMMITTED,
                    "first token delivery did not commit to live storage");
            check(service.status(player).resources().equals(Map.of(StagingEconomyCatalog.TEST_TOKEN, 1L)),
                    "committed token was not recognized by the staging snapshot");
            check(count(live.copy(), Material.PAPER) == 1,
                    "committed token did not use its PAPER fixture material");
            ItemStack[] committed = live.copy();
            check(service.deliver(access, requestId, StagingEconomyCatalog.TEST_TOKEN, 1).status()
                            == StagingEconomyOperationPort.Status.REPLAYED,
                    "token delivery replay was not terminal");
            check(same(committed, live.copy()), "token delivery replay duplicated the physical token");
        } finally { service.close(); }

        MemoryAccess full = new MemoryAccess(player, new ItemStack[]{
                new TestStack(Material.STONE, 1), new TestStack(Material.DIRT, 1)});
        BoundedStagingOperationJournal fullJournal = new BoundedStagingOperationJournal(32);
        StagingEconomyService fullService = service(inventory(full), fullJournal,
                () -> new UUID(0, 773));
        try {
            ItemStack[] before = full.copy();
            UUID requestId = new UUID(0, 774);
            var result = fullService.deliver(access, requestId,
                    StagingEconomyCatalog.TEST_TOKEN, 1);
            check(result.status() == StagingEconomyOperationPort.Status.REJECTED
                            && result.detail().equals("full-inventory"),
                    "full live storage was not rejected at capacity validation: " + result);
            check(same(before, full.copy()) && fullJournal.size() == 0
                            && fullService.status(player).activeReservations() == 0,
                    "full token delivery mutated storage or retained custody");

            full.storage[1] = null;
            var committed = fullService.deliver(access, requestId, StagingEconomyCatalog.TEST_TOKEN, 1);
            check(committed.status() == StagingEconomyOperationPort.Status.COMMITTED
                            && count(full.copy(), Material.PAPER) == 1,
                    "same direct delivery did not commit after freeing a slot: " + committed);
            ItemStack[] delivered = full.copy();
            check(fullService.deliver(access, requestId, StagingEconomyCatalog.TEST_TOKEN, 1).status()
                            == StagingEconomyOperationPort.Status.REPLAYED
                            && same(delivered, full.copy()),
                    "same direct delivery replay duplicated the committed token");
        } finally { fullService.close(); }
    }

    private static void liveStorageConflictsAndFailuresDoNotExposePartialResults() {
        UUID player = new UUID(0, 710);
        MemoryAccess live = new MemoryAccess(player, new ItemStack[]{resource(StagingEconomyCatalog.IRON_ORE, 2), null});
        BukkitStagingInventoryPort inventory = inventory(live);
        BoundedStagingOperationJournal journal = new BoundedStagingOperationJournal(32);
        StagingEconomyService service = service(inventory, journal, () -> new UUID(0, 711));
        StagingOperationAccess access = allowed(player);
        try {
            live.conflictNextReplace = true;
            var conflict = service.execute(request(12, access, StagingEconomyOperationPort.OperationKind.REFINE, null, 0));
            check(conflict.status() != StagingEconomyOperationPort.Status.COMMITTED
                    && conflict.status() != StagingEconomyOperationPort.Status.REPLAYED,
                    "CAS conflict was accepted");
            check(count(live.copy(), Material.RAW_IRON) == 2 && count(live.copy(), Material.IRON_INGOT) == 0,
                    "CAS conflict exposed partial consume");

            var insufficient = service.execute(request(13, access, StagingEconomyOperationPort.OperationKind.CRAFT, null, 0));
            check(insufficient.status() == StagingEconomyOperationPort.Status.REJECTED, "insufficient craft accepted");
            check(stagingStacks(live.copy()) == 0, "insufficient craft created an item");

            live.rejectWrites = true;
            var rejected = service.execute(request(14, access, StagingEconomyOperationPort.OperationKind.REFINE, null, 0));
            check(rejected.status() != StagingEconomyOperationPort.Status.COMMITTED
                    && rejected.status() != StagingEconomyOperationPort.Status.REPLAYED,
                    "rejected live mutation reported success");
            check(count(live.copy(), Material.RAW_IRON) == 2, "failed live mutation changed input");
            live.rejectWrites = false;
            live.applyThenReject = true;
            var partial = service.execute(request(16, access, StagingEconomyOperationPort.OperationKind.REFINE, null, 0));
            check(partial.status() != StagingEconomyOperationPort.Status.COMMITTED, "partial live mutation reported success");
            check(count(live.copy(), Material.RAW_IRON) == 2 && count(live.copy(), Material.IRON_INGOT) == 0,
                    "partial live mutation was not compensated");
        } finally { service.close(); }

        ItemStack[] full = new ItemStack[2];
        full[0] = resource(StagingEconomyCatalog.IRON_ORE, 2);
        full[1] = new TestStack(Material.IRON_SWORD, 1);
        MemoryAccess fullLive = new MemoryAccess(player, full);
        StagingEconomyService fullService = service(inventory(fullLive), new BoundedStagingOperationJournal(32),
                () -> new UUID(0, 712));
        try {
            var fullResult = fullService.execute(request(15, access, StagingEconomyOperationPort.OperationKind.REFINE, null, 0));
            check(fullResult.status() == StagingEconomyOperationPort.Status.COMMITTED, "consumed slot did not admit output");
            check(count(fullLive.copy(), Material.RAW_IRON) == 0 && count(fullLive.copy(), Material.IRON_INGOT) == 1,
                    "exact-two ore did not become one ingot");
        } finally { fullService.close(); }
    }

    private static void liveEquipmentInputsAreReplacedAtomically() {
        UUID player = new UUID(0, 750);
        StagingOperationAccess access = allowed(player);

        UUID promotedId = new UUID(0, 751);
        MemoryAccess promoteLive = new MemoryAccess(player, new ItemStack[]{
                TestStack.equipment(equipmentDocument(EquipmentTier.T1, 0, false, promotedId, 0)),
                resource(StagingEconomyCatalog.IRON_INGOT, 2), null});
        StagingEconomyService promoteService = equipmentService(inventory(promoteLive), 752);
        try {
            ItemStack original = promoteLive.copy()[0];
            var request = request(753, access, StagingEconomyOperationPort.OperationKind.PROMOTE, null, 0);
            EquipmentItemV1 replacement = execute(promoteService, request).equipment().orElseThrow();
            assertReplacement(promoteLive.copy(), original, replacement, promotedId, 1);
            ItemStack[] committed = promoteLive.copy();
            check(promoteService.execute(request).status() == StagingEconomyOperationPort.Status.REPLAYED,
                    "promote replay was not terminal");
            check(same(committed, promoteLive.copy()), "promote replay added a physical item");
        } finally { promoteService.close(); }

        UUID enhancedId = new UUID(0, 754);
        MemoryAccess enhanceLive = new MemoryAccess(player, new ItemStack[]{
                TestStack.equipment(equipmentDocument(EquipmentTier.T1, 3, false, enhancedId, 0)), null});
        StagingEconomyService enhanceService = equipmentService(inventory(enhanceLive), 755);
        try {
            ItemStack original = enhanceLive.copy()[0];
            enhanceService.selectEnhancementOutcome(access, EnhancementOutcome.SUCCESS);
            var request = request(756, access, StagingEconomyOperationPort.OperationKind.ENHANCE, null, 0);
            EquipmentItemV1 replacement = execute(enhanceService, request).equipment().orElseThrow();
            check(replacement.enhancementLevel() == 4, "enhance outcome was not deterministic");
            assertReplacement(enhanceLive.copy(), original, replacement, enhancedId, 1);
            ItemStack[] committed = enhanceLive.copy();
            check(enhanceService.execute(request).status() == StagingEconomyOperationPort.Status.REPLAYED,
                    "enhance replay was not terminal");
            check(same(committed, enhanceLive.copy()), "enhance replay added a physical item");
        } finally { enhanceService.close(); }

        UUID brokenId = new UUID(0, 757);
        MemoryAccess breakLive = new MemoryAccess(player, new ItemStack[]{
                TestStack.equipment(equipmentDocument(EquipmentTier.T1, 4, false, brokenId, 0)), null});
        StagingEconomyService breakService = equipmentService(inventory(breakLive), 758);
        try {
            ItemStack original = breakLive.copy()[0];
            var request = request(759, access, StagingEconomyOperationPort.OperationKind.BREAK, null, 0);
            EquipmentItemV1 replacement = execute(breakService, request).equipment().orElseThrow();
            check(replacement.broken(), "break did not resolve a broken replacement");
            assertReplacement(breakLive.copy(), original, replacement, brokenId, 1);
            ItemStack[] committed = breakLive.copy();
            check(breakService.execute(request).status() == StagingEconomyOperationPort.Status.REPLAYED,
                    "break replay was not terminal");
            check(same(committed, breakLive.copy()), "break replay added a physical item");
        } finally { breakService.close(); }

        UUID targetId = new UUID(0, 760), donorId = new UUID(0, 761);
        MemoryAccess repairLive = new MemoryAccess(player, new ItemStack[]{
                TestStack.equipment(equipmentDocument(EquipmentTier.T1, 5, true, targetId, 0)),
                TestStack.equipment(equipmentDocument(EquipmentTier.T1, 0, false, donorId, 0)), null});
        StagingEconomyService repairService = equipmentService(inventory(repairLive), 762);
        try {
            ItemStack original = repairLive.copy()[0];
            var request = request(763, access, StagingEconomyOperationPort.OperationKind.REPAIR, null, 0);
            EquipmentItemV1 replacement = execute(repairService, request).equipment().orElseThrow();
            check(!replacement.broken(), "repair did not resolve an intact replacement");
            assertReplacement(repairLive.copy(), original, replacement, targetId, 1);
            check(stagingItems(repairLive.copy()).stream().noneMatch(item -> donorId.equals(item.instanceId().orElse(null))),
                    "repair retained the physical donor");
            ItemStack[] committed = repairLive.copy();
            check(repairService.execute(request).status() == StagingEconomyOperationPort.Status.REPLAYED,
                    "repair replay was not terminal");
            check(same(committed, repairLive.copy()), "repair replay added a physical item");
        } finally { repairService.close(); }

        UUID failedId = new UUID(0, 764);
        ItemStack failedSource = TestStack.equipment(equipmentDocument(EquipmentTier.T1, 3, false, failedId, 0));
        MemoryAccess rejectedLive = new MemoryAccess(player, new ItemStack[]{failedSource, null});
        StagingEconomyService rejectedService = equipmentService(inventory(rejectedLive), 765);
        try {
            ItemStack[] before = rejectedLive.copy();
            rejectedLive.applyThenReject = true;
            var result = rejectedService.execute(request(766, access,
                    StagingEconomyOperationPort.OperationKind.ENHANCE, null, 0));
            check(result.status() != StagingEconomyOperationPort.Status.COMMITTED,
                    "apply-then-reject equipment consume reported success");
            check(same(before, rejectedLive.copy()),
                    "apply-then-reject equipment consume did not restore its original stack");
        } finally { rejectedService.close(); }

        UUID fullId = new UUID(0, 767);
        ItemStack original = TestStack.equipment(equipmentDocument(EquipmentTier.T1, 0, false, fullId, 0));
        MemoryAccess fullLive = new MemoryAccess(player, new ItemStack[]{original});
        BukkitStagingInventoryPort fullInventory = inventory(fullLive);
        fullInventory.openSession(player);
        long revision = fullInventory.snapshot(player).revision();
        TransactionRequest consume = new TransactionRequest(new UUID(0, 768), player,
                "projects:enhancement-v2", StagingEconomyCatalog.ENHANCEMENT_POLICY_ID,
                revision, 1, List.of(new TransactionRequest.InputRevision(
                EquipmentMutationProposal.inputId(fullId), revision)));
        var capacity = fullInventory.validate(player, consume, OperationResourcePlan.none()).orElseThrow();
        var reservation = fullInventory.reserve(player, consume, OperationResourcePlan.none(), capacity);
        fullInventory.consume(player, consume, OperationResourcePlan.none(), reservation);
        fullLive.storage[0] = new TestStack(Material.IRON_SWORD, 1);
        var full = fullInventory.commitEquipment(player, consume.requestId(), reservation, revision,
                equipmentDocument(EquipmentTier.T2, 0, false, fullId, revision + 1));
        check(!full.committed() && full.status().equals("full"), "full equipment commit was accepted");
        check(same(new ItemStack[]{original}, fullLive.copy()),
                "full equipment commit did not restore the exact original physical stack");
    }

    private static StagingEconomyService equipmentService(BukkitStagingInventoryPort inventory, long generatedId) {
        StagingEconomyService service = service(inventory, new BoundedStagingOperationJournal(32),
                () -> new UUID(0, generatedId));
        service.setGroupRunning(StagingEconomyService.OperationGroup.ENHANCEMENT_REPAIR, true);
        return service;
    }

    private static StagingEquipmentDocument equipmentDocument(EquipmentTier tier, int enhancement,
                                                               boolean broken, UUID instanceId, long revision) {
        EquipmentItemV1 preview = StagingEconomyCatalog.previewBlade(tier);
        EquipmentItemV1 item = new EquipmentItemV1(preview.schemaVersion(), preview.itemId(), preview.category(),
                preview.slot(), preview.tier(), preview.itemLevel(), preview.rarity(), preview.quality(),
                preview.baseStatRolls(), preview.modSlots(), preview.crafter(), enhancement, broken,
                preview.binding(), preview.tradePolicy(), Optional.of(instanceId));
        return new StagingEquipmentCodec().encode(item, revision);
    }

    private static void assertReplacement(ItemStack[] storage, ItemStack original,
                                          EquipmentItemV1 expected, UUID instanceId, long expectedCount) {
        List<EquipmentItemV1> physical = stagingItems(storage);
        check(physical.size() == expectedCount && stagingStacks(storage) == expectedCount,
                "physical staging count was not replaced exactly once");
        check(physical.equals(List.of(expected)) && physical.getFirst().instanceId().orElseThrow().equals(instanceId),
                "physical staging payload did not equal the resolved replacement");
        check(Arrays.stream(storage).filter(value -> value != null).noneMatch(original::isSimilar),
                "original physical stack survived beside its replacement");
    }

    private static List<EquipmentItemV1> stagingItems(ItemStack[] storage) {
        StagingEquipmentCodec codec = new StagingEquipmentCodec();
        return Arrays.stream(storage).filter(value -> value != null && value.hasItemMeta())
                .map(value -> value.getItemMeta().getPersistentDataContainer().get(
                        new NamespacedKey("projects", "beta_staging_equipment_payload"), PersistentDataType.BYTE_ARRAY))
                .filter(java.util.Objects::nonNull).map(payload -> codec.decode(payload).item()).toList();
    }

    private static void workbenchAccessMatrixUsesPresenterBoundary() {
        UUID player = new UUID(0, 720);
        MemoryAccess live = new MemoryAccess(player, new ItemStack[3]);
        StagingEconomyService service = service(inventory(live), new BoundedStagingOperationJournal(32), () -> new UUID(0, 721));
        try {
            StagingOperationAccess allowed = allowed(player);
            StagingWorkbenchPresenter readOnly = new StagingWorkbenchPresenter(service, () -> false, () -> false, () -> false);
            check(readOnly.view(allowed, Optional.empty()).readOnlyReason().contains("GATHERING_CRAFTING"), "default-off view was not readable with module reason");
            check(!readOnly.action(new UUID(0, 722), allowed, StagingEconomyOperationPort.OperationKind.GIVE,
                    Optional.of(StagingEconomyCatalog.IRON_ORE), 1).accepted(), "module-off write was enabled");
            StagingWorkbenchPresenter enabled = new StagingWorkbenchPresenter(service, () -> true, () -> true, () -> true);
            check(enabled.action(new UUID(0, 723), allowed, StagingEconomyOperationPort.OperationKind.GIVE,
                    Optional.of(StagingEconomyCatalog.IRON_ORE), 1).result().orElseThrow().status()
                    == StagingEconomyOperationPort.Status.COMMITTED, "presenter did not delegate give");
            check(!enabled.action(new UUID(0, 724), new StagingOperationAccess(player, "staging_world", false, allowed.activationPolicy()),
                    StagingEconomyOperationPort.OperationKind.GIVE, Optional.of(StagingEconomyCatalog.IRON_ORE), 1).accepted(), "projects.dev denial missing");
            check(!enabled.action(new UUID(0, 725), new StagingOperationAccess(player, "other", true, allowed.activationPolicy()),
                    StagingEconomyOperationPort.OperationKind.GIVE, Optional.of(StagingEconomyCatalog.IRON_ORE), 1).accepted(), "world denial missing");
            var readPolicy = new BetaActivationPolicy(BetaActivationAudience.ALLOWLIST, BetaActivationTargetScope.TRAINING_DUMMY_ONLY,
                    BetaMutationPolicy.READ_ONLY, java.util.Set.of(player), java.util.Set.of("staging_world"), true, false);
            check(!enabled.action(new UUID(0, 726), new StagingOperationAccess(player, "staging_world", true, readPolicy),
                    StagingEconomyOperationPort.OperationKind.GIVE, Optional.of(StagingEconomyCatalog.IRON_ORE), 1).accepted(), "mutation denial missing");
            StagingWorkbenchPresenter craftOff = new StagingWorkbenchPresenter(service, () -> true, () -> true, () -> false);
            check(!craftOff.action(new UUID(0, 727), allowed, StagingEconomyOperationPort.OperationKind.CRAFT,
                    Optional.empty(), 0).accepted(), "craft feature denial missing");
        } finally { service.close(); }
    }

    private static StagingEconomyService service(BukkitStagingInventoryPort inventory,
                                                   BoundedStagingOperationJournal journal,
                                                   java.util.function.Supplier<UUID> ids) {
        StagingEconomyService service = new StagingEconomyService(inventory, journal,
                new StagingInventoryTransactionAdapter(inventory, journal, CLOCK, ids),
                new StagingEnhancementOutcomeRegistry());
        service.setGroupRunning(StagingEconomyService.OperationGroup.GATHERING_CRAFTING, true);
        return service;
    }

    private static StagingEquipmentDocument finalizedDocument(UUID instanceId, long revision) {
        EquipmentItemV1 preview = StagingEconomyCatalog.previewBlade(io.github.gyai.projects.equipment.EquipmentTier.T1);
        EquipmentItemV1 resolved = new io.github.gyai.projects.beta.activation.track3.StagingModRollService(
                io.github.gyai.projects.beta.activation.track3.StagingModRollService.defaultCandidates(), () -> 0.25)
                .resolve(preview);
        EquipmentItemV1 finalized = new EquipmentItemV1(resolved.schemaVersion(), resolved.itemId(),
                resolved.category(), resolved.slot(), resolved.tier(), resolved.itemLevel(), resolved.rarity(),
                resolved.quality(), resolved.baseStatRolls(), resolved.modSlots(), resolved.crafter(), 3,
                resolved.broken(), resolved.binding(), resolved.tradePolicy(), Optional.of(instanceId));
        return new StagingEquipmentCodec().encode(finalized, revision);
    }

    private static BukkitStagingInventoryPort inventory(MemoryAccess live) {
        return new BukkitStagingInventoryPort(new BukkitStagingInventoryBridge(live),
                TestStack::equipment, (material, amount) -> new TestStack(material, amount, true));
    }

    private static ItemStack resource(String itemId, int amount) {
        return new BukkitStagingResourceItemAdapter(
                (material, stackAmount) -> new TestStack(material, stackAmount, true)).create(itemId, amount);
    }

    private static StagingOperationAccess allowed(UUID player) {
        return new StagingOperationAccess(player, "staging_world", true, new BetaActivationPolicy(
                BetaActivationAudience.ALLOWLIST, BetaActivationTargetScope.TRAINING_DUMMY_ONLY,
                BetaMutationPolicy.STAGING_WRITE, java.util.Set.of(player), java.util.Set.of("staging_world"), true, false));
    }

    private static StagingEconomyOperationPort.OperationRequest request(long id, StagingOperationAccess access,
                                                                          StagingEconomyOperationPort.OperationKind kind,
                                                                          String item, long quantity) {
        return new StagingEconomyOperationPort.OperationRequest(new UUID(0, id), access, kind,
                Optional.ofNullable(item), quantity);
    }

    private static StagingEconomyOperationPort.OperationResult execute(StagingEconomyService service,
                                                                         StagingEconomyOperationPort.OperationRequest request) {
        var result = service.execute(request);
        check(result.status() == StagingEconomyOperationPort.Status.COMMITTED, "operation failed: " + result);
        return result;
    }

    private static StagingEconomyOperationPort.OperationResult presenterExecute(
            StagingWorkbenchPresenter presenter, UUID requestId, StagingOperationAccess access,
            StagingEconomyOperationPort.OperationKind kind, String item, long quantity
    ) {
        var action = presenter.action(requestId, access, kind, Optional.ofNullable(item), quantity);
        check(action.accepted(), "presenter rejected action: " + action.denial());
        var result = action.result().orElseThrow();
        check(result.status() == StagingEconomyOperationPort.Status.COMMITTED,
                "presenter operation failed: " + result);
        return result;
    }

    private static long count(ItemStack[] storage, Material material) {
        return Arrays.stream(storage).filter(value -> value != null && value.getType() == material)
                .mapToLong(ItemStack::getAmount).sum();
    }

    private static long stagingStacks(ItemStack[] storage) {
        return Arrays.stream(storage).filter(value -> value != null && value.hasItemMeta()
                && value.getItemMeta().getPersistentDataContainer().getKeys().stream()
                .anyMatch(key -> key.getKey().equals("beta_staging_equipment_payload"))).count();
    }

    private static boolean same(ItemStack[] first, ItemStack[] second) {
        if (first.length != second.length) return false;
        for (int i = 0; i < first.length; i++) {
            if (first[i] == null || second[i] == null) { if (first[i] != second[i]) return false; }
            else if (first[i].getAmount() != second[i].getAmount() || !first[i].isSimilar(second[i])) return false;
        }
        return true;
    }

    private static ItemStack[] copy(ItemStack[] source) {
        return Arrays.stream(source).map(value -> value == null ? null : value.clone()).toArray(ItemStack[]::new);
    }

    private static Player player(UUID id, ItemStack[] storage) {
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
                PlayerInventory.class.getClassLoader(), new Class[]{PlayerInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getStorageContents" -> copy(storage);
                    case "getArmorContents" -> new ItemStack[4];
                    case "getItemInOffHand" -> null;
                    default -> defaultValue(method.getReturnType());
                });
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getInventory" -> inventory;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class MemoryAccess implements BukkitStagingInventoryBridge.BukkitInventoryAccess {
        private final UUID player;
        private ItemStack[] storage;
        private boolean conflictNextReplace;
        private boolean rejectWrites;
        private boolean applyThenReject;

        private MemoryAccess(UUID player, ItemStack[] storage) { this.player = player; this.storage = BukkitStagingInventoryPortIntegrationTest.copy(storage); }
        @Override public boolean isPrimaryThread() { return true; }
        @Override public Optional<ItemStack[]> storage(UUID playerId) {
            return player.equals(playerId) ? Optional.of(BukkitStagingInventoryPortIntegrationTest.copy(storage)) : Optional.empty();
        }
        @Override public boolean replaceStorage(UUID playerId, ItemStack[] expected, ItemStack[] replacement) {
            if (!player.equals(playerId) || rejectWrites) return false;
            if (conflictNextReplace) { conflictNextReplace = false; return false; }
            if (!same(storage, expected)) return false;
            storage = BukkitStagingInventoryPortIntegrationTest.copy(replacement);
            if (applyThenReject) { applyThenReject = false; return false; }
            return true;
        }
        private ItemStack[] copy() { return BukkitStagingInventoryPortIntegrationTest.copy(storage); }
    }

    /** Registry-free stack state with the metadata surface used by the production adapter. */
    private static class TestStack extends ItemStack {
        private StackState state;

        private TestStack(Material material, int amount) { this(new StackState(material, amount, null)); }
        private TestStack(Material material, int amount, boolean withMeta) {
            this(new StackState(material, amount, withMeta ? new MetaState() : null));
        }
        private TestStack(StackState state) { this.state = state; }

        private static TestStack equipment(StagingEquipmentDocument document) {
            TestStack stack = new TestStack(Material.IRON_SWORD, 1, true);
            ItemMeta meta = stack.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey("projects", "beta_staging_equipment_payload"),
                    PersistentDataType.BYTE_ARRAY, document.payload());
            stack.setItemMeta(meta);
            return stack;
        }

        @Override public Material getType() { return state.material; }
        @Override public int getAmount() { return state.amount; }
        @Override public void setAmount(int value) { state.amount = value; }
        @Override public boolean isSimilar(ItemStack other) {
            return other instanceof TestStack value && state.material == value.state.material
                    && metaEquals(state.meta, value.state.meta);
        }
        @Override public TestStack clone() { return new TestStack(state.copy()); }
        @Override public boolean hasItemMeta() { return state.meta != null; }
        @Override public ItemMeta getItemMeta() { return state.meta == null ? null : metaProxy(state.meta); }
        @Override public boolean setItemMeta(ItemMeta meta) {
            if (meta == null) { state.meta = null; return true; }
            if (!Proxy.isProxyClass(meta.getClass())) return false;
            InvocationHandler handler = Proxy.getInvocationHandler(meta);
            if (!(handler instanceof MetaHandler value)) return false;
            state.meta = value.state.copy();
            return true;
        }
        @Override public byte[] serializeAsBytes() {
            if (state.meta == null) return new byte[]{(byte) state.amount};
            StoredValue payload = state.meta.values.get(new NamespacedKey("projects", "beta_staging_equipment_payload"));
            return payload != null && payload.value instanceof byte[] bytes ? bytes.clone() : new byte[]{(byte) state.amount};
        }
    }

    /** Adapter factory target; this uses no ItemStack constructor or registry access. */
    private static final class RenderedTestStack extends TestStack {
        private RenderedTestStack() { super(Material.IRON_SWORD, 1, true); }
    }

    private static ItemMeta metaProxy(MetaState state) {
        return (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(), new Class[]{ItemMeta.class},
                new MetaHandler(state));
    }

    private static final class MetaHandler implements InvocationHandler {
        private final MetaState state;
        private MetaHandler(MetaState state) { this.state = state; }
        @Override public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] arguments) {
            Object[] args = arguments == null ? new Object[0] : arguments;
            return switch (method.getName()) {
                case "displayName" -> args.length == 0 ? state.displayName : setDisplayName(args[0]);
                case "lore" -> args.length == 0 ? List.copyOf(state.lore) : setLore(args[0]);
                case "hasDisplayName" -> state.displayName != null;
                case "hasLore" -> !state.lore.isEmpty();
                case "getPersistentDataContainer" -> pdcProxy(state);
                case "clone" -> metaProxy(state.copy());
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "TestItemMeta";
                default -> defaultValue(method.getReturnType());
            };
        }
        private Object setDisplayName(Object value) { state.displayName = (Component) value; return null; }
        @SuppressWarnings("unchecked")
        private Object setLore(Object value) {
            state.lore = value == null ? List.of() : List.copyOf((List<Component>) value);
            return null;
        }
    }

    private static PersistentDataContainer pdcProxy(MetaState state) {
        return (PersistentDataContainer) Proxy.newProxyInstance(PersistentDataContainer.class.getClassLoader(),
                new Class[]{PersistentDataContainer.class}, new PdcHandler(state));
    }

    private static final class PdcHandler implements InvocationHandler {
        private final MetaState state;
        private PdcHandler(MetaState state) { this.state = state; }
        @Override public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] arguments) {
            Object[] args = arguments == null ? new Object[0] : arguments;
            return switch (method.getName()) {
                case "set" -> set(args);
                case "get" -> get(args);
                case "has" -> has(args);
                case "getKeys" -> Set.copyOf(state.values.keySet());
                case "remove" -> { state.values.remove((NamespacedKey) args[0]); yield null; }
                case "isEmpty" -> state.values.isEmpty();
                case "copyTo" -> copyTo(args);
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "TestPersistentDataContainer";
                default -> defaultValue(method.getReturnType());
            };
        }
        private Object set(Object[] args) {
            state.values.put((NamespacedKey) args[0], new StoredValue((PersistentDataType<?, ?>) args[1], copyValue(args[2])));
            return null;
        }
        private Object get(Object[] args) {
            StoredValue value = state.values.get((NamespacedKey) args[0]);
            return value != null && value.type == args[1] ? copyValue(value.value) : null;
        }
        private Object has(Object[] args) {
            StoredValue value = state.values.get((NamespacedKey) args[0]);
            return value != null && (args.length == 1 || value.type == args[1]);
        }
        private Object copyTo(Object[] args) {
            if (args[0] != null && Proxy.isProxyClass(args[0].getClass())
                    && Proxy.getInvocationHandler(args[0]) instanceof PdcHandler target) {
                for (Map.Entry<NamespacedKey, StoredValue> entry : state.values.entrySet()) {
                    if ((boolean) args[1] || !target.state.values.containsKey(entry.getKey())) {
                        target.state.values.put(entry.getKey(), entry.getValue().copy());
                    }
                }
            }
            return null;
        }
    }

    private static final class StackState {
        private final Material material;
        private int amount;
        private MetaState meta;
        private StackState(Material material, int amount, MetaState meta) {
            this.material = material; this.amount = amount; this.meta = meta;
        }
        private StackState copy() { return new StackState(material, amount, meta == null ? null : meta.copy()); }
    }

    private static final class MetaState {
        private Component displayName;
        private List<Component> lore = List.of();
        private final Map<NamespacedKey, StoredValue> values = new HashMap<>();
        private MetaState copy() {
            MetaState copy = new MetaState();
            copy.displayName = displayName;
            copy.lore = List.copyOf(lore);
            values.forEach((key, value) -> copy.values.put(key, value.copy()));
            return copy;
        }
    }

    private record StoredValue(PersistentDataType<?, ?> type, Object value) {
        private StoredValue copy() { return new StoredValue(type, copyValue(value)); }
    }

    private static Object copyValue(Object value) { return value instanceof byte[] bytes ? bytes.clone() : value; }
    private static boolean metaEquals(MetaState first, MetaState second) {
        if (first == null || second == null) return first == second;
        if (!java.util.Objects.equals(first.displayName, second.displayName) || !first.lore.equals(second.lore)
                || !first.values.keySet().equals(second.values.keySet())) return false;
        for (NamespacedKey key : first.values.keySet()) {
            StoredValue one = first.values.get(key), two = second.values.get(key);
            if (one.type != two.type || !valuesEqual(one.value, two.value)) return false;
        }
        return true;
    }
    private static boolean valuesEqual(Object first, Object second) {
        return first instanceof byte[] firstBytes && second instanceof byte[] secondBytes
                ? Arrays.equals(firstBytes, secondBytes) : java.util.Objects.equals(first, second);
    }
}
