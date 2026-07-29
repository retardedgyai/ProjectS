package io.github.gyai.projects.listener;

import io.github.gyai.projects.manager.MonsterManager;
import io.github.gyai.projects.monster.CustomMonster;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import java.util.Objects;

public final class MonsterListener implements Listener {
    private static final double DEFEAT_MESSAGE_RANGE_SQUARED = 48.0 * 48.0;

    private final MonsterManager monsterManager;

    public MonsterListener(MonsterManager monsterManager) {
        this.monsterManager = Objects.requireNonNull(monsterManager, "monsterManager");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        CustomMonster monster = monsterManager.get(event.getEntity().getUniqueId());
        if (monster != null) {
            monster.handleDamage(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        CustomMonster monster = monsterManager.get(event.getEntity().getUniqueId());
        if (monster == null) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);
        for (Player player : event.getEntity().getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(event.getEntity().getLocation())
                    <= DEFEAT_MESSAGE_RANGE_SQUARED) {
                player.sendMessage("§6港喰らいの巨獣 グロームを討伐した！");
            }
        }
        monster.handleDeath(event);
        monsterManager.forget(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (monsterManager.isCustomMonster(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!monsterManager.isCustomMonster(event.getEntity())) {
            return;
        }
        if (event.getTarget() == null) {
            return;
        }
        if (!(event.getTarget() instanceof Player player)
                || player.isDead()
                || player.getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }
}
