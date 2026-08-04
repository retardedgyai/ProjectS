package io.github.gyai.projects.network.beta;

import java.util.UUID;

public record BetaCommandResult(Status status, UUID requestId, String detail, boolean terminal) {
    public enum Status {
        ACCEPTED,
        UNSUPPORTED,
        FEATURE_DISABLED,
        PERMISSION_DENIED,
        CAPABILITY_DENIED,
        STALE_REVISION,
        DUPLICATE,
        RATE_LIMITED,
        INVALID_CURRENT_STATE,
        TRANSACTION_REJECTED,
        MALFORMED,
        CLOSED
    }

    public BetaCommandResult {
        if (status == null || requestId == null) throw new IllegalArgumentException("Invalid result");
        detail = detail == null ? "" : detail;
        if (detail.length() > 256) detail = detail.substring(0, 256);
    }

    public BetaCommandResult asReplay() {
        return new BetaCommandResult(Status.DUPLICATE, requestId, status.name(), terminal);
    }
}
