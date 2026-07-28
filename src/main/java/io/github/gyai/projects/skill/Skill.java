package io.github.gyai.projects.skill;

import org.bukkit.entity.Player;

public interface Skill {
    String getId();

    String getDisplayName();

    double getBaseCooldownSeconds();

    int getResourceCost();

    void execute(Player player);
}
