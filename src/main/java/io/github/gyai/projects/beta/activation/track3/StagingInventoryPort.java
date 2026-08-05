package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.operation.OperationResourcePlan;
import io.github.gyai.projects.transaction.InventoryCapacityProposal;
import io.github.gyai.projects.transaction.ReservationToken;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.transaction.TransactionStage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Main-thread inventory boundary. Implementations must make commit atomic. */
public interface StagingInventoryPort extends AutoCloseable {
    void openSession(UUID playerId);

    Optional<InventoryCapacityProposal> validate(
            UUID playerId,
            TransactionRequest request,
            OperationResourcePlan resources);

    ReservationToken reserve(
            UUID playerId,
            TransactionRequest request,
            OperationResourcePlan resources,
            InventoryCapacityProposal capacity);

    void consume(
            UUID playerId,
            TransactionRequest request,
            OperationResourcePlan resources,
            ReservationToken reservation);

    CommitResult commitEquipment(
            UUID playerId,
            UUID requestId,
            ReservationToken reservation,
            long expectedRevision,
            StagingEquipmentDocument document);

    CommitResult commitResource(
            UUID playerId,
            UUID requestId,
            ReservationToken reservation,
            String resourceId,
            long quantity);

    void rollback(
            UUID playerId,
            UUID requestId,
            ReservationToken reservation,
            TransactionStage lastCompletedStage);

    InventorySnapshot snapshot(UUID playerId);

    void logout(UUID playerId);

    @Override
    void close();

    record CommitResult(
            boolean committed,
            long revision,
            Optional<EquipmentItemV1> equipment,
            String status
    ) {
        public CommitResult {
            if (revision < 0) throw new IllegalArgumentException("revision must be non-negative");
            equipment = equipment == null ? Optional.empty() : equipment;
            status = status == null ? "" : status;
            if (status.length() > 128) throw new IllegalArgumentException("status is oversized");
        }
    }

    record InventorySnapshot(
            long revision,
            Map<String, Long> resources,
            List<EquipmentItemV1> equipment,
            int activeReservations
    ) {
        public InventorySnapshot {
            if (revision < 0 || activeReservations < 0) {
                throw new IllegalArgumentException("invalid inventory snapshot");
            }
            resources = Map.copyOf(resources == null ? Map.of() : resources);
            equipment = List.copyOf(equipment == null ? List.of() : equipment);
        }
    }
}
