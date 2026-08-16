package io.github.gyai.projects.transaction;

import java.util.Objects;

public record ReservationToken(String value) {
    public ReservationToken {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("Invalid reservation token");
        }
    }
}
