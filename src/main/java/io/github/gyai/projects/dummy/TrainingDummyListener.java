package io.github.gyai.projects.dummy;

import io.github.gyai.projects.combat.classsystem.WarriorCombatManager;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class TrainingDummyListener implements Listener {
    private final TrainingDummyManager dummyManager;
    private final WarriorCombatManager warriorCombatManager;

    public TrainingDummyListener(
            TrainingDummyManager dummyManager,
            WarriorCombatManager warriorCombatManager
    ) {
        this.dummyManager = dummyManager;
        this.warriorCombatManager = warriorCombatManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDummyDamage(EntityDamageEvent event) {
        if (!dummyManager.isTrainingDummy(event.getEntity())) {
            return;
        }

        Player player = event instanceof EntityDamageByEntityEvent damageByEntity
                ? findPlayerDamager(damageByEntity) : null;
        if (!event.isCancelled() && player != null && event.getFinalDamage() > 0.0) {
            warriorCombatManager.recordConfirmedTrainingDummyHit(
                    (EntityDamageByEntityEvent) event);
            dummyManager.recordDamage(player, (ArmorStand) event.getEntity(), event.getFinalDamage());
        }
        event.setCancelled(true);
    }

    private Player findPlayerDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDummyCombust(EntityCombustEvent event) {
        if (dummyManager.isTrainingDummy(event.getEntity())) {
            event.setCancelled(true);
            event.getEntity().setFireTicks(0);
        }
    }
}
