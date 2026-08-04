package io.github.gyai.projects.participation;

import java.util.Objects;
import java.util.UUID;

public record EncounterId(UUID value) {
    public EncounterId {
        Objects.requireNonNull(value, "value");
    }
}
