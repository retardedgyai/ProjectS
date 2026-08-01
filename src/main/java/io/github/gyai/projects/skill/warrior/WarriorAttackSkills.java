package io.github.gyai.projects.skill.warrior;

import io.github.gyai.projects.skill.Skill;
import io.github.gyai.projects.skill.SkillManager;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.Optional;

public final class WarriorAttackSkills {
    private WarriorAttackSkills() {
    }

    public static void register(
            SkillManager skillManager,
            WarriorSkillSupport support
    ) {
        WarriorSkillSupport.SkillConfig config =
                support.config("sweeping_slash");
        boolean enabled = config.enabled();
        double cooldown = config.number("cooldown", 6, 0, 300);
        double range = config.number("range", 4.5, .5, 24);
        double angle = config.number("angle", 100, 1, 360);
        double baseDamage = config.number(
                "base-damage", 14, 0, 10_000);
        double scaling = config.number(
                "attack-power-scaling", 1.4, 0, 100);
        support.registerDamageBalance(
                "sweeping_slash", "薙ぎ払い", baseDamage, scaling);

        skillManager.register(new ConfiguredWarriorSkill(
                "sweeping_slash",
                "薙ぎ払い",
                enabled,
                cooldown,
                0,
                player -> {
                    if (!support.validateCaster(player)) {
                        return Optional.empty();
                    }
                    var values = support.damageValues("sweeping_slash");
                    return Optional.of(new Skill.PreparedUse(0, () -> {
                        support.play(
                                player,
                                Particle.SWEEP_ATTACK,
                                18,
                                Sound.ENTITY_PLAYER_ATTACK_STRONG,
                                .9f);
                        support.damageTargets(
                                player,
                                support.cone(player, range, angle, true),
                                values.baseDamage(),
                                values.attackPowerScaling(),
                                "sweeping_slash");
                    }));
                }));
    }
}
