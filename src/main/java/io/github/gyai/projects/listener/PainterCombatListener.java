package io.github.gyai.projects.listener;

import io.github.gyai.projects.combat.skill.PainterSkillExecutor;
import io.github.gyai.projects.manager.ItemManager;
import io.github.gyai.projects.combat.skill.SkillDamageService;
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
    public PainterCombatListener(ItemManager itemManager, PainterSkillExecutor executor, SkillDamageService damageService) {
        this.itemManager=itemManager; this.executor=executor; this.damageService=damageService;
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if(event.getDamager() instanceof Player player && event.getEntity() instanceof LivingEntity target
                && itemManager.isCustomItem(player.getInventory().getItemInMainHand(),"painter_staff")) {
            if (damageService.isApplying(player,target)) return;
            event.setDamage(event.getDamage()+executor.enhanceNormalAttack(player,target));
        }
    }
}
