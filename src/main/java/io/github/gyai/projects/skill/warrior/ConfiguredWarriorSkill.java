package io.github.gyai.projects.skill.warrior;

import io.github.gyai.projects.skill.Skill;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.function.Function;

public final class ConfiguredWarriorSkill implements Skill {
    private final String id;
    private final String displayName;
    private final boolean enabled;
    private final double cooldownSeconds;
    private final int resourceCost;
    private final Function<Player, Optional<PreparedUse>> preparation;

    public ConfiguredWarriorSkill(
            String id,
            String displayName,
            boolean enabled,
            double cooldownSeconds,
            int resourceCost,
            Function<Player, Optional<PreparedUse>> preparation
    ) {
        this.id = id;
        this.displayName = displayName;
        this.enabled = enabled;
        this.cooldownSeconds = cooldownSeconds;
        this.resourceCost = resourceCost;
        this.preparation = preparation;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public double getBaseCooldownSeconds() {
        return cooldownSeconds;
    }

    @Override
    public int getResourceCost() {
        return resourceCost;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Optional<PreparedUse> prepare(Player player) {
        return enabled ? preparation.apply(player) : Optional.empty();
    }
}
