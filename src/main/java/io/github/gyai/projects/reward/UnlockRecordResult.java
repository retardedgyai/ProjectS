package io.github.gyai.projects.reward;

public record UnlockRecordResult(Status status, String reason) {
    public UnlockRecordResult {
        if (status == null) throw new IllegalArgumentException("status is required");
        reason = reason == null ? "" : reason;
    }
    public enum Status { RECORDED, ALREADY_RECORDED, STALE, REJECTED }
}
