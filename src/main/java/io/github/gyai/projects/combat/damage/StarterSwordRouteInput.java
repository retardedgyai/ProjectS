package io.github.gyai.projects.combat.damage;

import java.util.Objects;

public record StarterSwordRouteInput(
        boolean authoritativeEnabled,
        String itemId,
        DamageType damageType,
        DamageKind damageKind,
        DamageMode damageMode,
        AttackMetadata attackMetadata,
        boolean critical,
        double shieldAmount,
        boolean specialState
) {
    public StarterSwordRouteInput {
        itemId = itemId == null ? "" : itemId;
        damageType = Objects.requireNonNull(damageType, "damageType");
        damageKind = Objects.requireNonNull(damageKind, "damageKind");
        damageMode = Objects.requireNonNull(damageMode, "damageMode");
        attackMetadata = attackMetadata == null
                ? AttackMetadata.EMPTY : attackMetadata;
    }
}
