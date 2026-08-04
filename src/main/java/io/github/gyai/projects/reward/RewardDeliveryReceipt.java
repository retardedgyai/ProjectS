package io.github.gyai.projects.reward;

public record RewardDeliveryReceipt(Status status, String reason, boolean durable) {
    public RewardDeliveryReceipt {
        if (status == null) throw new IllegalArgumentException("status is required");
        reason = reason == null ? "" : reason;
        if (status == Status.DELIVERED && !durable) {
            throw new IllegalArgumentException("Delivered reward must have durable receipt");
        }
    }

    public enum Status {
        DELIVERED, FULL_INVENTORY, PERSIST_FAILURE, COMMIT_UNCERTAIN, REJECTED
    }
}
