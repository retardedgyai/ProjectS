package io.github.gyai.projects.combat.skill;

import io.github.gyai.projects.combat.damage.DamageEventApplicationPolicy;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

public final class TargetingService {
    private final TrainingDummyManager dummyManager;
    public TargetingService(TrainingDummyManager dummyManager) { this.dummyManager = dummyManager; }

    public boolean isEnemy(Player caster, LivingEntity target) {
        if (target == null || target == caster || target.isDead() || !target.isValid()) return false;
        if (dummyManager.isTrainingDummy(target)) return true;
        if (!DamageEventApplicationPolicy.allowsPveTarget(
                target instanceof Player)) return false;
        if (target.getScoreboardTags().contains("projects_untargetable") || target.isInvulnerable()) return false;
        return !(target instanceof ArmorStand);
    }

    public List<LivingEntity> enemies(Player caster, Location center, double radius) {
        return center.getNearbyLivingEntities(radius).stream().filter(target -> isEnemy(caster, target)).toList();
    }
}
