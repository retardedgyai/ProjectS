package io.github.gyai.projects.network;

import io.github.gyai.projects.input.CombatInputManager;
import io.github.gyai.projects.listener.RangedWeaponListener;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public class ClientInputListener implements PluginMessageListener {
    public static final String CHANNEL = "projects:skill_input";

    private final CombatInputManager combatInputManager;
    private final RangedWeaponListener rangedWeaponListener;
    private final Logger logger;
    private final boolean debug;
    private final Map<UUID, ReceivedInput> lastInputs = new HashMap<>();
    private Consumer<Player> devMenuOpener;

    public ClientInputListener(
            CombatInputManager combatInputManager,
            RangedWeaponListener rangedWeaponListener,
            Logger logger,
            boolean debug
    ) {
        this.combatInputManager = combatInputManager;
        this.rangedWeaponListener = rangedWeaponListener;
        this.logger = logger;
        this.debug = debug;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }

        ClientInputPacket.decode(message).ifPresent(packet -> {
            lastInputs.put(player.getUniqueId(), new ReceivedInput(
                    packet.inputType(), System.currentTimeMillis()));
            if (debug) {
                logger.info("[ProjectS] %s から %s を受信".formatted(
                        player.getName(), packet.inputType().name()));
            }
            switch (packet.inputType()) {
                case BOW_FIRE_START -> rangedWeaponListener.setFiring(player, true);
                case BOW_FIRE_STOP -> rangedWeaponListener.setFiring(player, false);
                case OPEN_DEV_MENU -> openDevMenu(player);
                default -> combatInputManager.handle(player, packet.inputType());
            }
        });
    }

    public void setDevMenuOpener(Consumer<Player> devMenuOpener) {
        this.devMenuOpener = Objects.requireNonNull(devMenuOpener, "devMenuOpener");
    }

    public ReceivedInput getLastInput(Player player) {
        return lastInputs.get(player.getUniqueId());
    }

    public void removePlayer(Player player) {
        lastInputs.remove(player.getUniqueId());
    }

    private void openDevMenu(Player player) {
        if (devMenuOpener == null) {
            logger.warning("[ProjectS] Dev Menuを開く処理が初期化されていません。");
            return;
        }
        devMenuOpener.accept(player);
    }

    public record ReceivedInput(SkillInputType inputType, long receivedAtMillis) {
    }
}
