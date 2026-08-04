package io.github.gyai.projects.network.beta;

import java.util.Optional;

public record BetaProtocolDecodeResult<T>(Status status, T value, String detail) {
    public enum Status {
        SUCCESS,
        MALFORMED,
        OVERSIZED,
        UNSUPPORTED_VERSION,
        UNKNOWN_CAPABILITY,
        UNKNOWN_OPCODE
    }

    public BetaProtocolDecodeResult {
        if (status == null) throw new IllegalArgumentException("Status is required");
        detail = detail == null ? "" : detail;
        if (detail.length() > 256) detail = detail.substring(0, 256);
        if (status == Status.SUCCESS && value == null) {
            throw new IllegalArgumentException("Successful decode requires a value");
        }
    }

    public static <T> BetaProtocolDecodeResult<T> success(T value) {
        return new BetaProtocolDecodeResult<>(Status.SUCCESS, value, "");
    }

    public static <T> BetaProtocolDecodeResult<T> failure(Status status, String detail) {
        return new BetaProtocolDecodeResult<>(status, null, detail);
    }

    public Optional<T> decoded() {
        return Optional.ofNullable(value);
    }
}
