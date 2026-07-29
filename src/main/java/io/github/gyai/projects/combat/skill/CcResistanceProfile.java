package io.github.gyai.projects.combat.skill;

import java.util.EnumSet;
import java.util.Set;

public record CcResistanceProfile(
        Set<HardControlType> immunities,
        double hardControlDurationMultiplier,
        double statusDurationMultiplier
) {
    public static final CcResistanceProfile DEFAULT =
            new CcResistanceProfile(Set.of(), 1.0, 1.0);

    public CcResistanceProfile {
        immunities = immunities == null || immunities.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(immunities));
        if (!Double.isFinite(hardControlDurationMultiplier)
                || hardControlDurationMultiplier < 0.0) {
            throw new IllegalArgumentException(
                    "hardControlDurationMultiplier must be finite and non-negative");
        }
        if (!Double.isFinite(statusDurationMultiplier)
                || statusDurationMultiplier < 0.0) {
            throw new IllegalArgumentException(
                    "statusDurationMultiplier must be finite and non-negative");
        }
    }

    public boolean immuneTo(HardControlType type) {
        return immunities.contains(type);
    }
}
