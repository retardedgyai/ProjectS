package io.github.gyai.projects.skill.warrior;

import io.github.gyai.projects.combat.classsystem.WarriorEffectManager;
import io.github.gyai.projects.player.PlayerData;
import io.github.gyai.projects.skill.Skill;
import io.github.gyai.projects.skill.SkillManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;

import java.util.Optional;

public final class WarriorUltimateSkills {
    private WarriorUltimateSkills() {
    }

    public static void register(
            SkillManager skillManager,
            WarriorSkillSupport support,
            WarriorEffectManager effects,
            io.github.gyai.projects.manager.PlayerManager playerManager
    ) {
        registerRelease(skillManager, support, playerManager);
        registerBloodBattle(
                skillManager, support, effects, playerManager);
        registerEndWar(
                skillManager, support, effects, playerManager);
    }

    private static void registerRelease(
            SkillManager skillManager,
            WarriorSkillSupport support,
            io.github.gyai.projects.manager.PlayerManager playerManager
    ) {
        var config = support.config("fighting_spirit_release");
        boolean enabled = config.enabled();
        int minimumSpirit = config.integer(
                "minimum-spirit", 20, 0, PlayerData.MAX_FIGHTING_SPIRIT);
        double cooldown = config.number("cooldown", 35, 0, 300);
        double radius = config.number("radius", 5, .5, 20);
        double baseDamage = config.number(
                "base-damage", 10, 0, 10_000);
        double scaling = config.number(
                "attack-power-scaling", 1, 0, 100);
        double spiritScaling = config.number(
                "spirit-damage-scaling", .25, 0, 100);
        skillManager.register(new ConfiguredWarriorSkill(
                "fighting_spirit_release", "闘気解放", enabled,
                cooldown, minimumSpirit,
                player -> {
                    if (!support.validateCaster(player)) {
                        return Optional.empty();
                    }
                    int spirit = spirit(playerManager, player);
                    if (spirit < minimumSpirit) {
                        support.fail(player, "闘気が%d以上必要です"
                                .formatted(minimumSpirit));
                        return Optional.empty();
                    }
                    double damage = baseDamage
                            + support.attackPower(player) * scaling
                            + spirit * spiritScaling;
                    return Optional.of(new Skill.PreparedUse(
                            spirit,
                            () -> {
                                support.play(
                                        player, Particle.SONIC_BOOM, 1,
                                        Sound.ENTITY_WARDEN_SONIC_BOOM,
                                        1.35f);
                                support.damageTargetsAtSpirit(
                                        player,
                                        support.nearby(player, radius),
                                        damage,
                                        "fighting_spirit_release",
                                        spirit);
                            }));
                }));
    }

    private static void registerBloodBattle(
            SkillManager skillManager,
            WarriorSkillSupport support,
            WarriorEffectManager effects,
            io.github.gyai.projects.manager.PlayerManager playerManager
    ) {
        var config = support.config("blood_battle");
        boolean enabled = config.enabled();
        int minimumSpirit = config.integer(
                "minimum-spirit", 20, 0, PlayerData.MAX_FIGHTING_SPIRIT);
        double cooldown = config.number("cooldown", 40, 0, 300);
        double baseDuration = config.number(
                "base-duration", 4, .1, 60);
        double durationPerSpirit = config.number(
                "duration-per-spirit", .04, 0, 1);
        double maximumDuration = config.number(
                "maximum-duration", 8, .1, 60);
        double attackSpeedBonus = config.number(
                "attack-speed-bonus", .25, 0, 5);
        double splashFraction = config.number(
                "splash-damage-fraction", .5, 0, 2);
        double splashRadius = config.number(
                "splash-radius", 2.5, 0, 12);
        double cooldownReduction = config.number(
                "e-cooldown-reduction-per-hit", .5, 0, 10);
        skillManager.register(new ConfiguredWarriorSkill(
                "blood_battle", "血戦", enabled, cooldown,
                minimumSpirit,
                player -> {
                    if (!support.validateCaster(player)) {
                        return Optional.empty();
                    }
                    int spirit = spirit(playerManager, player);
                    if (spirit < minimumSpirit) {
                        support.fail(player, "闘気が%d以上必要です"
                                .formatted(minimumSpirit));
                        return Optional.empty();
                    }
                    double duration = Math.min(
                            maximumDuration,
                            baseDuration + spirit * durationPerSpirit);
                    return Optional.of(new Skill.PreparedUse(
                            spirit,
                            () -> {
                                effects.startBloodBattle(
                                        player, duration, attackSpeedBonus,
                                        splashFraction, splashRadius,
                                        cooldownReduction);
                                support.play(
                                        player, Particle.DAMAGE_INDICATOR, 24,
                                        Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR,
                                        .7f);
                            }));
                }));
    }

    private static void registerEndWar(
            SkillManager skillManager,
            WarriorSkillSupport support,
            WarriorEffectManager effects,
            io.github.gyai.projects.manager.PlayerManager playerManager
    ) {
        var config = support.config("end_war_strike");
        boolean enabled = config.enabled();
        int minimumSpirit = config.integer(
                "minimum-spirit", 20, 0, PlayerData.MAX_FIGHTING_SPIRIT);
        double cooldown = config.number("cooldown", 45, 0, 300);
        double castDelay = config.number("cast-delay", .8, .05, 5);
        double range = config.number("range", 6, .5, 24);
        double angle = config.number("angle", 120, 1, 360);
        double baseDamage = config.number(
                "base-damage", 20, 0, 10_000);
        double scaling = config.number(
                "attack-power-scaling", 2, 0, 100);
        double spiritScaling = config.number(
                "spirit-damage-scaling", .35, 0, 100);
        double maxMissingHealthBonus = config.number(
                "maximum-missing-health-bonus", .5, 0, 5);
        skillManager.register(new ConfiguredWarriorSkill(
                "end_war_strike", "終戦の一撃", enabled, cooldown,
                minimumSpirit,
                player -> {
                    if (!support.validateCaster(player)) {
                        return Optional.empty();
                    }
                    int spirit = spirit(playerManager, player);
                    if (spirit < minimumSpirit) {
                        support.fail(player, "闘気が%d以上必要です"
                                .formatted(minimumSpirit));
                        return Optional.empty();
                    }
                    double damage = baseDamage
                            + support.attackPower(player) * scaling
                            + spirit * spiritScaling;
                    if (spirit >= PlayerData.MAX_FIGHTING_SPIRIT) {
                        var maximumHealth =
                                player.getAttribute(Attribute.MAX_HEALTH);
                        double maximum = maximumHealth == null
                                ? player.getHealth()
                                : maximumHealth.getValue();
                        double missing = maximum <= 0
                                ? 0 : 1.0 - player.getHealth() / maximum;
                        damage *= 1.0
                                + missing * maxMissingHealthBonus;
                    }
                    double capturedDamage = damage;
                    long delayTicks = Math.max(
                            1L, Math.round(castDelay * 20));
                    return Optional.of(new Skill.PreparedUse(
                            spirit,
                            () -> {
                                support.play(
                                        player, Particle.FLASH, 1,
                                        Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,
                                        .55f);
                                effects.schedule(
                                        player,
                                        "end_war_strike",
                                        delayTicks,
                                        () -> {
                                            support.play(
                                                    player,
                                                    Particle.SWEEP_ATTACK,
                                                    30,
                                                    Sound.ENTITY_PLAYER_ATTACK_STRONG,
                                                    .55f);
                                            support.damageTargetsAtSpirit(
                                                    player,
                                                    support.cone(
                                                            player, range,
                                                            angle, true),
                                                    capturedDamage,
                                                    "end_war_strike",
                                                    spirit);
                                        });
                            }));
                }));
    }

    private static int spirit(
            io.github.gyai.projects.manager.PlayerManager playerManager,
            org.bukkit.entity.Player player
    ) {
        return playerManager.getPlayerData(player).getFightingSpirit();
    }
}
