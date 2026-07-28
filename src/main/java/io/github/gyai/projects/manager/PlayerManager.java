package io.github.gyai.projects.manager;

import io.github.gyai.projects.player.PlayerData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerManager {
    private final Map<UUID, PlayerData> players = new HashMap<>();

    public PlayerData initializePlayer(Player player) {
        return players.computeIfAbsent(player.getUniqueId(), PlayerData::new);
    }

    public PlayerData getPlayerData(Player player) {
        return initializePlayer(player);
    }

    public void removePlayer(Player player) {
        players.remove(player.getUniqueId());
    }

    public void clear() {
        players.clear();
    }
}
