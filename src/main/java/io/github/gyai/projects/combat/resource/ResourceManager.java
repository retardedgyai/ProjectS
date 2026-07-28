package io.github.gyai.projects.combat.resource;

import io.github.gyai.projects.manager.PlayerManager;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ResourceManager {
    private final PlayerManager playerManager;
    private final Map<UUID, Double> mana = new HashMap<>();
    private final Set<UUID> infiniteMana = new HashSet<>();

    public ResourceManager(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    public double get(Player player, ResourceDefinition definition) {
        return switch (definition.type()) {
            case FIGHTING_SPIRIT -> playerManager.getPlayerData(player).getFightingSpirit();
            case MANA -> mana.computeIfAbsent(player.getUniqueId(), ignored -> (double) definition.maximum());
            case ENERGY, RAGE, NONE -> 0;
        };
    }

    public void set(Player player, ResourceDefinition definition, double value) {
        double clamped = Math.clamp(value, 0, definition.maximum());
        if (definition.type() == ResourceType.FIGHTING_SPIRIT) {
            playerManager.getPlayerData(player).setFightingSpirit((int) Math.round(clamped));
        } else if (definition.type() == ResourceType.MANA) {
            mana.put(player.getUniqueId(), clamped);
        }
    }

    public boolean consume(Player player, ResourceDefinition definition, double amount) {
        if (definition.type() == ResourceType.MANA && infiniteMana.contains(player.getUniqueId())) return true;
        double current = get(player, definition);
        if (amount < 0 || current < amount) return false;
        set(player, definition, current - amount);
        return true;
    }

    public void regenerate(Player player, ResourceDefinition definition, double elapsedSeconds) {
        if (definition.regenerationPerSecond() > 0) {
            set(player, definition, get(player, definition) + definition.regenerationPerSecond() * elapsedSeconds);
        }
    }

    public boolean toggleInfiniteMana(Player player) {
        UUID id = player.getUniqueId();
        if (!infiniteMana.add(id)) infiniteMana.remove(id);
        return infiniteMana.contains(id);
    }

    public boolean hasInfiniteMana(Player player) { return infiniteMana.contains(player.getUniqueId()); }

    public void removePlayer(Player player) {
        mana.remove(player.getUniqueId());
        infiniteMana.remove(player.getUniqueId());
    }

    public void clear() { mana.clear(); infiniteMana.clear(); }
}
