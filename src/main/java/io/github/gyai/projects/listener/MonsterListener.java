package io.github.gyai.projects.listener;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.github.gyai.projects.combat.skill.CrowdControlManager;
import io.github.gyai.projects.combat.skill.HardControlRemovalReason;
import io.github.gyai.projects.combat.skill.HardControlType;
import io.github.gyai.projects.manager.MonsterManager;
import io.github.gyai.projects.monster.CustomMonster;
import io.github.gyai.projects.status.StatusEffectManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import java.util.Objects;

public final class MonsterListener implements Listener {
    private static final double DEFEAT_MESSAGE_RANGE_SQUARED = 48.0 * 48.0;

    private final MonsterManager monsterManager;
    private final CrowdControlManager crowdControlManager;
    private final StatusEffectManager statusEffectManager;

    public MonsterListener(
            MonsterManager monsterManager,
            CrowdControlManager crowdControlManager,
            StatusEffectManager statusEffectManager
    ) {
        this.monsterManager = Objects.requireNonNull(monsterManager, "monsterManager");
        this.crowdControlManager = Objects.requireNonNull(
                crowdControlManager, "crowdControlManager");
        this.statusEffectManager = Objects.requireNonNull(
                statusEffectManager, "statusEffectManager");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onControlledAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof org.bukkit.entity.LivingEntity attacker)) {
            return;
        }
        HardControlType type = crowdControlManager.getType(attacker);
        if (type == HardControlType.STUN
                || type == HardControlType.FEAR
                || type == HardControlType.CHARM) {
            event.setCancelled(true);
        }
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
        crowdControlManager.clear(
                event.getEntity(), HardControlRemovalReason.ENTITY_INVALID);
        statusEffectManager.clear(event.getEntity());
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
        if (event.getEntity() instanceof org.bukkit.entity.LivingEntity living) {
            HardControlType type = crowdControlManager.getType(living);
            if (type == HardControlType.STUN
                    || type == HardControlType.FEAR
                    || type == HardControlType.CHARM) {
                event.setCancelled(true);
                return;
            }
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemoved(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.LivingEntity living) {
            crowdControlManager.clear(
                    living, HardControlRemovalReason.ENTITY_INVALID);
            statusEffectManager.clear(living);
        }
    }
}
