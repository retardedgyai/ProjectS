package io.github.gyai.projects.gathering;

import io.github.gyai.projects.transaction.DomainId;

import java.util.Objects;

public record ResourceDefinitionV1(
        int schemaVersion,
        long revision,
        String resourceId,
        String displayKey
) {
    public static final int SCHEMA_VERSION = 1;

    public ResourceDefinitionV1 {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported resource schema");
        }
        if (revision < 0) throw new IllegalArgumentException("Negative revision");
        resourceId = DomainId.requireNamespaced(resourceId, "resource ID");
        displayKey = DomainId.requireKey(displayKey, "display key");
        Objects.requireNonNull(displayKey, "displayKey");
    }
}
