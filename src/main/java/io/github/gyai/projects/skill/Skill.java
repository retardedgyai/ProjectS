package io.github.gyai.projects.skill;

import org.bukkit.entity.Player;

import java.util.Optional;

public interface Skill {
    String getId();

    String getDisplayName();

    double getBaseCooldownSeconds();

    int getResourceCost();

    default boolean isEnabled() {
        return true;
    }

    Optional<PreparedUse> prepare(Player player);

    record PreparedUse(int resourceCost, Runnable execution) {
        public PreparedUse {
            if (resourceCost < 0) {
                throw new IllegalArgumentException("resourceCost must not be negative");
            }
        }
    }
}
