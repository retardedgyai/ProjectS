package io.github.gyai.projects.skill;

import io.github.gyai.projects.combat.classsystem.WarriorCombatManager;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.manager.EnhancementManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class SpinSlashSkill implements Skill {
    private final TrainingDummyManager dummyManager;
    private final EnhancementManager enhancementManager;
    private final WarriorCombatManager warriorCombatManager;

    public SpinSlashSkill(
            TrainingDummyManager dummyManager,
            EnhancementManager enhancementManager,
            WarriorCombatManager warriorCombatManager
    ) {
        this.dummyManager = dummyManager;
        this.enhancementManager = enhancementManager;
        this.warriorCombatManager = warriorCombatManager;
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
        return 8.0;
    }

    @Override
    public int getResourceCost() {
        return 0;
    }

    @Override
    public void execute(Player player) {
        double attackPower = enhancementManager.getAttackPower(
                player, player.getInventory().getItemInMainHand());
        double skillDamage = 11.0 + attackPower * 1.2;
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0, 1, 0), 12, 1.5, 0.35, 1.5, 0.0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);

        try (WarriorCombatManager.SkillHitSession ignored =
                     warriorCombatManager.beginSkillUse(player)) {
            for (LivingEntity entity : player.getLocation().getNearbyLivingEntities(3.0)) {
                if (!warriorCombatManager.isValidEnemy(player, entity)) {
                    continue;
                }
                if (dummyManager.isTrainingDummy(entity)) {
                    dummyManager.markSkillDamage(player, entity);
                }
                enhancementManager.beginSkillDamage(player.getUniqueId());
                try {
                    entity.damage(skillDamage, player);
                } finally {
                    enhancementManager.endSkillDamage(player.getUniqueId());
                }
                entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0.1);
            }
        }
    }
}
