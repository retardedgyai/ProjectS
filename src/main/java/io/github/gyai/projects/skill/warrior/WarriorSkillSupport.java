package io.github.gyai.projects.skill.warrior;

import io.github.gyai.projects.combat.classsystem.WarriorCombatManager;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.manager.EnhancementManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Optional;

public final class WarriorSkillSupport {
    private final JavaPlugin plugin;
    private final TrainingDummyManager dummyManager;
    private final EnhancementManager enhancementManager;
    private final WarriorCombatManager combatManager;

    public WarriorSkillSupport(
            JavaPlugin plugin,
            TrainingDummyManager dummyManager,
            EnhancementManager enhancementManager,
            WarriorCombatManager combatManager
    ) {
        this.plugin = plugin;
        this.dummyManager = dummyManager;
        this.enhancementManager = enhancementManager;
        this.combatManager = combatManager;
    }

    public boolean validateCaster(Player player) {
        if (!combatManager.isWarrior(player)
                || player.isDead()
                || !player.isOnline()) {
            player.sendActionBar(Component.text(
                    "ウォーリアー武器を装備してください", NamedTextColor.RED));
            return false;
        }
        if (enhancementManager.isBroken(
                player.getInventory().getItemInMainHand())) {
            player.sendActionBar(Component.text(
                    "この武器は破損しています", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    public double attackPower(Player player) {
        return enhancementManager.getAttackPower(
                player, player.getInventory().getItemInMainHand());
    }

    public List<LivingEntity> nearby(Player player, double radius) {
        return player.getLocation().getNearbyLivingEntities(radius).stream()
                .filter(target -> combatManager.isValidEnemy(player, target))
                .toList();
    }

    public List<LivingEntity> cone(
            Player player,
            double range,
            double angleDegrees,
            boolean requireLineOfSight
    ) {
        Vector forward = player.getEyeLocation().getDirection().normalize();
        double minimumDot = Math.cos(Math.toRadians(angleDegrees / 2.0));
        return player.getLocation().getNearbyLivingEntities(range).stream()
                .filter(target -> combatManager.isValidEnemy(player, target))
                .filter(target -> {
                    Vector direction = target.getLocation()
                            .add(0, target.getHeight() * .5, 0)
                            .toVector()
                            .subtract(player.getEyeLocation().toVector());
                    if (direction.lengthSquared() == 0.0) return true;
                    return forward.dot(direction.normalize()) >= minimumDot;
                })
                .filter(target -> !requireLineOfSight
                        || player.hasLineOfSight(target))
                .toList();
    }

    public Optional<LivingEntity> crosshairTarget(
            Player player,
            double range
    ) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                1.0,
                entity -> combatManager.isValidEnemy(player, entity));
        if (result == null
                || !(result.getHitEntity() instanceof LivingEntity target)
                || !player.hasLineOfSight(target)) {
            return Optional.empty();
        }
        return Optional.of(target);
    }

    public int damageTargets(
            Player player,
            Iterable<? extends LivingEntity> targets,
            double damage,
            String skillId
    ) {
        try (WarriorCombatManager.SkillHitSession session =
                     combatManager.beginSkillUse(player)) {
            for (LivingEntity target : targets) {
                damage(player, target, damage, skillId, session);
            }
            return session.confirmedHits();
        }
    }

    public int damageTargetsAtSpirit(
            Player player,
            Iterable<? extends LivingEntity> targets,
            double damage,
            String skillId,
            double spiritSnapshot
    ) {
        double adjustedDamage = damage
                * combatManager.damageMultiplierForSpirit(spiritSnapshot);
        try (WarriorCombatManager.SkillHitSession session =
                     combatManager.beginSkillUse(player)) {
            for (LivingEntity target : targets) {
                damage(
                        player, target, adjustedDamage,
                        skillId, session, true);
            }
            return session.confirmedHits();
        }
    }

    public void damage(
            Player player,
            LivingEntity target,
            double damage,
            String skillId,
            WarriorCombatManager.SkillHitSession session
    ) {
        damage(player, target, damage, skillId, session, false);
    }

    private void damage(
            Player player,
            LivingEntity target,
            double damage,
            String skillId,
            WarriorCombatManager.SkillHitSession session,
            boolean spiritBonusAlreadyApplied
    ) {
        if (!validateCaster(player)
                || !target.isValid()
                || !combatManager.isValidEnemy(player, target)
                || damage <= 0.0) {
            return;
        }
        if (dummyManager.isTrainingDummy(target)) {
            dummyManager.markSkillDamage(player, target, skillId);
        }
        enhancementManager.beginSkillDamage(player.getUniqueId());
        try (WarriorCombatManager.HitScope ignored = session.activate()) {
            if (spiritBonusAlreadyApplied) {
                combatManager.runWithSpiritBonusAlreadyApplied(
                        player, () -> target.damage(damage, player));
            } else {
                target.damage(damage, player);
            }
        } finally {
            enhancementManager.endSkillDamage(player.getUniqueId());
        }
    }

    public Optional<Location> safeLocationNear(
            Player player,
            Location target,
            Vector approach
    ) {
        Vector horizontal = approach.clone().setY(0);
        if (horizontal.lengthSquared() == 0.0) horizontal.setZ(1);
        horizontal.normalize();
        for (double distance : new double[]{1.4, 1.8, 1.0, 2.2}) {
            Location candidate = target.clone()
                    .subtract(horizontal.clone().multiply(distance));
            candidate.setYaw(player.getLocation().getYaw());
            candidate.setPitch(player.getLocation().getPitch());
            candidate.setX(Math.floor(candidate.getX()) + .5);
            candidate.setZ(Math.floor(candidate.getZ()) + .5);
            if (isSafeStandingLocation(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public boolean isSafeStandingLocation(Location location) {
        var feet = location.getBlock();
        var head = feet.getRelative(org.bukkit.block.BlockFace.UP);
        var ground = feet.getRelative(org.bukkit.block.BlockFace.DOWN);
        return feet.isPassable()
                && head.isPassable()
                && ground.getType().isSolid()
                && ground.getType() != org.bukkit.Material.MAGMA_BLOCK;
    }

    public void fail(Player player, String reason) {
        player.sendActionBar(Component.text(reason, NamedTextColor.RED));
    }

    public void play(
            Player player,
            Particle particle,
            int count,
            Sound sound,
            float pitch
    ) {
        player.getWorld().spawnParticle(
                particle,
                player.getLocation().add(0, 1, 0),
                count,
                .6, .5, .6,
                .08);
        player.getWorld().playSound(
                player.getLocation(), sound, .9f, pitch);
    }

    public SkillConfig config(String skillId) {
        ConfigurationSection section = plugin.getConfig()
                .getConfigurationSection("skills.warrior." + skillId);
        return new SkillConfig(plugin, skillId, section);
    }

    public static final class SkillConfig {
        private final JavaPlugin plugin;
        private final String skillId;
        private final ConfigurationSection section;

        private SkillConfig(
                JavaPlugin plugin,
                String skillId,
                ConfigurationSection section
        ) {
            this.plugin = plugin;
            this.skillId = skillId;
            this.section = section;
        }

        public boolean enabled() {
            return section == null || section.getBoolean("enabled", true);
        }

        public double number(
                String key,
                double fallback,
                double minimum,
                double maximum
        ) {
            double value = section == null
                    ? fallback : section.getDouble(key, fallback);
            double clamped = Math.clamp(value, minimum, maximum);
            if (value != clamped) {
                plugin.getLogger().warning(
                        "[ProjectS] skills.warrior.%s.%s を %.3f に補正しました"
                                .formatted(skillId, key, clamped));
            }
            return clamped;
        }

        public int integer(
                String key,
                int fallback,
                int minimum,
                int maximum
        ) {
            int value = section == null
                    ? fallback : section.getInt(key, fallback);
            int clamped = Math.clamp(value, minimum, maximum);
            if (value != clamped) {
                plugin.getLogger().warning(
                        "[ProjectS] skills.warrior.%s.%s を %d に補正しました"
                                .formatted(skillId, key, clamped));
            }
            return clamped;
        }
    }
}
