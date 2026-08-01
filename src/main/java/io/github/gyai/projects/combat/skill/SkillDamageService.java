package io.github.gyai.projects.combat.skill;

import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageMode;
import io.github.gyai.projects.combat.damage.DamageRequest;
import io.github.gyai.projects.combat.damage.DamageService;
import io.github.gyai.projects.combat.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkillDamageService {
    private final DamageService damageService;
    private final PainterPassiveManager passiveManager;
    private final JavaPlugin plugin;
    private PainterSkillExecutor executor;
    public SkillDamageService(JavaPlugin plugin, DamageService damageService, PainterPassiveManager passiveManager) {
        this.plugin=plugin; this.damageService = damageService; this.passiveManager = passiveManager;
    }
    public void setExecutor(PainterSkillExecutor executor) { this.executor = executor; }

    public void damage(Player caster, LivingEntity target, String skillId, double amount,
                       boolean damageOverTime, boolean passiveEligible, UUIDCast cast) {
        damage(caster, target, skillId, amount,
                damageOverTime, false, passiveEligible, cast);
    }
    public void damage(Player caster, LivingEntity target, String skillId, double amount,
                       boolean damageOverTime, boolean areaDamage,
                       boolean passiveEligible, UUIDCast cast) {
        apply(caster, target, amount, skillId,
                damageOverTime, areaDamage, cast.id());
        if ("severing-bolt".equals(skillId) && plugin.getConfig().getBoolean("debug.painter-skills", false))
            plugin.getLogger().info("[SkillDamage] skill=severing-bolt castId="+cast.id()+" target="+target.getUniqueId()+" amount="+amount);
        if (passiveEligible) passiveManager.record(caster, target, skillId);
        if (executor != null) executor.consumeStirringLight(caster, target, cast.id());
    }
    public void bonusDamage(Player caster, LivingEntity target, double amount) { apply(caster, target, amount, "bonus-damage", false, false, java.util.UUID.randomUUID()); }
    public void bonusDamage(Player caster, LivingEntity target, String skillId, double amount) { apply(caster, target, amount, skillId, false, false, java.util.UUID.randomUUID()); }
    public void bonusAreaDamage(Player caster, LivingEntity target, String skillId,
                                double amount, UUIDCast cast) {
        apply(caster, target, amount, skillId, false, true, cast.id());
    }
    public boolean isApplying(Player caster, LivingEntity target) {
        return damageService.isApplying(caster, target);
    }
    private void apply(Player caster, LivingEntity target, double amount, String skillId,
                       boolean damageOverTime, boolean areaDamage,
                       java.util.UUID castId) {
        damageService.apply(DamageRequest.builder(caster, target)
                .skillId(skillId)
                .castId(castId)
                .damageType(DamageType.MAGICAL)
                .damageKind(damageOverTime
                        ? DamageKind.DAMAGE_OVER_TIME : DamageKind.DIRECT_SKILL)
                .mode(DamageMode.PVE)
                .areaDamage(areaDamage)
                .criticalAllowed(!damageOverTime)
                .fixedDamage(amount)
                .coefficient(0.0)
                .build());
    }
    public record UUIDCast(java.util.UUID id) { public static UUIDCast create() { return new UUIDCast(java.util.UUID.randomUUID()); } }
}
