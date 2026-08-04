package io.github.gyai.projects.equipment;

import java.util.Objects;
import java.util.UUID;

public record CrafterIdentity(UUID playerId, String displaySnapshot) {
    public CrafterIdentity {
        Objects.requireNonNull(playerId, "playerId");
        displaySnapshot = MetadataIds.requireBoundedText(
                "displaySnapshot", displaySnapshot, 256);
    }
}
