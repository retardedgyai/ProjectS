package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.crafting.OutputProposal;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.operation.EquipmentOperationParticipant;
import io.github.gyai.projects.equipment.operation.EquipmentOperationPlan;
import io.github.gyai.projects.equipment.operation.OperationResourcePlan;
import io.github.gyai.projects.transaction.TransactionAuditResult;
import io.github.gyai.projects.transaction.TransactionEngine;
import io.github.gyai.projects.transaction.TransactionParticipant;
import io.github.gyai.projects.transaction.TransactionRequest;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Track D adapter shared by all Track 3 staging operations. */
public final class StagingInventoryTransactionAdapter implements AutoCloseable {
    private final StagingInventoryPort inventory;
    private final BoundedStagingOperationJournal journal;
    private final TransactionEngine engine;
    private final Supplier<UUID> itemUuidSource;

    public StagingInventoryTransactionAdapter(
            StagingInventoryPort inventory,
            BoundedStagingOperationJournal journal,
            Clock clock,
            Supplier<UUID> itemUuidSource
    ) {
        if (inventory == null || journal == null || clock == null || itemUuidSource == null) {
            throw new IllegalArgumentException("transaction adapter input missing");
        }
        this.inventory = inventory;
        this.journal = journal;
        this.engine = new TransactionEngine(64, 512, clock);
        this.itemUuidSource = itemUuidSource;
    }

    public Execution executeEquipment(
            UUID playerId,
            EquipmentOperationPlan plan,
            StagingFailurePoint failurePoint
    ) {
        StagingInventoryResourceAdapter resources =
                new StagingInventoryResourceAdapter(playerId, inventory);
        StagingEquipmentWriter writer = new StagingEquipmentWriter(
                playerId, plan.transactionRequest().requestId(), inventory,
                resources, itemUuidSource);
        TransactionParticipant participant = new EquipmentOperationParticipant(
                plan, resources, writer, journal);
        TransactionAuditResult result = engine.execute(plan.transactionRequest(),
                new FailureInjectingTransactionParticipant(participant, failurePoint));
        return new Execution(result, writer.committedItem());
    }

    public Execution executeResource(
            UUID playerId,
            TransactionRequest request,
            OperationResourcePlan resources,
            OutputProposal output,
            StagingFailurePoint failurePoint
    ) {
        StagingInventoryResourceAdapter adapter =
                new StagingInventoryResourceAdapter(playerId, inventory);
        TransactionParticipant participant = new StagingResourceOperationParticipant(
                playerId, request, resources, output, inventory, adapter, journal);
        TransactionAuditResult result = engine.execute(request,
                new FailureInjectingTransactionParticipant(participant, failurePoint));
        return new Execution(result, Optional.empty());
    }

    public int cancelForPlayer(UUID playerId) {
        return engine.cancelForPlayer(playerId);
    }

    @Override
    public void close() {
        engine.close();
    }

    public record Execution(
            TransactionAuditResult result,
            Optional<EquipmentItemV1> committedEquipment
    ) {
        public Execution {
            if (result == null) throw new IllegalArgumentException("transaction result is required");
            committedEquipment = committedEquipment == null
                    ? Optional.empty() : committedEquipment;
        }
    }
}
