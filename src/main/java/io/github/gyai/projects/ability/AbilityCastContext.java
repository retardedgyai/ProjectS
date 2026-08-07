package io.github.gyai.projects.ability;

import io.github.gyai.projects.transaction.DomainId;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AbilityCastContext(UUID castId, String abilityId, EntityRef source,
                                 SourceKind sourceKind, Origin origin,
                                 EntityRef primaryTarget, Map<String, String> metadata) {
    public AbilityCastContext {
        Objects.requireNonNull(castId, "castId");
        DomainId.requireNamespaced(abilityId, "ability id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceKind, "sourceKind");
        Objects.requireNonNull(origin, "origin");
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
    public record EntityRef(UUID id) { public EntityRef { Objects.requireNonNull(id, "id"); } }
    public record Origin(UUID worldId, String dimension, double x, double y, double z) {
        public Origin { Objects.requireNonNull(worldId, "worldId"); if (dimension == null || dimension.isBlank()
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Invalid origin"); }
    }
}
