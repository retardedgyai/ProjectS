package io.github.gyai.projects.combat.damage;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable runtime identifiers captured before legacy damage is applied. */
public record DamageShadowRuntimeContext(
        Instant timestamp,
        UUID attackerId,
        UUID targetId,
        DamageShadowTargetType targetType,
        String itemId,
        int enhancementLevel
) {
    public DamageShadowRuntimeContext {
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        attackerId = Objects.requireNonNull(attackerId, "attackerId");
        targetId = Objects.requireNonNull(targetId, "targetId");
        targetType = Objects.requireNonNull(targetType, "targetType");
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        if (enhancementLevel < 0 || enhancementLevel > 30) {
            throw new IllegalArgumentException(
                    "enhancementLevel must be between 0 and 30");
        }
    }
}
