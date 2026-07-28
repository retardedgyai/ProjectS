package io.github.gyai.projects.listener;

import io.github.gyai.projects.manager.ItemManager;
import io.github.gyai.projects.manager.CombatHudManager;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.manager.EnhancementManager;
import io.github.gyai.projects.input.CombatInputManager;
import io.github.gyai.projects.network.SkillInputType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public class CombatListener implements Listener {
    private final ItemManager itemManager;
    private final CombatInputManager combatInputManager;
    private final CombatHudManager hudManager;
    private final TrainingDummyManager dummyManager;
    private final EnhancementManager enhancementManager;

    public CombatListener(
            ItemManager itemManager,
            CombatInputManager combatInputManager,
            CombatHudManager hudManager,
            TrainingDummyManager dummyManager,
            EnhancementManager enhancementManager
    ) {
        this.itemManager = itemManager;
        this.combatInputManager = combatInputManager;
        this.hudManager = hudManager;
        this.dummyManager = dummyManager;
        this.enhancementManager = enhancementManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onNormalAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof LivingEntity)
                || event.getEntity() instanceof Player
                || (event.getEntity() instanceof ArmorStand && !dummyManager.isTrainingDummy(event.getEntity()))
                || !itemManager.isCustomItem(player.getInventory().getItemInMainHand(), "starter_sword")) {
            return;
        }

        var weapon = player.getInventory().getItemInMainHand();
        if (enhancementManager.isApplyingSkillDamage(player.getUniqueId())) {
            return;
        }
        if (enhancementManager.isBroken(weapon)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("この武器は破損しています", NamedTextColor.RED));
            return;
        }
        double enhancedAttackPower = enhancementManager.getAttackPower(player, weapon);
        event.setDamage(enhancedAttackPower);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSkillInput(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !itemManager.isCustomItem(event.getPlayer().getInventory().getItemInMainHand(), "starter_sword")) {
            return;
        }
        event.setCancelled(true);
        if (enhancementManager.isBroken(event.getPlayer().getInventory().getItemInMainHand())) {
            event.getPlayer().sendActionBar(Component.text(
                    "この武器は破損しているためスキルを使えません", NamedTextColor.RED));
            return;
        }
        combatInputManager.handle(event.getPlayer(), SkillInputType.SKILL_1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDodge(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        combatInputManager.handle(event.getPlayer(), SkillInputType.DODGE);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        combatInputManager.removePlayer(event.getPlayer());
        hudManager.removePlayer(event.getPlayer());
    }
}
