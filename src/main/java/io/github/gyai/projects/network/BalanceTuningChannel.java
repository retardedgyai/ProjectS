package io.github.gyai.projects.network;

import io.github.gyai.projects.dev.DevMenuManager;
import io.github.gyai.projects.manager.BalanceTuningManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BalanceTuningChannel implements PluginMessageListener {
    public static final String REQUEST_CHANNEL = "projects:balance_req_v1";
    public static final String UPDATE_CHANNEL = "projects:balance_upd_v1";
    public static final String ACTION_CHANNEL = "projects:balance_act_v1";
    private static final long DEBOUNCE_MILLIS = 100;

    private final JavaPlugin plugin;
    private final BalanceTuningManager manager;
    private final Map<UUID, Long> lastRequests = new HashMap<>();

    public BalanceTuningChannel(
            JavaPlugin plugin,
            BalanceTuningManager manager
    ) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void onPluginMessageReceived(
            @NotNull String channel,
            @NotNull Player player,
            byte @NotNull [] payload
    ) {
        if (!channel.equals(REQUEST_CHANNEL)
                && !channel.equals(UPDATE_CHANNEL)
                && !channel.equals(ACTION_CHANNEL)) {
            return;
        }
        if (payload.length == 0
                || payload.length > BalancePacketIO.MAX_PAYLOAD_BYTES) {
            send(player, false, "Payloadサイズが不正です");
            return;
        }
        if (!player.hasPermission(DevMenuManager.PERMISSION)) {
            send(player, false, "projects.dev 権限がありません");
            return;
        }
        long now = System.currentTimeMillis();
        long previous = lastRequests.getOrDefault(
                player.getUniqueId(), 0L);
        if (now - previous < DEBOUNCE_MILLIS) {
            send(player, false, "操作が速すぎます");
            return;
        }
        lastRequests.put(player.getUniqueId(), now);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            int version = input.readUnsignedByte();
            if (version != BalancePacketIO.VERSION) {
                send(player, false, "未対応の通信バージョンです");
                return;
            }
            if (channel.equals(REQUEST_CHANNEL)) {
                if (input.readUnsignedByte() != 0 || input.available() != 0) {
                    throw new IOException("Invalid request");
                }
                send(player, true, "");
            } else if (channel.equals(UPDATE_CHANNEL)) {
                handleUpdate(player, input);
            } else {
                handleAction(player, input);
            }
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().warning(
                    "不正なバランスPayloadを拒否しました: "
                            + exception.getMessage());
            send(player, false, "不正なバランス調整要求です");
        }
    }

    private void handleUpdate(
            Player player,
            DataInputStream input
    ) throws IOException {
        long revision = input.readLong();
        int count = input.readUnsignedByte();
        if (count < 1 || count > 32) throw new IOException("Invalid edit count");
        List<BalanceTuningManager.Edit> edits = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int targetCode = input.readUnsignedByte();
            String id = BalancePacketIO.readString(input, 64);
            int fieldCode = input.readUnsignedByte();
            double value = input.readDouble();
            edits.add(new BalanceTuningManager.Edit(
                    decodeTarget(targetCode),
                    id,
                    decodeField(targetCode, fieldCode),
                    value));
        }
        if (input.available() != 0) throw new IOException("Trailing bytes");
        var result = manager.apply(revision, edits);
        send(player, result.success(), result.message());
    }

    private void handleAction(
            Player player,
            DataInputStream input
    ) throws IOException {
        long revision = input.readLong();
        int action = input.readUnsignedByte();
        if (action == 0) {
            if (input.available() != 0) throw new IOException("Trailing bytes");
            if (revision != manager.snapshot().revision()) {
                send(player, false, "revision競合: 最新状態を再取得しました");
                return;
            }
            manager.saveAsync(result -> {
                if (player.isOnline()) {
                    send(player, result.success(), result.message());
                }
            });
            send(player, true, "保存中...");
            return;
        }
        if (action == 1) {
            if (input.available() != 0) throw new IOException("Trailing bytes");
            if (revision != manager.snapshot().revision()) {
                send(player, false, "revision競合: 最新状態を再取得しました");
                return;
            }
            manager.reloadAsync(result -> {
                if (player.isOnline()) {
                    send(player, result.success(), result.message());
                }
            });
            send(player, true, "再読み込み中...");
            return;
        }
        BalanceTuningManager.OperationResult result;
        if (action == 2) {
            BalanceTuningManager.Target target =
                    decodeTarget(input.readUnsignedByte());
            String id = BalancePacketIO.readString(input, 64);
            if (input.available() != 0) throw new IOException("Trailing bytes");
            result = manager.resetSelected(revision, target, id);
        } else if (action == 3) {
            if (input.available() != 0) throw new IOException("Trailing bytes");
            result = manager.resetAll(revision);
        } else {
            throw new IOException("Unknown action");
        }
        send(player, result.success(), result.message());
    }

    private BalanceTuningManager.Target decodeTarget(int code)
            throws IOException {
        return switch (code) {
            case 0 -> BalanceTuningManager.Target.WEAPON;
            case 1 -> BalanceTuningManager.Target.SKILL;
            default -> throw new IOException("Unknown target");
        };
    }

    private BalanceTuningManager.Field decodeField(
            int target,
            int field
    ) throws IOException {
        if (target == 0) {
            return switch (field) {
                case 0 -> BalanceTuningManager.Field.ATTACK_POWER;
                case 1 -> BalanceTuningManager.Field.ATTACK_SPEED;
                default -> throw new IOException("Unknown weapon field");
            };
        }
        if (target == 1) {
            return switch (field) {
                case 0 -> BalanceTuningManager.Field.BASE_DAMAGE;
                case 1 -> BalanceTuningManager.Field.ATTACK_POWER_SCALING;
                default -> throw new IOException("Unknown skill field");
            };
        }
        throw new IOException("Unknown target");
    }

    public void send(Player player, boolean success, String message) {
        boolean permitted = player.hasPermission(DevMenuManager.PERMISSION);
        player.sendPluginMessage(
                plugin,
                BalanceStatePacket.CHANNEL,
                new BalanceStatePacket(
                        permitted, success, message,
                        manager.snapshot()).encode());
    }

    public void removePlayer(Player player) {
        lastRequests.remove(player.getUniqueId());
    }

    public void clear() {
        lastRequests.clear();
    }
}
