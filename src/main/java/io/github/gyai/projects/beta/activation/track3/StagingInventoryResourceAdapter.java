package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.operation.EquipmentResourcePort;
import io.github.gyai.projects.equipment.operation.OperationResourcePlan;
import io.github.gyai.projects.crafting.OutputProposal;
import io.github.gyai.projects.transaction.InventoryCapacityProposal;
import io.github.gyai.projects.transaction.ReservationToken;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.transaction.TransactionStage;

import java.util.Optional;
import java.util.UUID;

/** Track E resource port backed by the staging inventory boundary. */
public final class StagingInventoryResourceAdapter implements EquipmentResourcePort {
    private final UUID playerId;
    private final StagingInventoryPort inventory;
    private ReservationToken reservation;

    public StagingInventoryResourceAdapter(UUID playerId, StagingInventoryPort inventory) {
        if (playerId == null || inventory == null) throw new IllegalArgumentException("adapter input missing");
        this.playerId = playerId;
        this.inventory = inventory;
    }

    @Override
    public Optional<InventoryCapacityProposal> validate(
            TransactionRequest request,
            OperationResourcePlan resources
    ) {
        return inventory.validate(playerId, request, resources);
    }

    public StagingInventoryPort.ResourceValidation validateResource(
            TransactionRequest request,
            OperationResourcePlan resources,
            OutputProposal output
    ) {
        return inventory.validateResource(playerId, request, resources, output);
    }

    @Override
    public synchronized ReservationToken reserve(
            TransactionRequest request,
            OperationResourcePlan resources,
            InventoryCapacityProposal capacity
    ) {
        if (reservation != null) throw new IllegalStateException("reservation already acquired");
        reservation = inventory.reserve(playerId, request, resources, capacity);
        return reservation;
    }

    @Override
    public void consume(
            TransactionRequest request,
            OperationResourcePlan resources,
            ReservationToken token
    ) {
        inventory.consume(playerId, request, resources, require(token));
    }

    @Override
    public void rollback(
            TransactionRequest request,
            OperationResourcePlan resources,
            ReservationToken token,
            TransactionStage lastCompletedStage
    ) {
        inventory.rollback(playerId, request.requestId(), require(token), lastCompletedStage);
    }

    public synchronized ReservationToken currentReservation() {
        if (reservation == null) throw new IllegalStateException("reservation not acquired");
        return reservation;
    }

    private synchronized ReservationToken require(ReservationToken token) {
        if (reservation == null || !reservation.equals(token)) {
            throw new IllegalStateException("reservation token mismatch");
        }
        return reservation;
    }
}
