package io.github.gyai.projects.combat.classsystem;

import io.github.gyai.projects.skill.SkillManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class WarriorController implements ClassController {
    private final SkillManager skillManager;
    private final WarriorCombatManager combatManager;

    public WarriorController(
            SkillManager skillManager,
            WarriorCombatManager combatManager
    ) {
        this.skillManager = skillManager;
        this.combatManager = combatManager;
    }

    @Override
    public void handle(Player player, SkillSlot input) {
        if (input == SkillSlot.SKILL_Q) {
            skillManager.useSkill(player, "spin_slash");
            return;
        }
        player.sendActionBar(Component.text(
                "ウォーリアースキルは未実装です", NamedTextColor.YELLOW));
    }

    @Override
    public void reset(Player player) {
        combatManager.reset(player);
    }

    @Override
    public Component getSelectionHud(Player player) {
        return Component.text(
                "[Q] 回転斬り（仮） | その他のスキルは未実装です",
                NamedTextColor.GOLD);
    }
}
