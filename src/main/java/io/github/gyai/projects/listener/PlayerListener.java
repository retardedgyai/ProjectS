package io.github.gyai.projects.listener;

import io.github.gyai.projects.manager.PlayerManager;
import io.github.gyai.projects.skill.SkillManager;
import io.github.gyai.projects.manager.CombatHudManager;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import io.github.gyai.projects.combat.classsystem.ClassManager;
import io.github.gyai.projects.combat.resource.ResourceManager;
import org.bukkit.event.entity.PlayerDeathEvent;
import io.github.gyai.projects.combat.classsystem.WarriorLoadoutManager;

public class PlayerListener implements Listener {
    private final PlayerManager playerManager;
    private final SkillManager skillManager;
    private final CombatHudManager hudManager;
    private final TrainingDummyManager dummyManager;
    private final ClassManager classManager;
    private final ResourceManager resourceManager;
    private final WarriorLoadoutManager warriorLoadoutManager;

    public PlayerListener(
            PlayerManager playerManager,
            SkillManager skillManager,
            CombatHudManager hudManager,
            TrainingDummyManager dummyManager,
            ClassManager classManager,
            ResourceManager resourceManager,
            WarriorLoadoutManager warriorLoadoutManager
    ) {
        this.playerManager = playerManager;
        this.skillManager = skillManager;
        this.hudManager = hudManager;
        this.dummyManager = dummyManager;
        this.classManager = classManager;
        this.resourceManager = resourceManager;
        this.warriorLoadoutManager = warriorLoadoutManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerManager.initializePlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        classManager.removePlayer(event.getPlayer());
        skillManager.removePlayer(event.getPlayer());
        hudManager.removePlayer(event.getPlayer());
        dummyManager.removePlayer(event.getPlayer());
        resourceManager.removePlayer(event.getPlayer());
        warriorLoadoutManager.removePlayer(event.getPlayer());
        playerManager.removePlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        classManager.removePlayer(event.getPlayer());
    }
}
