package io.github.gyai.projects.beta.activation.track1.bukkit;

import io.github.gyai.projects.beta.activation.track1.equipment.EquipmentInspectionService;
import io.github.gyai.projects.beta.activation.track1.player.LegacyPlayerProgressProjector;
import io.github.gyai.projects.beta.activation.track1.player.StagingPlayerProgressService;
import io.github.gyai.projects.manager.PlayerManager;
import io.github.gyai.projects.player.progress.PlayerProgressSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/** Bukkit callback boundary. No Player reference crosses into retained state. */
public final class BukkitTrack1PlayerListener implements Listener {
    private final PlayerManager playerManager;
    private final LegacyPlayerProgressProjector projector;
    private final StagingPlayerProgressService progress;
    private final EquipmentInspectionService equipment;
    private final CompatibleClientResolver compatibleClients;

    public BukkitTrack1PlayerListener(PlayerManager playerManager,
                                      StagingPlayerProgressService progress,
                                      EquipmentInspectionService equipment) {
        this(playerManager, progress, equipment, ignored -> false);
    }

    public BukkitTrack1PlayerListener(PlayerManager playerManager,
                                      StagingPlayerProgressService progress,
                                      EquipmentInspectionService equipment,
                                      CompatibleClientResolver compatibleClients) {
        this.playerManager = Objects.requireNonNull(playerManager, "playerManager");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.equipment = Objects.requireNonNull(equipment, "equipment");
        this.compatibleClients = Objects.requireNonNull(compatibleClients, "compatibleClients");
        projector = new LegacyPlayerProgressProjector();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerProgressSnapshot snapshot = projector.project(playerManager.getPlayerData(player));
        progress.onJoin(snapshot, player.getWorld().getName(),
                compatibleClients.hasCompatibleClient(player.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerProgressSnapshot snapshot = projector.project(playerManager.getPlayerData(player));
        progress.onQuit(snapshot, player.getWorld().getName(),
                compatibleClients.hasCompatibleClient(player.getUniqueId()));
        equipment.remove(player.getUniqueId());
    }
}
