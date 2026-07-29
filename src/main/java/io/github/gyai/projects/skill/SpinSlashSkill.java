package io.github.gyai.projects.skill;

import io.github.gyai.projects.skill.warrior.WarriorSkillSupport;
import io.github.gyai.projects.manager.BalanceMath;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class SpinSlashSkill implements Skill {
    private final WarriorSkillSupport support;
    private final boolean enabled;
    private final double cooldown;
    private final double radius;

    public SpinSlashSkill(WarriorSkillSupport support) {
        this.support = support;
        WarriorSkillSupport.SkillConfig config = support.config(getId());
        enabled = config.enabled();
        cooldown = config.number("cooldown", 8, 0, 300);
        radius = config.number("radius", 3, .5, 16);
        double baseDamage = config.number("base-damage", 11, 0, 10_000);
        double attackPowerScaling = config.number(
                "attack-power-scaling", 1.2, 0, 100);
        support.registerDamageBalance(
                getId(), getDisplayName(), baseDamage, attackPowerScaling);
    }

    @Override
    public String getId() {
        return "spin_slash";
    }

    @Override
    public String getDisplayName() {
        return "回転斬り";
    }

    @Override
    public double getBaseCooldownSeconds() {
        return cooldown;
    }

    @Override
    public int getResourceCost() {
        return 0;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Optional<PreparedUse> prepare(Player player) {
        if (!support.validateCaster(player)) return Optional.empty();
        var values = support.damageValues(getId());
        double damage = BalanceMath.skillDamage(
                values.baseDamage(), support.attackPower(player),
                values.attackPowerScaling());
        return Optional.of(new PreparedUse(0, () -> {
            support.play(
                    player,
                    Particle.SWEEP_ATTACK,
                    12,
                    Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                    .8f);
            support.damageTargets(
                    player, support.nearby(player, radius), damage, getId());
        }));
    }
}
