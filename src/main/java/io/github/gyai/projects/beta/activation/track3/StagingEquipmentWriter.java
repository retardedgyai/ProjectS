package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentWriteBoundary;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Commit-only staging writer. Preview and construction do not allocate an item UUID. */
public final class StagingEquipmentWriter implements EquipmentWriteBoundary {
    private final UUID playerId;
    private final UUID requestId;
    private final StagingInventoryPort inventory;
    private final StagingInventoryResourceAdapter reservation;
    private final Supplier<UUID> uuidSource;
    private final BoundedStagingOperationJournal journal;
    private final StagingEquipmentCodec codec;
    private UUID generatedId;
    private StagingInventoryPort.CommitResult lastResult;

    public StagingEquipmentWriter(
            UUID playerId,
            UUID requestId,
            StagingInventoryPort inventory,
            StagingInventoryResourceAdapter reservation,
            Supplier<UUID> uuidSource,
            BoundedStagingOperationJournal journal
    ) {
        if (playerId == null || requestId == null || inventory == null
                || reservation == null || uuidSource == null || journal == null) {
            throw new IllegalArgumentException("staging writer input missing");
        }
        this.playerId = playerId;
        this.requestId = requestId;
        this.inventory = inventory;
        this.reservation = reservation;
        this.uuidSource = uuidSource;
        this.journal = journal;
        this.codec = new StagingEquipmentCodec();
    }

    @Override
    public synchronized WriteResult write(WriteRequest request) {
        String expected = "projects:request-" + requestId.toString().replace("-", "");
        if (!expected.equals(request.requestId())
                || !StagingEconomyCatalog.isStagingItem(request.proposedItem().itemId())) {
            return new WriteResult(false, request.expectedRevision(), "non-staging-request");
        }
        EquipmentItemV1 committed = request.proposedItem();
        if (committed.instanceId().isEmpty()) {
            if (generatedId == null) {
                generatedId = Optional.ofNullable(uuidSource.get())
                        .orElseThrow(() -> new IllegalStateException("UUID source returned null"));
            }
            committed = copyWithIdentity(committed, generatedId);
        }
        // This precedes the live inventory mutation. A retry can therefore
        // project the same identity even when commit acknowledgement is lost.
        journal.recordFinalizedEquipment(requestId, committed);
        StagingEquipmentDocument document = codec.encode(committed, request.expectedRevision() + 1);
        lastResult = inventory.commitEquipment(
                playerId, requestId, reservation.currentReservation(),
                request.expectedRevision(), document);
        return new WriteResult(lastResult.committed(), lastResult.revision(), lastResult.status());
    }

    public synchronized Optional<EquipmentItemV1> committedItem() {
        return lastResult == null ? Optional.empty() : lastResult.equipment();
    }

    private static EquipmentItemV1 copyWithIdentity(EquipmentItemV1 source, UUID instanceId) {
        return new EquipmentItemV1(
                source.schemaVersion(), source.itemId(), source.category(), source.slot(),
                source.tier(), source.itemLevel(), source.rarity(), source.quality(),
                source.baseStatRolls(), source.modSlots(), source.crafter(),
                source.enhancementLevel(), source.broken(), source.binding(),
                source.tradePolicy(), Optional.of(instanceId));
    }
}
