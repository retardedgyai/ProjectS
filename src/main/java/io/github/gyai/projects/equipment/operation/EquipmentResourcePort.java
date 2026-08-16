package io.github.gyai.projects.equipment.operation;

import io.github.gyai.projects.transaction.InventoryCapacityProposal;
import io.github.gyai.projects.transaction.ReservationToken;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.transaction.TransactionStage;

import java.util.Optional;

public interface EquipmentResourcePort {
    Optional<InventoryCapacityProposal> validate(
            TransactionRequest request,
            OperationResourcePlan resources);

    ReservationToken reserve(
            TransactionRequest request,
            OperationResourcePlan resources,
            InventoryCapacityProposal capacity);

    void consume(
            TransactionRequest request,
            OperationResourcePlan resources,
            ReservationToken reservation);

    void rollback(
            TransactionRequest request,
            OperationResourcePlan resources,
            ReservationToken reservation,
            TransactionStage lastCompletedStage);
}
