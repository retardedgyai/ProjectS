package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentModSlot;
import io.github.gyai.projects.equipment.EquipmentRarity;
import io.github.gyai.projects.equipment.EquipmentTier;
import io.github.gyai.projects.mod.ModDefinition;
import io.github.gyai.projects.mod.ModRank;
import io.github.gyai.projects.mod.UnknownModEntry;
import io.github.gyai.projects.beta.activation.track3.infrastructure.BukkitStagingInventoryPortIntegrationTest;
import io.github.gyai.projects.beta.activation.track3.infrastructure.StagingEquipmentInspectionPresentationTest;

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
        duplicateModIdsAreNeverInsertedAcrossSlots();
        ineligibleStagingFixtureNeverConsumesModRng();
        uuidIsGeneratedOnlyAtCommitAndOnlyOnce();
        auditWritesOnlyToTheStagingTransactionRoot();
        StagingEquipmentInspectionPresentationTest.runAll();
        BukkitStagingInventoryPortIntegrationTest.runAll();
        Track3CraftFailureSafetyTest.runAll();
        Track3FinalizedOutputRecoveryTest.runAll();
        io.github.gyai.projects.dev.DevMenuStagingHolderStateTest.runAll();
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
        String rendered = StagingEquipmentInspectionFormatter.format(decoded.item());
        assert rendered.contains("ID=" + item.itemId()) && rendered.contains("Tier=")
                && rendered.contains("ILv=") && rendered.contains("Rarity=")
                && rendered.contains("Category=") && rendered.contains("Slot=")
                && rendered.contains("UNKNOWN / 効果無効");
    }

    private static void duplicateModIdsAreNeverInsertedAcrossSlots() {
        EquipmentItemV1 preview = StagingEconomyCatalog.previewBlade(EquipmentTier.T1);
        var roller = new StagingModRollService(StagingModRollService.defaultCandidates(), () -> .25);
        var known = roller.resolve(preview).modSlots().getFirst().entry().orElseThrow();
        EquipmentItemV1 knownDuplicate = withSlots(preview, List.of(
                new EquipmentModSlot(0, Optional.of(known)), EquipmentModSlot.empty(1)));
        java.util.concurrent.atomic.AtomicInteger rolls = new java.util.concurrent.atomic.AtomicInteger();
        EquipmentItemV1 knownResult = new StagingModRollService(
                StagingModRollService.defaultCandidates(), () -> {
                    rolls.incrementAndGet(); return .25;
                }).resolve(knownDuplicate);
        assert knownResult.equals(knownDuplicate) && rolls.get() == 0
                : "known duplicate MOD was inserted into another slot";

        UnknownModEntry opaque = new UnknownModEntry(0, "future-mod", 99,
                StagingModRollService.KEEN_EDGE, new byte[]{1, 2, 3});
        EquipmentItemV1 opaqueDuplicate = withSlots(preview, List.of(
                new EquipmentModSlot(0, Optional.of(opaque)), EquipmentModSlot.empty(1)));
        EquipmentItemV1 opaqueResult = new StagingModRollService(
                StagingModRollService.defaultCandidates(), () -> .25).resolve(opaqueDuplicate);
        assert opaqueResult.equals(opaqueDuplicate)
                && opaqueResult.modSlots().get(1).entry().isEmpty()
                : "opaque duplicate MOD was inserted into another slot";
    }

    private static EquipmentItemV1 withSlots(EquipmentItemV1 base, List<EquipmentModSlot> slots) {
        return new EquipmentItemV1(base.schemaVersion(), base.itemId(), base.category(), base.slot(),
                base.tier(), base.itemLevel(), EquipmentRarity.UNCOMMON, base.quality(), base.baseStatRolls(),
                slots, base.crafter(), base.enhancementLevel(), base.broken(), base.binding(),
                base.tradePolicy(), base.instanceId());
    }

    private static void ineligibleStagingFixtureNeverConsumesModRng() {
        EquipmentItemV1 preview = StagingEconomyCatalog.previewBlade(EquipmentTier.T1);
        assertNoRoll(preview, EquipmentTier.T1, 2, StagingModRollService.defaultCandidates(),
                "ILv other than the staging fixture ILv 1 accepted a MOD");
        assertNoRoll(preview, EquipmentTier.T2, 16, StagingModRollService.defaultCandidates(),
                "wrong tier accepted a staging T1 MOD");

        ModDefinition source = StagingModRollService.defaultCandidates().getFirst().definition();
        ModDefinition wrongRank = new ModDefinition(source.schemaVersion(), source.modId(),
                ModRank.RANK_2, source.allowedSlots(), source.requiredTags(), source.excludedTags(),
                source.tagMatchPolicy(), source.statId(), source.minimumValue(), source.maximumValue(),
                source.stackingLayer(), source.source(), source.display(), source.definitionRevision());
        assertNoRoll(preview, EquipmentTier.T1, 1,
                List.of(new StagingModRollService.Candidate(wrongRank, 1.0)),
                "wrong rank candidate consumed RNG or populated a MOD");
    }

    private static void assertNoRoll(EquipmentItemV1 base, EquipmentTier tier, int itemLevel,
                                     List<StagingModRollService.Candidate> candidates, String message) {
        EquipmentItemV1 mutated = new EquipmentItemV1(base.schemaVersion(), base.itemId(),
                base.category(), base.slot(), tier, itemLevel, base.rarity(), base.quality(),
                base.baseStatRolls(), base.modSlots(), base.crafter(), base.enhancementLevel(),
                base.broken(), base.binding(), base.tradePolicy(), base.instanceId());
        AtomicInteger rolls = new AtomicInteger();
        EquipmentItemV1 result = new StagingModRollService(candidates, () -> {
            rolls.incrementAndGet(); return .25;
        }).resolve(mutated);
        assert result.equals(mutated) && rolls.get() == 0 : message;
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
        assert result.equipment().orElseThrow().modSlots().getFirst().entry().isPresent()
                : "craft did not resolve the staging MOD after reservation";

        var replay = service.execute(StagingEconomyOperationPort.OperationRequest.action(
                request, access, StagingEconomyOperationPort.OperationKind.CRAFT));
        assert replay.status() == StagingEconomyOperationPort.Status.REPLAYED;
        assert uuidCalls.get() == 1 : "duplicate request allocated another UUID";
        assert replay.equipment().orElseThrow().equals(result.equipment().orElseThrow())
                : "replay did not retain the finalized item";
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
                var names = entries.map(path -> path.getFileName().toString()).toList();
                assert names.stream().anyMatch(name -> name.endsWith(".resolved.yml"));
                assert names.stream().anyMatch(name -> name.endsWith(".terminal.yml"));
                assert names.stream().anyMatch(name -> name.endsWith(".journal"));
                assert names.contains("quarantine");
                assert names.size() == 4 : "bounded audit/journal contents expected: " + names;
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
