package io.github.gyai.projects.network;

import io.github.gyai.projects.combat.classsystem.WarriorCombatManager;
import io.github.gyai.projects.combat.classsystem.WarriorLoadout;
import io.github.gyai.projects.combat.classsystem.WarriorLoadoutManager;
import io.github.gyai.projects.combat.classsystem.ClassManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WarriorLoadoutChannel
        implements PluginMessageListener {
    private static final long REQUEST_DEBOUNCE_MILLIS = 150;

    private final JavaPlugin plugin;
    private final WarriorLoadoutManager loadouts;
    private final WarriorCombatManager combat;
    private final Map<UUID, Long> lastRequests = new HashMap<>();
    private ClassManager classManager;

    public WarriorLoadoutChannel(
            JavaPlugin plugin,
            WarriorLoadoutManager loadouts,
            WarriorCombatManager combat
    ) {
        this.plugin = plugin;
        this.loadouts = loadouts;
        this.combat = combat;
    }

    public void setClassManager(ClassManager classManager) {
        this.classManager = classManager;
    }

    @Override
    public void onPluginMessageReceived(
            @NotNull String channel,
            @NotNull Player player,
            byte @NotNull [] payload
    ) {
        long now = System.currentTimeMillis();
        long previous = lastRequests.getOrDefault(
                player.getUniqueId(), 0L);
        if (now - previous < REQUEST_DEBOUNCE_MILLIS) return;
        lastRequests.put(player.getUniqueId(), now);

        if (channel.equals(WarriorLoadoutRequestPacket.CHANNEL)) {
            var packet =
                    WarriorLoadoutRequestPacket.decode(payload).orElse(null);
            if (packet == null) {
                send(player, false, "不正なロードアウト要求です");
                return;
            }
            if (packet.action()
                    == WarriorLoadoutRequestPacket.Action.RESET) {
                var result = loadouts.resetToDefaults(player);
                send(player, result.success(), result.reason());
            } else {
                boolean warrior = combat.isWarrior(player);
                boolean inCombat = combat.isInCombat(player);
                send(player, warrior && !inCombat,
                        !warrior
                                ? "ウォーリアー装備中のみ変更できます"
                                : inCombat
                                ? "戦闘中はスキルを変更できません"
                                : "");
            }
            return;
        }
        if (channel.equals(WarriorLoadoutSelectPacket.CHANNEL)) {
            var packet =
                    WarriorLoadoutSelectPacket.decode(payload).orElse(null);
            if (packet == null) {
                send(player, false, "不正なロードアウト要求です");
                return;
            }
            var result = loadouts.select(
                    player, packet.slot(), packet.skillId());
            send(player, result.success(), result.reason());
        }
    }

    public void send(Player player, boolean success, String reason) {
        boolean warrior = combat.isWarrior(player);
        boolean inCombat = combat.isInCombat(player);
        var activeClass = classManager == null
                ? null : classManager.getActive(player);
        String classId = activeClass == null
                ? "" : activeClass.definition().id();
        WarriorLoadout current = warrior
                ? loadouts.get(player) : WarriorLoadout.defaults();
        player.sendPluginMessage(
                plugin,
                WarriorLoadoutStatePacket.CHANNEL,
                new WarriorLoadoutStatePacket(
                        warrior && !inCombat,
                        inCombat,
                        success,
                        classId,
                        reason == null ? "" : reason,
                        current).encode());
    }

    public void removePlayer(Player player) {
        lastRequests.remove(player.getUniqueId());
        loadouts.removePlayer(player);
    }

    public void clear() {
        lastRequests.clear();
    }
}
