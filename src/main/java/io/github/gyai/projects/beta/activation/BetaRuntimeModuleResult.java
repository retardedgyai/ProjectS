package io.github.gyai.projects.beta.activation;

public record BetaRuntimeModuleResult(
        boolean success,
        BetaRuntimeModuleState state,
        String detail
) {
    public static final int MAXIMUM_DETAIL_LENGTH = 256;

    public BetaRuntimeModuleResult {
        if (state == null) throw new IllegalArgumentException("Module state is required");
        detail = bounded(detail);
        if (success && state == BetaRuntimeModuleState.FAILED) {
            throw new IllegalArgumentException("Successful module result cannot be failed");
        }
    }

    public static BetaRuntimeModuleResult ready() {
        return new BetaRuntimeModuleResult(true, BetaRuntimeModuleState.READY, "ready");
    }

    public static BetaRuntimeModuleResult running() {
        return new BetaRuntimeModuleResult(true, BetaRuntimeModuleState.RUNNING, "running");
    }

    public static BetaRuntimeModuleResult stopped() {
        return new BetaRuntimeModuleResult(true, BetaRuntimeModuleState.STOPPED, "stopped");
    }

    public static BetaRuntimeModuleResult failure(String detail) {
        return new BetaRuntimeModuleResult(false, BetaRuntimeModuleState.FAILED, detail);
    }

    static String bounded(String value) {
        String text = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        return text.length() <= MAXIMUM_DETAIL_LENGTH
                ? text : text.substring(0, MAXIMUM_DETAIL_LENGTH);
    }
}
