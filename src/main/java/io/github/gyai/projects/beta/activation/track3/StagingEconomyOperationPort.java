package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.transaction.TransactionAuditResult;

import java.util.Optional;
import java.util.UUID;

/** Operator-facing staging economy boundary; no command registration occurs here. */
public interface StagingEconomyOperationPort {
    OperationResult execute(OperationRequest request);

    void selectEnhancementOutcome(
            StagingOperationAccess access,
            io.github.gyai.projects.enhancement.v2.EnhancementOutcome outcome);

    StagingInventoryPort.InventorySnapshot status(UUID playerId);

    void logout(UUID playerId);

    enum OperationKind {
        GIVE,
        REFINE,
        CRAFT,
        PROMOTE,
        ENHANCE,
        BREAK,
        REPAIR
    }

    record OperationRequest(
            UUID requestId,
            StagingOperationAccess access,
            OperationKind kind,
            Optional<String> requestedItemId,
            long requestedQuantity
    ) {
        public OperationRequest {
            if (requestId == null || access == null || kind == null) {
                throw new IllegalArgumentException("operation request input missing");
            }
            requestedItemId = requestedItemId == null ? Optional.empty() : requestedItemId;
            if (requestedQuantity < 0 || requestedQuantity > 1_000_000) {
                throw new IllegalArgumentException("requested quantity is invalid");
            }
        }

        public static OperationRequest action(
                UUID requestId,
                StagingOperationAccess access,
                OperationKind kind
        ) {
            return new OperationRequest(requestId, access, kind, Optional.empty(), 0);
        }
    }

    record OperationResult(
            Status status,
            String detail,
            Optional<TransactionAuditResult> transaction,
            Optional<EquipmentItemV1> equipment
    ) {
        public OperationResult {
            if (status == null) throw new IllegalArgumentException("operation status is required");
            detail = detail == null ? "" : detail;
            if (detail.length() > 256) detail = detail.substring(0, 256);
            transaction = transaction == null ? Optional.empty() : transaction;
            equipment = equipment == null ? Optional.empty() : equipment;
        }

        public static OperationResult rejected(String detail) {
            return new OperationResult(Status.REJECTED, detail, Optional.empty(), Optional.empty());
        }
    }

    enum Status {
        COMMITTED,
        REPLAYED,
        REJECTED,
        ROLLED_BACK,
        COMMIT_UNCERTAIN,
        FAILED
    }
}
