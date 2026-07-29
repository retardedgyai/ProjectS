package io.github.gyai.projects.combat.classsystem;

import io.github.gyai.projects.skill.SkillManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class WarriorController implements ClassController {
    private final SkillManager skillManager;
    private final WarriorCombatManager combatManager;
    private final WarriorEffectManager effectManager;
    private final WarriorLoadoutManager loadoutManager;

    public WarriorController(
            SkillManager skillManager,
            WarriorCombatManager combatManager,
            WarriorEffectManager effectManager,
            WarriorLoadoutManager loadoutManager
    ) {
        this.skillManager = skillManager;
        this.combatManager = combatManager;
        this.effectManager = effectManager;
        this.loadoutManager = loadoutManager;
    }

    @Override
    public void handle(Player player, SkillSlot input) {
        WarriorLoadoutSlot slot =
                WarriorLoadoutSlot.fromInternalSlot(input);
        skillManager.useSkill(
                player, loadoutManager.get(player).skill(slot));
    }

    @Override
    public void reset(Player player) {
        effectManager.clearPlayer(player, true);
        combatManager.reset(player);
    }

    @Override
    public Component getSelectionHud(Player player) {
        WarriorLoadout loadout = loadoutManager.get(player);
        return Component.text(
                "[Q] %s | [E] %s | [R] %s | [F] %s".formatted(
                        displayName(loadout.q()),
                        displayName(loadout.e()),
                        displayName(loadout.r()),
                        displayName(loadout.f())),
                NamedTextColor.GOLD);
    }

    public WarriorLoadoutManager getLoadoutManager() {
        return loadoutManager;
    }

    public WarriorEffectManager getEffectManager() {
        return effectManager;
    }

    public WarriorCombatManager getCombatManager() {
        return combatManager;
    }

    private String displayName(String skillId) {
        var skill = skillManager.getSkill(skillId);
        return skill == null ? "未実装" : skill.getDisplayName();
    }
}
