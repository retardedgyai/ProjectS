package io.github.gyai.projects.manager;

import io.github.gyai.projects.player.PlayerData;
import io.github.gyai.projects.skill.SkillManager;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.dummy.TrainingDummySession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import io.github.gyai.projects.combat.classsystem.ClassManager;
import io.github.gyai.projects.combat.classsystem.ClassRegistry;
import io.github.gyai.projects.combat.resource.ResourceManager;
import io.github.gyai.projects.combat.classsystem.ScoutController;
import io.github.gyai.projects.combat.classsystem.WarriorController;
import io.github.gyai.projects.network.HudStatePacket;
import io.github.gyai.projects.network.HudStatePacket.SkillSlotState;

import java.util.List;

public class CombatHudManager {
    public static final int UPDATE_INTERVAL_TICKS = 8;
    private static final long TEMPORARY_MESSAGE_MILLIS = 1_000L;
    private static final String SPIN_SLASH_ID = "spin_slash";
    private static final String DODGE_ID = "dodge";

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final PlayerManager playerManager;
    private final SkillManager skillManager;
    private final TrainingDummyManager dummyManager;
    private final Map<UUID, TemporaryMessage> temporaryMessages = new HashMap<>();
    private final ClassManager classManager;
    private final ResourceManager resourceManager;
    private BukkitTask task;

    public CombatHudManager(
            JavaPlugin plugin,
            ItemManager itemManager,
            PlayerManager playerManager,
            SkillManager skillManager,
            TrainingDummyManager dummyManager,
            ClassManager classManager,
            ResourceManager resourceManager
    ) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.playerManager = playerManager;
        this.skillManager = skillManager;
        this.dummyManager = dummyManager;
        this.classManager = classManager;
        this.resourceManager = resourceManager;
    }

    public void start() {
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(
                    plugin, this::updateOnlinePlayers, 0L, UPDATE_INTERVAL_TICKS);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        temporaryMessages.clear();
    }

    public void showTemporary(Player player, Component message) {
        player.sendActionBar(message);
    }

    public void removePlayer(Player player) {
        temporaryMessages.remove(player.getUniqueId());
    }

    private void updateOnlinePlayers() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ClassRegistry.RegisteredClass activeClass = classManager.getActive(player);
            boolean starterSword = itemManager.isCustomItem(player.getInventory().getItemInMainHand(), "starter_sword");
            if (!starterSword && activeClass == null) {
                temporaryMessages.remove(player.getUniqueId());
                sendModHud(player, null);
                continue;
            }
            if (activeClass != null) {
                resourceManager.regenerate(player, activeClass.definition().resource(), UPDATE_INTERVAL_TICKS / 20.0);
            }
            sendModHud(player, activeClass);
        }
    }

    private void sendModHud(Player player, ClassRegistry.RegisteredClass activeClass) {
        if (activeClass == null) {
            player.sendPluginMessage(plugin, HudStatePacket.CHANNEL, new HudStatePacket(
                    false, "", "", 0.0f, 0.0f, List.of()).encode());
            return;
        }

        var definition = activeClass.definition();
        String resourceName = "";
        float resourceCurrent = 0.0f;
        float resourceMaximum = 0.0f;
        if (definition.resource().type()
                != io.github.gyai.projects.combat.resource.ResourceType.NONE) {
            resourceName = definition.resource().type().getDisplayName();
            resourceCurrent = (float) resourceManager.get(player, definition.resource());
            resourceMaximum = definition.resource().maximum();
        }

        List<SkillSlotState> slots;
        if (activeClass.controller() instanceof ScoutController scout) {
            slots = List.of(
                    new SkillSlotState(
                            "Q", "Rapid",
                            (float) skillManager.getRemainingCooldownSeconds(player, "scout_q"),
                            0, scout.getPassiveHits(player), true,
                            scout.getRapidVolleyRemainingSeconds(player) > 0.0),
                    SkillSlotState.locked("E", "未実装"),
                    new SkillSlotState(
                            "R", "Volley",
                            (float) skillManager.getRemainingCooldownSeconds(player, "scout_e"),
                            0, 0, true, false),
                    SkillSlotState.locked("F", "未実装")
            );
        } else if (activeClass.controller() instanceof WarriorController) {
            slots = List.of(
                    new SkillSlotState(
                            "Q", "回転斬り",
                            (float) skillManager.getRemainingCooldownSeconds(
                                    player, SPIN_SLASH_ID),
                            0, 0, true, false),
                    SkillSlotState.locked("E", "未実装"),
                    SkillSlotState.locked("R", "未実装"),
                    SkillSlotState.locked("F", "未実装")
            );
        } else {
            slots = List.of(
                    new SkillSlotState("Q", "Skill 1", 0, 0, 0, true, false),
                    new SkillSlotState("E", "Skill 2", 0, 0, 0, true, false),
                    new SkillSlotState("R", "Skill 3", 0, 0, 0, true, false),
                    new SkillSlotState("F", "Ultimate", 0, 0, 0, true, false)
            );
        }

        player.sendPluginMessage(plugin, HudStatePacket.CHANNEL, new HudStatePacket(
                true, definition.displayName(), resourceName,
                resourceCurrent, resourceMaximum, slots).encode());
    }

    private Component buildHud(Player player, long now, ClassRegistry.RegisteredClass activeClass) {
        PlayerData data = playerManager.getPlayerData(player);

        Component hud = Component.empty();
        TemporaryMessage temporary = temporaryMessages.get(player.getUniqueId());
        if (temporary != null) {
            if (temporary.expiresAtMillis() > now) {
                hud = hud.append(temporary.message()).append(Component.text(" | ", NamedTextColor.DARK_GRAY));
            } else {
                temporaryMessages.remove(player.getUniqueId());
            }
        }

        if (activeClass != null) {
            var resource = activeClass.definition().resource();
            if (resource.type() != io.github.gyai.projects.combat.resource.ResourceType.NONE) {
                double current = resourceManager.get(player, resource);
                hud = hud.append(Component.text("%s %.0f/%d (+%.1f/秒)".formatted(
                        resource.type().getDisplayName(), current, resource.maximum(),
                        resource.regenerationPerSecond()), NamedTextColor.BLUE));
            }
            Component selection = activeClass.controller().getSelectionHud(player);
            if (!selection.equals(Component.empty())) {
                if (!hud.equals(Component.empty())) {
                    hud = hud.append(Component.text(" | ", NamedTextColor.DARK_GRAY));
                }
                hud = hud.append(selection);
            }
        } else {
            hud = hud.append(buildFightingSpirit(data.getFightingSpirit()))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("回転斬り ", NamedTextColor.AQUA))
                .append(buildSkillStatus(player, SPIN_SLASH_ID, data.getFightingSpirit() < 30))
                .append(Component.text(" | 回避 ", NamedTextColor.DARK_AQUA))
                .append(buildSkillStatus(player, DODGE_ID, false));
        }
        TrainingDummySession session = dummyManager.getActiveSession(player, now);
        if (session != null) {
            hud = hud.append(Component.text(" | DPS %.1f | 3秒 %.1f | 合計 %.1f | %d HIT".formatted(
                    session.getAverageDps(now), session.getRecentDps(now),
                    session.getTotalDamage(), session.getHitCount()), NamedTextColor.LIGHT_PURPLE));
        }
        return hud;
    }

    private Component buildFightingSpirit(int value) {
        int filled = Math.clamp(value / 10, 0, 10);
        String gauge = "█".repeat(filled) + "░".repeat(10 - filled);
        return Component.text("闘気 [", NamedTextColor.GOLD)
                .append(Component.text(gauge, NamedTextColor.YELLOW))
                .append(Component.text("] %d/%d".formatted(value, PlayerData.MAX_FIGHTING_SPIRIT), NamedTextColor.GOLD));
    }

    private Component buildSkillStatus(Player player, String skillId, boolean resourceMissing) {
        double remaining = skillManager.getRemainingCooldownSeconds(player, skillId);
        if (remaining > 0.0) {
            return Component.text("%.1f秒".formatted(remaining), NamedTextColor.RED);
        }
        if (resourceMissing) {
            return Component.text("闘気不足", NamedTextColor.RED);
        }
        return Component.text("READY", NamedTextColor.GREEN);
    }

    private record TemporaryMessage(Component message, long expiresAtMillis) {
    }
}
