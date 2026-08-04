package io.github.gyai.projects.skill;

import io.github.gyai.projects.manager.PlayerManager;
import io.github.gyai.projects.manager.CombatHudManager;
import io.github.gyai.projects.player.PlayerData;
import io.github.gyai.projects.combat.stat.StatCalculator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class SkillManager {
    private final PlayerManager playerManager;
    private final Map<String, Skill> skills = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldownEnds = new HashMap<>();
    private CombatHudManager hudManager;
    private final Set<UUID> fullCooldownReduction = new HashSet<>();

    public SkillManager(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    public void register(Skill skill) {
        skills.put(skill.getId(), skill);
    }

    public void setHudManager(CombatHudManager hudManager) {
        this.hudManager = hudManager;
    }

    public boolean isRegistered(String skillId) {
        return skills.containsKey(skillId);
    }

    public Skill getSkill(String skillId) {
        return skills.get(skillId);
    }

    public Collection<Skill> getSkills() {
        return java.util.List.copyOf(skills.values());
    }

    public Map<String, Double> getActiveCooldowns(Player player) {
        Map<String, Long> values = cooldownEnds.get(player.getUniqueId());
        if (values == null) {
            return Map.of();
        }
        long now = System.currentTimeMillis();
        Map<String, Double> result = new HashMap<>();
        values.forEach((id, end) -> {
            if (end > now) {
                result.put(id, (end - now) / 1_000.0);
            }
        });
        return Map.copyOf(result);
    }

    public void clearCooldowns(Player player) {
        cooldownEnds.remove(player.getUniqueId());
    }

    public boolean useSkill(Player player, String skillId) {
        Skill skill = skills.get(skillId);
        if (skill == null || !skill.isEnabled()) {
            return false;
        }

        double remaining = getRemainingCooldownSeconds(player, skillId);
        if (remaining > 0.0) {
            showTemporary(player, Component.text(
                    "%s CD: %.1f秒".formatted(skill.getDisplayName(), remaining),
                    NamedTextColor.RED
            ));
            return false;
        }

        Skill.PreparedUse prepared = skill.prepare(player).orElse(null);
        if (prepared == null) {
            return false;
        }

        PlayerData data = playerManager.getPlayerData(player);
        int resourceCost = prepared.resourceCost();
        if (data.getFightingSpirit() < resourceCost) {
            showTemporary(player, Component.text(
                    "闘気が足りません %d/%d".formatted(
                            data.getFightingSpirit(), resourceCost),
                    NamedTextColor.RED
            ));
            return false;
        }

        data.consumeFightingSpirit(resourceCost);
        startCooldown(player, skillId, skill.getBaseCooldownSeconds(),
                data.getCooldownRecoveryPercent());
        try {
            prepared.execution().run();
        } catch (RuntimeException exception) {
            data.addFightingSpirit(resourceCost);
            clearCooldown(player, skillId);
            throw exception;
        }
        showTemporary(player, Component.text(skill.getDisplayName() + "！", NamedTextColor.AQUA));
        return true;
    }

    private void showTemporary(Player player, Component message) {
        if (hudManager != null) {
            hudManager.showTemporary(player, message);
        }
    }

    public void startCooldown(
            Player player,
            String id,
            double baseSeconds,
            double cooldownRecoveryPercent
    ) {
        if (fullCooldownReduction.contains(player.getUniqueId())) {
            return;
        }
        long durationMillis = Math.round(StatCalculator.cooldownSeconds(
                baseSeconds, cooldownRecoveryPercent) * 1_000.0);
        cooldownEnds.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .put(id, System.currentTimeMillis() + durationMillis);
    }

    public double getRemainingCooldownSeconds(Player player, String id) {
        Map<String, Long> playerCooldowns = cooldownEnds.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return 0.0;
        }
        long remainingMillis = playerCooldowns.getOrDefault(id, 0L) - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            playerCooldowns.remove(id);
            return 0.0;
        }
        return remainingMillis / 1_000.0;
    }

    public void clearCooldown(Player player, String id) {
        Map<String, Long> playerCooldowns = cooldownEnds.get(player.getUniqueId());
        if (playerCooldowns == null) return;
        playerCooldowns.remove(id);
        if (playerCooldowns.isEmpty()) cooldownEnds.remove(player.getUniqueId());
    }

    public void reduceCooldown(Player player, String id, double seconds) {
        if (seconds <= 0.0) return;
        Map<String, Long> playerCooldowns = cooldownEnds.get(player.getUniqueId());
        if (playerCooldowns == null) return;
        Long currentEnd = playerCooldowns.get(id);
        if (currentEnd == null) return;
        long newEnd = currentEnd - Math.round(seconds * 1_000.0);
        if (newEnd <= System.currentTimeMillis()) {
            playerCooldowns.remove(id);
        } else {
            playerCooldowns.put(id, newEnd);
        }
        if (playerCooldowns.isEmpty()) cooldownEnds.remove(player.getUniqueId());
    }

    public void removePlayer(Player player) {
        cooldownEnds.remove(player.getUniqueId());
        fullCooldownReduction.remove(player.getUniqueId());
    }

    public boolean toggleFullCooldownReduction(Player player) {
        UUID id = player.getUniqueId();
        if (!fullCooldownReduction.add(id)) fullCooldownReduction.remove(id);
        else clearCooldowns(player);
        return fullCooldownReduction.contains(id);
    }

    public boolean hasFullCooldownReduction(Player player) {
        return fullCooldownReduction.contains(player.getUniqueId());
    }

    public void clear() {
        cooldownEnds.clear();
        skills.clear();
        fullCooldownReduction.clear();
    }
}
