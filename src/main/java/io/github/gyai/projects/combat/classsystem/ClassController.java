package io.github.gyai.projects.combat.classsystem;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public interface ClassController {
    void handle(Player player, SkillSlot input);
    void reset(Player player);
    Component getSelectionHud(Player player);
}
