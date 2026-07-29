package io.github.gyai.projects.skill.warrior;

import io.github.gyai.projects.combat.classsystem.WarriorCombatManager;
import io.github.gyai.projects.combat.classsystem.WarriorEffectManager;
import io.github.gyai.projects.skill.Skill;
import io.github.gyai.projects.skill.SkillManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Optional;

public final class WarriorMobilitySkills {
    private WarriorMobilitySkills() {
    }

    public static void register(
            SkillManager skillManager,
            WarriorSkillSupport support,
            WarriorCombatManager combatManager,
            WarriorEffectManager effectManager
    ) {
        registerCharge(skillManager, support, effectManager);
        registerExecutionLeap(skillManager, support, combatManager);
        registerEarthShatter(skillManager, support, combatManager);
    }

    private static void registerCharge(
            SkillManager skillManager,
            WarriorSkillSupport support,
            WarriorEffectManager effectManager
    ) {
        var config = support.config("warrior_charge");
        boolean enabled = config.enabled();
        double cooldown = config.number("cooldown", 10, 0, 300);
        double distance = config.number("range", 7, .5, 24);
        double width = config.number("width", 1.5, .3, 6);
        double speed = config.number(
                "dash-speed", 1.25, .2, 2);
        double baseDamage = config.number("base-damage", 8, 0, 10_000);
        double scaling = config.number(
                "attack-power-scaling", .8, 0, 100);

        skillManager.register(new ConfiguredWarriorSkill(
                "warrior_charge", "猛進", enabled, cooldown, 0,
                player -> {
                    if (!support.validateCaster(player)) {
                        return Optional.empty();
                    }
                    Vector direction =
                            player.getLocation().getDirection();
                    if (!effectManager.canStartCharge(
                            player, direction, speed)) {
                        support.fail(
                                player, "前方へダッシュできません");
                        return Optional.empty();
                    }
                    double damage =
                            baseDamage + support.attackPower(player) * scaling;
                    return Optional.of(new Skill.PreparedUse(0, () -> {
                        effectManager.startCharge(
                                player,
                                direction,
                                distance,
                                speed,
                                width,
                                damage);
                    }));
                }));
    }

    private static void registerExecutionLeap(
            SkillManager skillManager,
            WarriorSkillSupport support,
            WarriorCombatManager combatManager
    ) {
        var config = support.config("execution_leap");
        boolean enabled = config.enabled();
        double cooldown = config.number("cooldown", 9, 0, 300);
        double range = config.number("range", 10, .5, 32);
        double baseDamage = config.number("base-damage", 12, 0, 10_000);
        double scaling = config.number(
                "attack-power-scaling", 1, 0, 100);

        skillManager.register(new ConfiguredWarriorSkill(
                "execution_leap", "処刑跳躍", enabled, cooldown, 0,
                player -> {
                    if (!support.validateCaster(player)) {
                        return Optional.empty();
                    }
                    LivingEntity target =
                            support.crosshairTarget(player, range).orElse(null);
                    if (target == null) {
                        support.fail(player, "有効な対象がいません");
                        return Optional.empty();
                    }
                    Vector approach = target.getLocation().toVector()
                            .subtract(player.getLocation().toVector());
                    var destination = support.safeLocationNear(
                            player, target.getLocation(), approach).orElse(null);
                    if (destination == null) {
                        support.fail(player, "安全な着地点がありません");
                        return Optional.empty();
                    }
                    double damage =
                            baseDamage + support.attackPower(player) * scaling;
                    return Optional.of(new Skill.PreparedUse(0, () -> {
                        player.teleport(destination);
                        player.getWorld().spawnParticle(
                                Particle.GUST,
                                destination.clone().add(0, 1, 0),
                                10, .35, .5, .35, .05);
                        player.playSound(
                                player,
                                Sound.ENTITY_PLAYER_ATTACK_CRIT,
                                1f,
                                .75f);
                        try (WarriorCombatManager.SkillHitSession session =
                                     combatManager.beginSkillUse(player)) {
                            support.damage(
                                    player,
                                    target,
                                    damage,
                                    "execution_leap",
                                    session);
                        }
                        if (target.isDead()
                                || (target.getHealth() <= 0.0
                                && target.getType() != EntityType.ARMOR_STAND)) {
                            skillManager.clearCooldown(
                                    player, "execution_leap");
                        }
                    }));
                }));
    }

    private static void registerEarthShatter(
            SkillManager skillManager,
            WarriorSkillSupport support,
            WarriorCombatManager combatManager
    ) {
        var config = support.config("earth_shatter");
        boolean enabled = config.enabled();
        double cooldown = config.number("cooldown", 12, 0, 300);
        double radius = config.number("radius", 4, .5, 16);
        double baseDamage = config.number("base-damage", 10, 0, 10_000);
        double scaling = config.number(
                "attack-power-scaling", .9, 0, 100);
        double slowSeconds = config.number(
                "slow-duration", 2, 0, 30);
        double launchY = config.number(
                "launch-y", .45, 0, 2);

        skillManager.register(new ConfiguredWarriorSkill(
                "earth_shatter", "大地砕き", enabled, cooldown, 0,
                player -> {
                    if (!support.validateCaster(player)) {
                        return Optional.empty();
                    }
                    double damage =
                            baseDamage + support.attackPower(player) * scaling;
                    return Optional.of(new Skill.PreparedUse(0, () -> {
                        List<LivingEntity> targets =
                                support.nearby(player, radius);
                        support.play(
                                player,
                                Particle.DUST_PLUME,
                                28,
                                Sound.ENTITY_GENERIC_EXPLODE,
                                .7f);
                        try (WarriorCombatManager.SkillHitSession session =
                                     combatManager.beginSkillUse(player)) {
                            for (LivingEntity target : targets) {
                                support.damage(
                                        player,
                                        target,
                                        damage,
                                        "earth_shatter",
                                        session);
                                if (!session.confirmedTarget(
                                        target.getUniqueId())) {
                                    continue;
                                }
                                if (target instanceof Mob) {
                                    target.addPotionEffect(new PotionEffect(
                                            PotionEffectType.SLOWNESS,
                                            (int) Math.round(
                                                    slowSeconds * 20),
                                            0,
                                            false,
                                            true,
                                            true));
                                }
                                if (canLaunch(target)) {
                                    Vector velocity = target.getVelocity();
                                    target.setVelocity(velocity.setY(
                                            Math.max(
                                                    velocity.getY(),
                                                    launchY)));
                                }
                            }
                        }
                    }));
                }));
    }

    private static boolean canLaunch(LivingEntity target) {
        if (!(target instanceof Mob)) return false;
        return target.getType() != EntityType.ENDER_DRAGON
                && target.getType() != EntityType.WITHER
                && target.getType() != EntityType.WARDEN
                && !target.getScoreboardTags().contains(
                "projects:no_knockback");
    }
}
