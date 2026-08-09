package io.github.gyai.projects.listener;

import io.github.gyai.projects.combat.damage.DamageService;
import io.github.gyai.projects.combat.skill.CrowdControlManager;
import io.github.gyai.projects.combat.skill.HardControlApplicationResult;
import io.github.gyai.projects.combat.skill.HardControlRemovalReason;
import io.github.gyai.projects.combat.skill.HardControlType;
import io.github.gyai.projects.dev.HardControlTestTool;
import io.github.gyai.projects.manager.MonsterManager;
import io.github.gyai.projects.monster.CustomMonster;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class HardControlTestToolListener implements Listener {
    private final HardControlTestTool tool;
    private final CrowdControlManager crowdControlManager;
    private final MonsterManager monsterManager;
    private final DamageService damageService;

    public HardControlTestToolListener(
            HardControlTestTool tool,
            CrowdControlManager crowdControlManager,
            MonsterManager monsterManager,
            DamageService damageService
    ) {
        this.tool = tool;
        this.crowdControlManager = crowdControlManager;
        this.monsterManager = monsterManager;
        this.damageService = damageService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !tool.isTestTool(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        Player player = event.getPlayer();
        if (!player.hasPermission(HardControlTestTool.PERMISSION)) {
            player.sendActionBar(Component.text(
                    "このアイテムを使用する権限がありません",
                    NamedTextColor.RED));
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (player.isSneaking()) {
            int ticks = tool.cycleDuration(item);
            player.sendActionBar(Component.text(
                    "ハードCC時間：%.1f秒".formatted(ticks / 20.0),
                    NamedTextColor.AQUA));
        } else {
            HardControlType type = tool.cycleMode(item);
            player.sendActionBar(Component.text(
                    "ハードCCモード：" + type.displayName(),
                    NamedTextColor.AQUA));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !tool.isTestTool(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity target
                && damageService.isApplying(player, target)) {
            return;
        }

        boolean permitted = player.hasPermission(HardControlTestTool.PERMISSION);
        event.setCancelled(true);
        event.setDamage(0);
        if (!permitted) {
            player.sendActionBar(Component.text(
                    "このアイテムを使用する権限がありません",
                    NamedTextColor.RED));
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity target)) {
            showCustomMonsterOnly(player);
            return;
        }
        CustomMonster monster = monsterManager.get(target.getUniqueId());
        if (monster == null
                || !monster.isValid()
                || !target.isValid()
                || target.isDead()
                || !monster.getEntity().equals(target)
                || !target.getWorld().equals(player.getWorld())) {
            showCustomMonsterOnly(player);
            return;
        }

        if (player.isSneaking()) {
            HardControlType previous = crowdControlManager.getType(target);
            crowdControlManager.clear(target, HardControlRemovalReason.DEV_TOOL);
            player.sendActionBar(Component.text(
                    previous == null
                            ? monster.getData().displayName() + "に有効なハードCCはありません"
                            : monster.getData().displayName() + "の"
                            + previous.displayName() + "を解除しました",
                    previous == null ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        HardControlType type = tool.getMode(item);
        int durationTicks = tool.getDurationTicks(item);
        HardControlApplicationResult result = crowdControlManager.apply(
                target, type, player, durationTicks);
        tool.showApplicationResult(player, monster, type, durationTicks, result);
    }

    private void showCustomMonsterOnly(Player player) {
        player.sendActionBar(Component.text(
                "このアイテムはProjectSカスタムモブ専用です",
                NamedTextColor.RED));
    }
}
