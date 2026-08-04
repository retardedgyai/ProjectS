package io.github.gyai.projects.equipment;

/** Proposed persistence boundary only; Wave 1 deliberately provides no implementation. */
public interface EquipmentWriteBoundary {
    WriteResult write(WriteRequest request);

    record WriteRequest(EquipmentItemV1 proposedItem, long expectedRevision, String requestId) {
        public WriteRequest {
            java.util.Objects.requireNonNull(proposedItem, "proposedItem");
            if (expectedRevision < 0) throw new IllegalArgumentException("expectedRevision must be non-negative");
            requestId = MetadataIds.requireCanonical("requestId", requestId);
        }
    }
    record WriteResult(boolean committed, long revision, String status) {
        public WriteResult {
            if (revision < 0) throw new IllegalArgumentException("revision must be non-negative");
            status = MetadataIds.requireBoundedText("status", status, 128);
        }
    }
}
