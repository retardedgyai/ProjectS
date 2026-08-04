package io.github.gyai.projects.item.compatibility;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public record LegacyItemView(
        String itemId, String materialIdentity,
        OptionalInt enhancementLevel, boolean broken,
        OptionalDouble attackPowerBonus, OptionalDouble attackSpeedBonus
) {
    public LegacyItemView {
        if (itemId == null || itemId.isBlank() || itemId.length() > 128) {
            throw new IllegalArgumentException("legacy item ID is missing or oversized");
        }
        if (materialIdentity == null || materialIdentity.isBlank()
                || materialIdentity.length() > 128) {
            throw new IllegalArgumentException("material identity is missing or oversized");
        }
        enhancementLevel = enhancementLevel == null ? OptionalInt.empty() : enhancementLevel;
        attackPowerBonus = attackPowerBonus == null ? OptionalDouble.empty() : attackPowerBonus;
        attackSpeedBonus = attackSpeedBonus == null ? OptionalDouble.empty() : attackSpeedBonus;
    }
    /** Legacy reads never synthesize a mutable item identity. */
    public Optional<java.util.UUID> instanceId() { return Optional.empty(); }
}
