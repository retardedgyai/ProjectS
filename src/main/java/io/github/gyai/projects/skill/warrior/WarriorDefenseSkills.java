package io.github.gyai.projects.skill.warrior;

import io.github.gyai.projects.combat.classsystem.WarriorEffectManager;
import io.github.gyai.projects.skill.Skill;
import io.github.gyai.projects.skill.SkillManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Optional;

public final class WarriorDefenseSkills {
    private WarriorDefenseSkills() {
    }

    public static void register(
            SkillManager skillManager,
            WarriorSkillSupport support,
            WarriorEffectManager effects
    ) {
        registerIndomitable(skillManager, support, effects);
        registerBattlefieldAura(skillManager, support, effects);
        registerEndure(skillManager, support, effects);
    }

    private static void registerIndomitable(
            SkillManager skillManager,
            WarriorSkillSupport support,
            WarriorEffectManager effects
    ) {
        var config = support.config("indomitable_spirit");
        boolean enabled = config.enabled();
        double cooldown = config.number("cooldown", 20, 0, 300);
        double duration = config.number("duration", 5, .1, 60);
        double damageReduction = config.number(
                "damage-reduction", .25, 0, .9);
        double attackSpeedBonus = config.number(
                "attack-speed-bonus", .25, 0, 5);
        skillManager.register(new ConfiguredWarriorSkill(
                "indomitable_spirit", "不屈の闘志", enabled, cooldown, 0,
                player -> {
                    if (!support.validateCaster(player)) {
                        return Optional.empty();
                    }
                    return Optional.of(new Skill.PreparedUse(0, () -> {
                        effects.startIndomitable(
                                player, duration, damageReduction,
                                attackSpeedBonus);
                        support.play(
                                player, Particle.FLAME, 20,
                                Sound.ENTITY_PLAYER_LEVELUP, .7f);
                    }));
                }));
    }

    private static void registerBattlefieldAura(
            SkillManager skillManager,
            WarriorSkillSupport support,
            WarriorEffectManager effects
    ) {
        var config = support.config("battlefield_aura");
        boolean enabled = config.enabled();
        double cooldown = config.number("cooldown", 18, 0, 300);
        double radius = config.number("radius", 5, .5, 20);
        double slowDuration = config.number(
                "slow-duration", 1.5, 0, 30);
        int slowAmplifier = config.integer(
                "slow-amplifier", 0, 0, 10);
        double absorptionPerEnemy = config.number(
                "absorption-per-enemy", 2, 0, 20);
        double maximumAbsorption = config.number(
                "maximum-absorption", 12, 0, 40);
        double absorptionDuration = config.number(
                "absorption-duration", 6, .1, 60);
        skillManager.register(new ConfiguredWarriorSkill(
                "battlefield_aura", "戦場の覇気", enabled, cooldown, 0,
                player -> {
                    if (!support.validateCaster(player)) {
                        return Optional.empty();
                    }
                    List<LivingEntity> targets =
                            support.nearby(player, radius);
                    if (targets.isEmpty()) {
                        support.fail(player, "範囲内に有効な敵がいません");
                        return Optional.empty();
                    }
                    return Optional.of(new Skill.PreparedUse(0, () -> {
                        for (LivingEntity target : targets) {
                            if (target instanceof Mob) {
                                target.addPotionEffect(new PotionEffect(
                                        PotionEffectType.SLOWNESS,
                                        (int) Math.round(
                                                slowDuration * 20),
                                        slowAmplifier,
                                        false, true, true));
                            }
                        }
                        effects.grantAbsorption(
                                player,
                                Math.min(maximumAbsorption,
                                        targets.size()
                                                * absorptionPerEnemy),
                                absorptionDuration);
                        support.play(
                                player, Particle.ANGRY_VILLAGER, 18,
                                Sound.ENTITY_RAVAGER_ROAR, 1.25f);
                    }));
                }));
    }

    private static void registerEndure(
            SkillManager skillManager,
            WarriorSkillSupport support,
            WarriorEffectManager effects
    ) {
        var config = support.config("endure");
        boolean enabled = config.enabled();
        double cooldown = config.number("cooldown", 24, 0, 300);
        double duration = config.number("duration", 5, .1, 60);
        double deferred = config.number(
                "deferred-damage-fraction", .4, 0, .9);
        double outgoingReduction = config.number(
                "outgoing-clears-fraction", .5, 0, 1);
        skillManager.register(new ConfiguredWarriorSkill(
                "endure", "耐え抜く", enabled, cooldown, 0,
                player -> {
                    if (!support.validateCaster(player)) {
                        return Optional.empty();
                    }
                    return Optional.of(new Skill.PreparedUse(0, () -> {
                        effects.startEndure(
                                player, duration, deferred,
                                outgoingReduction);
                        support.play(
                                player, Particle.ASH, 24,
                                Sound.ITEM_SHIELD_BLOCK, .65f);
                    }));
                }));
    }
}
