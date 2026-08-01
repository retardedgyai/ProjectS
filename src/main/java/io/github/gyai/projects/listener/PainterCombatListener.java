package io.github.gyai.projects.listener;

import io.github.gyai.projects.combat.skill.PainterSkillExecutor;
import io.github.gyai.projects.manager.ItemManager;
import io.github.gyai.projects.combat.skill.SkillDamageService;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageEventApplicationPolicy;
import io.github.gyai.projects.combat.damage.DamageMode;
import io.github.gyai.projects.combat.damage.DamageRequest;
import io.github.gyai.projects.combat.damage.DamageService;
import io.github.gyai.projects.combat.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class PainterCombatListener implements Listener {
    private final ItemManager itemManager;
    private final PainterSkillExecutor executor;
    private final SkillDamageService damageService;
    private final DamageService commonDamageService;
    public PainterCombatListener(ItemManager itemManager, PainterSkillExecutor executor,
                                 SkillDamageService damageService, DamageService commonDamageService) {
        this.itemManager=itemManager; this.executor=executor; this.damageService=damageService;
        this.commonDamageService=commonDamageService;
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if(event.getDamager() instanceof Player player && event.getEntity() instanceof LivingEntity target
                && itemManager.isCustomItem(player.getInventory().getItemInMainHand(),"painter_staff")) {
            if (!DamageEventApplicationPolicy.allowsPveTarget(
                    target instanceof Player)) {
                event.setCancelled(true);
                return;
            }
            if (damageService.isApplying(player,target)) return;
            event.setCancelled(true);
            commonDamageService.apply(DamageRequest.builder(player, target)
                    .skillId("painter_normal_attack")
                    .damageType(DamageType.MAGICAL)
                    .damageKind(DamageKind.NORMAL_ATTACK)
                    .mode(DamageMode.PVE)
                    .fixedDamage(executor.enhanceNormalAttack(player,target))
                    .coefficient(1.0)
                    .build());
        }
    }
}
