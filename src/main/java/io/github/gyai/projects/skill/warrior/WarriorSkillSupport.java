package io.github.gyai.projects.skill.warrior;

import io.github.gyai.projects.combat.classsystem.WarriorCombatManager;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.manager.EnhancementManager;
import io.github.gyai.projects.manager.BalanceTuningManager;
import io.github.gyai.projects.combat.damage.DamageRequest;
import io.github.gyai.projects.combat.damage.DamageRequestApplier;
import io.github.gyai.projects.combat.damage.DamageService;
import io.github.gyai.projects.combat.damage.AttackMetadata;
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
import java.util.Objects;
import java.util.Optional;

public final class WarriorSkillSupport {
    private final JavaPlugin plugin;
    private final TrainingDummyManager dummyManager;
    private final EnhancementManager enhancementManager;
    private final WarriorCombatManager combatManager;
    private final BalanceTuningManager balanceManager;
    private final DamageRequestApplier damageApplier;

    public WarriorSkillSupport(
            JavaPlugin plugin,
            TrainingDummyManager dummyManager,
            EnhancementManager enhancementManager,
            WarriorCombatManager combatManager,
            BalanceTuningManager balanceManager,
            DamageService damageService
    ) {
        this(plugin, dummyManager, enhancementManager, combatManager,
                balanceManager, damageService::apply);
    }

    public WarriorSkillSupport(
            JavaPlugin plugin,
            TrainingDummyManager dummyManager,
            EnhancementManager enhancementManager,
            WarriorCombatManager combatManager,
            BalanceTuningManager balanceManager,
            DamageRequestApplier damageApplier
    ) {
        this.plugin = plugin;
        this.dummyManager = dummyManager;
        this.enhancementManager = enhancementManager;
        this.combatManager = combatManager;
        this.balanceManager = balanceManager;
        this.damageApplier = Objects.requireNonNull(
                damageApplier, "damageApplier");
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

    public void registerDamageBalance(
            String skillId,
            String displayName,
            double baseDamage,
            double attackPowerScaling
    ) {
        balanceManager.registerDamageSkill(
                skillId, displayName, baseDamage, attackPowerScaling);
    }

    public BalanceTuningManager.DamageValues damageValues(String skillId) {
        return balanceManager.damageValues(skillId);
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
            double fixedDamage,
            double coefficient,
            String skillId
    ) {
        return damageTargets(
                player, targets, fixedDamage, coefficient,
                skillId, AttackMetadata.EMPTY);
    }

    public int damageTargets(
            Player player,
            Iterable<? extends LivingEntity> targets,
            double fixedDamage,
            double coefficient,
            String skillId,
            AttackMetadata attackMetadata
    ) {
        try (WarriorCombatManager.SkillHitSession session =
                     combatManager.beginSkillUse(player)) {
            for (LivingEntity target : targets) {
                damage(player, target, fixedDamage, coefficient,
                        skillId, session, true, 1.0, false,
                        attackMetadata);
            }
            return session.confirmedHits();
        }
    }

    public int damageTargetsAtSpirit(
            Player player,
            Iterable<? extends LivingEntity> targets,
            double fixedDamage,
            double coefficient,
            String skillId,
            double spiritSnapshot
    ) {
        return damageTargetsAtSpirit(
                player, targets, fixedDamage, coefficient,
                skillId, spiritSnapshot, 1.0);
    }

    public int damageTargetsAtSpirit(
            Player player,
            Iterable<? extends LivingEntity> targets,
            double fixedDamage,
            double coefficient,
            String skillId,
            double spiritSnapshot,
            double additionalMultiplier
    ) {
        double multiplier = combatManager.damageMultiplierForSpirit(spiritSnapshot)
                * Math.max(0.0, additionalMultiplier);
        try (WarriorCombatManager.SkillHitSession session =
                     combatManager.beginSkillUse(player)) {
            for (LivingEntity target : targets) {
                damage(player, target, fixedDamage, coefficient,
                        skillId, session, true, multiplier, true,
                        AttackMetadata.EMPTY);
            }
            return session.confirmedHits();
        }
    }

    public void damage(
            Player player,
            LivingEntity target,
            double fixedDamage,
            double coefficient,
            String skillId,
            WarriorCombatManager.SkillHitSession session
    ) {
        damage(player, target, fixedDamage, coefficient,
                skillId, session, AttackMetadata.EMPTY);
    }

    public void damage(
            Player player,
            LivingEntity target,
            double fixedDamage,
            double coefficient,
            String skillId,
            WarriorCombatManager.SkillHitSession session,
            AttackMetadata attackMetadata
    ) {
        damage(player, target, fixedDamage, coefficient,
                skillId, session, false, 1.0, false, attackMetadata);
    }

    public void damage(
            Player player,
            LivingEntity target,
            double fixedDamage,
            double coefficient,
            String skillId,
            WarriorCombatManager.SkillHitSession session,
            boolean areaDamage
    ) {
        damage(player, target, fixedDamage, coefficient,
                skillId, session, areaDamage, AttackMetadata.EMPTY);
    }

    public void damage(
            Player player,
            LivingEntity target,
            double fixedDamage,
            double coefficient,
            String skillId,
            WarriorCombatManager.SkillHitSession session,
            boolean areaDamage,
            AttackMetadata attackMetadata
    ) {
        damage(player, target, fixedDamage, coefficient,
                skillId, session, areaDamage, 1.0, false,
                attackMetadata);
    }

    private void damage(
            Player player,
            LivingEntity target,
            double fixedDamage,
            double coefficient,
            String skillId,
            WarriorCombatManager.SkillHitSession session,
            boolean areaDamage,
            double modeMultiplier,
            boolean spiritBonusAlreadyApplied,
            AttackMetadata attackMetadata
    ) {
        if (!validateCaster(player)
                || !target.isValid()
                || !combatManager.isValidEnemy(player, target)
                || (fixedDamage <= 0.0 && coefficient <= 0.0)) {
            return;
        }
        enhancementManager.beginSkillDamage(player.getUniqueId());
        try (WarriorCombatManager.HitScope ignored = session.activate()) {
            DamageRequest request = WarriorDamageRequestFactory.create(
                    player, target, fixedDamage, coefficient,
                    skillId, session.sessionId(), areaDamage,
                    modeMultiplier, attackMetadata);
            Runnable application = () -> damageApplier.apply(request);
            if (spiritBonusAlreadyApplied) {
                combatManager.runWithSpiritBonusAlreadyApplied(
                        player, application);
            } else {
                application.run();
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
