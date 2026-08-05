package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentModSlot;
import io.github.gyai.projects.equipment.EquipmentTier;
import io.github.gyai.projects.mod.UnknownModEntry;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class Track3StagingItemWriterTest {
    private Track3StagingItemWriterTest() {
    }

    public static void main(String[] args) {
        catalogAndCodecRoundTripAreBounded();
        unsupportedModsRemainDisabled();
        uuidIsGeneratedOnlyAtCommitAndOnlyOnce();
        auditWritesOnlyToTheStagingTransactionRoot();
    }

    private static void catalogAndCodecRoundTripAreBounded() {
        assert StagingEconomyCatalog.itemIds().equals(java.util.Set.of(
                "projects:staging/iron-ore",
                "projects:staging/iron-ingot",
                "projects:staging/test-blade",
                "projects:staging/test-blade-t2",
                "projects:staging/test-token"));
        assert StagingEconomyCatalog.refineRecipe().inputs().getFirst().quantity() == 2;
        assert StagingEconomyCatalog.refineRecipe().output().quantity() == 1;
        assert StagingEconomyCatalog.craftRecipe().inputs().getFirst().quantity() == 3;

        EquipmentItemV1 preview = StagingEconomyCatalog.previewBlade(EquipmentTier.T1);
        assert preview.instanceId().isEmpty() : "preview generated UUID";
        StagingEquipmentCodec codec = new StagingEquipmentCodec();
        StagingEquipmentDocument document = codec.encode(preview, 0);
        assert document.payload().length <= StagingEquipmentDocument.MAXIMUM_PAYLOAD_BYTES;
        assert codec.decode(document.payload()).equals(document);
        byte[] modified = document.payload();
        modified[0] ^= 1;
        expectIllegal(() -> codec.decode(modified));
        expectUnsupported(() -> StagingEconomyCatalog.itemIds().clear());
    }

    private static void unsupportedModsRemainDisabled() {
        EquipmentItemV1 base = StagingEconomyCatalog.previewBlade(EquipmentTier.T1);
        UnknownModEntry unsupported = new UnknownModEntry(
                0, "future-mod", 99, "future:opaque", new byte[]{1, 2, 3});
        EquipmentItemV1 item = new EquipmentItemV1(
                base.schemaVersion(), base.itemId(), base.category(), base.slot(),
                base.tier(), base.itemLevel(), base.rarity(), base.quality(),
                base.baseStatRolls(), List.of(new EquipmentModSlot(
                0, Optional.of(unsupported))), base.crafter(), base.enhancementLevel(),
                base.broken(), base.binding(), base.tradePolicy(), base.instanceId());
        StagingEquipmentDocument decoded = new StagingEquipmentCodec().decode(
                new StagingEquipmentCodec().encode(item, 3).payload());
        assert decoded.item().equals(item);
        assert !decoded.item().modSlots().getFirst().entry().orElseThrow().effectEnabled();
        byte[] opaque = ((UnknownModEntry) decoded.item().modSlots().getFirst()
                .entry().orElseThrow()).payload();
        opaque[0] = 99;
        assert ((UnknownModEntry) decoded.item().modSlots().getFirst()
                .entry().orElseThrow()).payload()[0] == 1 : "opaque payload leaked";
    }

    private static void uuidIsGeneratedOnlyAtCommitAndOnlyOnce() {
        UUID player = uuid(1);
        UUID request = uuid(2);
        AtomicInteger uuidCalls = new AtomicInteger();
        UUID generated = uuid(100);
        BoundedStagingInventory inventory = new BoundedStagingInventory();
        inventory.openSession(player);
        inventory.seedResource(player, StagingEconomyCatalog.IRON_INGOT, 3);
        BoundedStagingOperationJournal journal = new BoundedStagingOperationJournal(32);
        StagingInventoryTransactionAdapter adapter = new StagingInventoryTransactionAdapter(
                inventory, journal, Clock.fixed(
                Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC),
                () -> {
                    uuidCalls.incrementAndGet();
                    return generated;
                });
        StagingEconomyService service = new StagingEconomyService(
                inventory, journal, adapter, new StagingEnhancementOutcomeRegistry());
        service.setGroupRunning(
                StagingEconomyService.OperationGroup.GATHERING_CRAFTING, true);
        StagingOperationAccess access = Track3TestFixtures.access(player);

        EquipmentItemV1 preview = StagingEconomyCatalog.previewBlade(EquipmentTier.T1);
        assert preview.instanceId().isEmpty();
        var result = service.execute(StagingEconomyOperationPort.OperationRequest.action(
                request, access, StagingEconomyOperationPort.OperationKind.CRAFT));
        assert result.status() == StagingEconomyOperationPort.Status.COMMITTED : result;
        assert uuidCalls.get() == 1;
        assert result.equipment().orElseThrow().instanceId().orElseThrow().equals(generated);

        var replay = service.execute(StagingEconomyOperationPort.OperationRequest.action(
                request, access, StagingEconomyOperationPort.OperationKind.CRAFT));
        assert replay.status() == StagingEconomyOperationPort.Status.REPLAYED;
        assert uuidCalls.get() == 1 : "duplicate request allocated another UUID";
        assert inventory.snapshot(player).equipment().size() == 1;
        service.close();
    }

    private static void auditWritesOnlyToTheStagingTransactionRoot() {
        Path temporary = null;
        try {
            temporary = Files.createTempDirectory("projects-track3-");
            Path pluginData = temporary.resolve("plugins").resolve("ProjectS");
            StagingEconomyPaths paths = StagingEconomyPaths.under(pluginData);
            UUID player = uuid(200);
            BoundedStagingInventory inventory = new BoundedStagingInventory();
            inventory.openSession(player);
            inventory.seedResource(player, StagingEconomyCatalog.IRON_INGOT, 3);
            BoundedStagingOperationJournal journal = new BoundedStagingOperationJournal(
                    32, new FileStagingTransactionAuditSink(paths));
            StagingInventoryTransactionAdapter adapter = new StagingInventoryTransactionAdapter(
                    inventory, journal, Track3TestFixtures.CLOCK, () -> uuid(201));
            StagingEconomyService service = new StagingEconomyService(
                    inventory, journal, adapter, new StagingEnhancementOutcomeRegistry());
            service.setGroupRunning(
                    StagingEconomyService.OperationGroup.GATHERING_CRAFTING, true);
            var result = service.execute(StagingEconomyOperationPort.OperationRequest.action(
                    uuid(202), Track3TestFixtures.access(player),
                    StagingEconomyOperationPort.OperationKind.CRAFT));
            assert result.status() == StagingEconomyOperationPort.Status.COMMITTED;
            assert Files.isDirectory(paths.transactionsDirectory());
            try (var entries = Files.list(paths.transactionsDirectory())) {
                assert entries.count() == 2 : "resolved and terminal audit expected";
            }
            assert !Files.exists(pluginData.resolve("data"));
            service.close();
        } catch (Exception failure) {
            throw new AssertionError(failure);
        } finally {
            if (temporary != null) {
                try (var entries = Files.walk(temporary)) {
                    entries.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try { Files.deleteIfExists(path); }
                        catch (Exception failure) { throw new RuntimeException(failure); }
                    });
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
            }
        }
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }
}
