package io.github.gyai.projects.beta.activation.track3.infrastructure;

import io.github.gyai.projects.beta.activation.track3.BoundedStagingInventory;
import io.github.gyai.projects.beta.activation.track3.StagingEquipmentDocument;
import io.github.gyai.projects.beta.activation.track3.StagingInventoryPort;
import io.github.gyai.projects.equipment.operation.OperationResourcePlan;
import io.github.gyai.projects.transaction.InventoryCapacityProposal;
import io.github.gyai.projects.transaction.ReservationToken;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.transaction.TransactionStage;

import java.util.Optional;
import java.util.UUID;

/**
 * Production main-thread port. The Bukkit bridge establishes live inventory
 * ownership for every session; immutable transaction state remains bounded and
 * is never backed by production PlayerData files.
 */
public final class BukkitStagingInventoryPort implements StagingInventoryPort {
    private final BukkitStagingInventoryBridge bridge;
    private final BoundedStagingInventory transactions = new BoundedStagingInventory();

    public BukkitStagingInventoryPort(BukkitStagingInventoryBridge bridge) {
        this.bridge = java.util.Objects.requireNonNull(bridge);
    }

    @Override public void openSession(UUID playerId) {
        if (bridge.snapshot(playerId).isEmpty()) throw new IllegalStateException("staging player offline");
        transactions.openSession(playerId);
    }
    @Override public Optional<InventoryCapacityProposal> validate(UUID playerId,
            TransactionRequest request, OperationResourcePlan resources) {
        if (bridge.snapshot(playerId).isEmpty()) return Optional.empty();
        return transactions.validate(playerId, request, resources);
    }
    @Override public ReservationToken reserve(UUID playerId, TransactionRequest request,
            OperationResourcePlan resources, InventoryCapacityProposal capacity) {
        return transactions.reserve(playerId, request, resources, capacity);
    }
    @Override public void consume(UUID playerId, TransactionRequest request,
            OperationResourcePlan resources, ReservationToken reservation) {
        transactions.consume(playerId, request, resources, reservation);
    }
    @Override public CommitResult commitEquipment(UUID playerId, UUID requestId,
            ReservationToken reservation, long expectedRevision,
            StagingEquipmentDocument document) {
        if (bridge.snapshot(playerId).isEmpty()) return new CommitResult(
                false, expectedRevision, Optional.empty(), "staging-player-offline");
        return transactions.commitEquipment(playerId, requestId, reservation,
                expectedRevision, document);
    }
    @Override public CommitResult commitResource(UUID playerId, UUID requestId,
            ReservationToken reservation, String resourceId, long quantity) {
        if (bridge.snapshot(playerId).isEmpty()) return new CommitResult(
                false, snapshot(playerId).revision(), Optional.empty(), "staging-player-offline");
        return transactions.commitResource(playerId, requestId, reservation, resourceId, quantity);
    }
    @Override public void rollback(UUID playerId, UUID requestId,
            ReservationToken reservation, TransactionStage lastCompletedStage) {
        transactions.rollback(playerId, requestId, reservation, lastCompletedStage);
    }
    @Override public InventorySnapshot snapshot(UUID playerId) { return transactions.snapshot(playerId); }
    @Override public void logout(UUID playerId) { transactions.logout(playerId); }
    @Override public void close() { transactions.close(); }
    public BukkitStagingInventoryBridge bridge() { return bridge; }
}
