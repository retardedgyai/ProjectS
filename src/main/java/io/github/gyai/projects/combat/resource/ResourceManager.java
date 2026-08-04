package io.github.gyai.projects.combat.resource;

import io.github.gyai.projects.manager.PlayerManager;
import io.github.gyai.projects.combat.stat.StatCalculator;
import io.github.gyai.projects.player.StatType;
import io.github.gyai.projects.player.Stats;
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
            case MANA -> {
                double maximum = maximum(player, definition);
                double current = mana.computeIfAbsent(
                        player.getUniqueId(), ignored -> maximum);
                double clamped = Math.clamp(current, 0.0, maximum);
                if (clamped != current) mana.put(player.getUniqueId(), clamped);
                yield clamped;
            }
            case ENERGY, RAGE, NONE -> 0;
        };
    }

    public void set(Player player, ResourceDefinition definition, double value) {
        double clamped = Math.clamp(
                StatCalculator.finiteOrZero(value), 0, maximum(player, definition));
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
        regenerate(player, definition, elapsedSeconds, false);
    }

    public void regenerate(
            Player player,
            ResourceDefinition definition,
            double elapsedSeconds,
            boolean outOfCombat
    ) {
        double regeneration = regenerationPerSecond(
                player, definition, outOfCombat);
        if (regeneration > 0.0 && elapsedSeconds > 0.0) {
            set(player, definition, get(player, definition)
                    + regeneration * elapsedSeconds);
        }
    }

    public double maximum(Player player, ResourceDefinition definition) {
        if (definition.type() == ResourceType.FIGHTING_SPIRIT) {
            return definition.maximum();
        }
        Stats stats = playerManager.getPlayerData(player).getStats();
        if (definition.type() == ResourceType.MANA) {
            return StatCalculator.maximumMana(
                    definition.maximum(),
                    stats.get(StatType.MAX_MANA_FLAT),
                    stats.get(StatType.MAX_MANA_PERCENT));
        }
        return StatCalculator.maximumMana(
                definition.maximum(),
                stats.get(StatType.MAX_RESOURCE_FLAT),
                stats.get(StatType.MAX_RESOURCE_PERCENT));
    }

    public double regenerationPerSecond(
            Player player,
            ResourceDefinition definition,
            boolean outOfCombat
    ) {
        Stats stats = playerManager.getPlayerData(player).getStats();
        if (definition.type() == ResourceType.MANA) {
            return StatCalculator.manaRegeneration(
                    definition.regenerationPerSecond(),
                    stats.get(StatType.MANA_REGEN_FLAT),
                    stats.get(StatType.MANA_REGEN_PERCENT),
                    outOfCombat);
        }
        return StatCalculator.manaRegeneration(
                definition.regenerationPerSecond(),
                stats.get(StatType.RESOURCE_REGEN_FLAT),
                stats.get(StatType.RESOURCE_REGEN_PERCENT),
                false);
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
