package io.github.gyai.projects.combat.skill;

import io.github.gyai.projects.dummy.TrainingDummyManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkillDamageService {
    private final TrainingDummyManager dummyManager;
    private final PainterPassiveManager passiveManager;
    private final JavaPlugin plugin;
    private PainterSkillExecutor executor;
    private final java.util.Set<DamageKey> applying = new java.util.HashSet<>();
    public SkillDamageService(JavaPlugin plugin, TrainingDummyManager dummyManager, PainterPassiveManager passiveManager) {
        this.plugin=plugin; this.dummyManager = dummyManager; this.passiveManager = passiveManager;
    }
    public void setExecutor(PainterSkillExecutor executor) { this.executor = executor; }

    public void damage(Player caster, LivingEntity target, String skillId, double amount,
                       boolean damageOverTime, boolean passiveEligible, UUIDCast cast) {
        apply(caster, target, amount, skillId);
        if ("severing-bolt".equals(skillId) && plugin.getConfig().getBoolean("debug.painter-skills", false))
            plugin.getLogger().info("[SkillDamage] skill=severing-bolt castId="+cast.id()+" target="+target.getUniqueId()+" amount="+amount);
        if (passiveEligible) passiveManager.record(caster, target, skillId);
        if (executor != null) executor.consumeStirringLight(caster, target, cast.id());
    }
    public void bonusDamage(Player caster, LivingEntity target, double amount) { apply(caster, target, amount, "bonus-damage"); }
    public void bonusDamage(Player caster, LivingEntity target, String skillId, double amount) { apply(caster, target, amount, skillId); }
    public boolean isApplying(Player caster, LivingEntity target) {
        return applying.contains(new DamageKey(caster.getUniqueId(), target.getUniqueId()));
    }
    private void apply(Player caster, LivingEntity target, double amount, String skillId) {
        if (dummyManager.isTrainingDummy(target)) dummyManager.markSkillDamage(caster, target, skillId);
        DamageKey key=new DamageKey(caster.getUniqueId(),target.getUniqueId()); applying.add(key);
        try { target.damage(amount,caster); } finally { applying.remove(key); }
    }
    public record UUIDCast(java.util.UUID id) { public static UUIDCast create() { return new UUIDCast(java.util.UUID.randomUUID()); } }
    private record DamageKey(java.util.UUID caster,java.util.UUID target){}
}
