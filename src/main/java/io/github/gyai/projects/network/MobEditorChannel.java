package io.github.gyai.projects.network;

import io.github.gyai.projects.manager.MonsterManager;
import io.github.gyai.projects.monster.editor.HeadDefinition;
import io.github.gyai.projects.monster.editor.MobDefinition;
import io.github.gyai.projects.monster.editor.MobEditorManager;
import io.github.gyai.projects.monster.editor.MobEditorPermissions;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MobEditorChannel implements PluginMessageListener, Listener {
    public static final String REQUEST_CHANNEL = "projects:mob_editor_req_v1";
    public static final int OPEN = 0;
    public static final int REQUEST_DETAIL = 1;
    public static final int CREATE_DRAFT = 2;
    public static final int UPDATE_DRAFT = 3;
    public static final int VALIDATE_DRAFT = 4;
    public static final int SAVE_DRAFT = 5;
    public static final int APPLY_DEFINITION = 6;
    public static final int TEST_SPAWN = 7;
    public static final int DESPAWN_TEST_MOBS = 8;
    public static final int CONTROL_TEST_MOBS = 9;
    public static final int REQUEST_HEAD_LIST = 10;
    public static final int REQUEST_HEAD_DETAIL = 11;
    public static final int CREATE_HEAD = 12;
    public static final int RELOAD = 13;
    public static final int CLOSE = 14;
    public static final int DESPAWN_ALL_TEST_MOBS = 15;
    public static final int REQUEST_MOB_LIST = 16;
    public static final int UPDATE_HEAD_FAVORITE = 17;

    private static final long DEBOUNCE_MILLIS = 40;
    private static final long PERSISTENT_WRITE_COOLDOWN_MILLIS = 1_000;
    private final JavaPlugin plugin;
    private final MobEditorManager manager;
    private final MonsterManager monsterManager;
    private final Map<UUID, Long> lastRequests = new HashMap<>();
    private final Map<UUID, Long> lastPersistentWrites = new HashMap<>();
    private final Map<UUID, Long> stateIoInFlight = new HashMap<>();
    private long stateIoGeneration;

    public MobEditorChannel(
            JavaPlugin plugin,
            MobEditorManager manager,
            MonsterManager monsterManager
    ) {
        this.plugin = plugin;
        this.manager = manager;
        this.monsterManager = monsterManager;
    }

    @Override
    public void onPluginMessageReceived(
            @NotNull String channel,
            @NotNull Player player,
            byte @NotNull [] payload
    ) {
        if (!channel.equals(REQUEST_CHANNEL)) return;
        if (payload.length < 2 || payload.length > MobEditorPacketIO.MAX_PAYLOAD_BYTES) {
            sendDenied(player, "Payloadサイズが不正です");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRequests.getOrDefault(player.getUniqueId(), 0L)
                < DEBOUNCE_MILLIS) {
            sendDenied(player, "操作が速すぎます");
            return;
        }
        lastRequests.put(player.getUniqueId(), now);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            if (input.readUnsignedByte() != MobEditorPacketIO.VERSION) {
                throw new IOException("Unsupported version");
            }
            int operation = input.readUnsignedByte();
            String requiredPermission = permission(operation);
            if (!player.hasPermission(requiredPermission)) {
                sendDenied(player, requiredPermission + " 権限がありません");
                return;
            }
            if (containsAppearance(operation)
                    && !player.hasPermission(MobEditorPermissions.APPEARANCE)) {
                sendDenied(player,
                        MobEditorPermissions.APPEARANCE + " 権限がありません");
                return;
            }
            if (operation == TEST_SPAWN
                    && !player.hasPermission(MobEditorPermissions.EDIT)) {
                sendDenied(player, MobEditorPermissions.EDIT + " 権限がありません");
                return;
            }
            if (manager.reloading() && operation != CLOSE) {
                sendDenied(player, "Mob Editorの再読み込み中です");
                return;
            }
            handle(player, operation, input);
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().warning("不正なMob Editor Payloadを拒否しました: "
                    + exception.getMessage());
            sendDenied(player, "不正なMob Editor要求です");
        }
    }

    private void handle(Player player, int operation, DataInputStream input)
            throws IOException {
        switch (operation) {
            case OPEN -> {
                requireEnd(input);
                send(player, manager.open(player, ""));
            }
            case REQUEST_DETAIL -> {
                String id = MobEditorPacketIO.readString(input, 64);
                requireEnd(input);
                send(player, manager.select(player, id));
            }
            case CREATE_DRAFT -> {
                String id = MobEditorPacketIO.readString(input, 64);
                requireEnd(input);
                send(player, manager.create(player, id));
            }
            case UPDATE_DRAFT, VALIDATE_DRAFT -> {
                MobDefinition definition = MobEditorPacketIO.readMob(input);
                requireEnd(input);
                send(player, manager.update(player, definition));
            }
            case SAVE_DRAFT -> {
                MobDefinition definition = MobEditorPacketIO.readMob(input);
                requireEnd(input);
                var updated = manager.update(player, definition);
                if (!updated.success()) {
                    send(player, updated);
                    return;
                }
                long ioToken = beginStateIo(player, true);
                if (ioToken == 0) return;
                send(player, manager.snapshot(
                        player, true, false, "保存中...", "", 0));
                manager.saveAsync(player,
                        snapshot -> finishStateIo(player, ioToken, snapshot));
            }
            case APPLY_DEFINITION -> {
                requireEnd(input);
                send(player, manager.apply(player));
            }
            case TEST_SPAWN -> {
                MobDefinition definition = MobEditorPacketIO.readMob(input);
                boolean cursor = input.readBoolean();
                requireEnd(input);
                var updated = manager.update(player, definition);
                send(player, updated.success()
                        ? manager.testSpawn(player, cursor) : updated);
            }
            case DESPAWN_TEST_MOBS -> {
                requireEnd(input);
                send(player, manager.despawnTests(player));
            }
            case CONTROL_TEST_MOBS -> {
                int code = input.readUnsignedByte();
                requireEnd(input);
                if (code >= MonsterManager.TestControl.values().length) {
                    throw new IOException("Invalid test control");
                }
                send(player, manager.controlTests(
                        player, MonsterManager.TestControl.values()[code]));
            }
            case REQUEST_HEAD_LIST -> {
                String query = MobEditorPacketIO.readString(input, 64);
                int page = input.readUnsignedShort();
                requireEnd(input);
                send(player, manager.headList(player, query, page));
            }
            case REQUEST_HEAD_DETAIL -> {
                String id = MobEditorPacketIO.readString(input, 64);
                String query = MobEditorPacketIO.readString(input, 64);
                int page = input.readUnsignedShort();
                requireEnd(input);
                send(player, manager.selectHead(player, id, query, page));
            }
            case CREATE_HEAD -> {
                HeadDefinition head = MobEditorPacketIO.readHead(input);
                requireEnd(input);
                long ioToken = beginStateIo(player, true);
                if (ioToken == 0) return;
                send(player, manager.snapshot(
                        player, true, false, "Head保存中...", "", 0));
                manager.createHeadAsync(
                        player, head,
                        snapshot -> finishStateIo(player, ioToken, snapshot));
            }
            case RELOAD -> {
                requireEnd(input);
                long ioToken = beginStateIo(player, false);
                if (ioToken == 0) return;
                send(player, manager.snapshot(
                        player, true, false, "再読み込み中...", "", 0));
                manager.reloadAsync(
                        player, snapshot -> finishStateIo(player, ioToken, snapshot));
            }
            case CLOSE -> {
                requireEnd(input);
                manager.close(player);
            }
            case DESPAWN_ALL_TEST_MOBS -> {
                requireEnd(input);
                int count = monsterManager.removeAllTestMobs();
                send(player, manager.snapshot(player, true, false,
                        count + "体の全テスト個体を削除しました", "", 0));
            }
            case REQUEST_MOB_LIST -> {
                String query = MobEditorPacketIO.readString(input, 64);
                int page = input.readUnsignedShort();
                requireEnd(input);
                send(player, manager.mobList(player, query, page));
            }
            case UPDATE_HEAD_FAVORITE -> {
                String id = MobEditorPacketIO.readString(input, 64);
                long revision = Long.parseLong(MobEditorPacketIO.readString(input, 32));
                boolean favorite = input.readBoolean();
                requireEnd(input);
                long ioToken = beginStateIo(player, true);
                if (ioToken == 0) return;
                send(player, manager.snapshot(
                        player, true, false, "お気に入り更新中...", "", 0));
                manager.updateHeadFavoriteAsync(player, id, revision, favorite,
                        snapshot -> finishStateIo(player, ioToken, snapshot));
            }
            default -> throw new IOException("Unknown operation");
        }
    }

    private static String permission(int operation) throws IOException {
        return switch (operation) {
            case OPEN, REQUEST_DETAIL, REQUEST_HEAD_LIST, REQUEST_HEAD_DETAIL,
                    REQUEST_MOB_LIST, CLOSE ->
                    MobEditorPermissions.OPEN;
            case CREATE_DRAFT, UPDATE_DRAFT, VALIDATE_DRAFT, SAVE_DRAFT ->
                    MobEditorPermissions.EDIT;
            case APPLY_DEFINITION -> MobEditorPermissions.APPLY;
            case TEST_SPAWN, DESPAWN_TEST_MOBS, CONTROL_TEST_MOBS ->
                    MobEditorPermissions.TEST;
            case CREATE_HEAD, UPDATE_HEAD_FAVORITE -> MobEditorPermissions.HEAD_IMPORT;
            case RELOAD, DESPAWN_ALL_TEST_MOBS -> MobEditorPermissions.RELOAD;
            default -> throw new IOException("Unknown operation");
        };
    }

    private static boolean containsAppearance(int operation) {
        return operation == UPDATE_DRAFT || operation == VALIDATE_DRAFT
                || operation == SAVE_DRAFT || operation == TEST_SPAWN;
    }

    private void send(Player player, MobEditorManager.Snapshot snapshot) {
        boolean permitted = player.hasPermission(MobEditorPermissions.OPEN);
        try {
            player.sendPluginMessage(plugin, MobEditorStatePacket.CHANNEL,
                    new MobEditorStatePacket(
                            permitted, snapshot.success(), snapshot.revisionConflict(),
                            MobEditorPacketIO.boundedUtf8(snapshot.message(), 256),
                            snapshot.mobs(), snapshot.detail(),
                            snapshot.heads(), snapshot.headDetail()).encode());
        } catch (IllegalStateException exception) {
            plugin.getLogger().warning("Mob Editor応答がサイズ上限を超えました");
            sendDenied(player, "一覧データがサイズ上限を超えました");
        }
    }

    private void sendDenied(Player player, String message) {
        player.sendPluginMessage(plugin, MobEditorStatePacket.CHANNEL,
                new MobEditorStatePacket(
                        player.hasPermission(MobEditorPermissions.OPEN),
                        false, false, MobEditorPacketIO.boundedUtf8(message, 256),
                        java.util.List.of(), null,
                        java.util.List.of(), null).encode());
    }

    private void sendIfOnline(Player player, MobEditorManager.Snapshot snapshot) {
        if (player.isOnline()) send(player, snapshot);
    }

    private long beginStateIo(Player player, boolean rateLimited) {
        UUID playerId = player.getUniqueId();
        if (stateIoInFlight.containsKey(playerId)) {
            sendDenied(player, "保存または再読み込み処理中です");
            return 0;
        }
        long now = System.currentTimeMillis();
        if (rateLimited && now - lastPersistentWrites.getOrDefault(playerId, 0L)
                < PERSISTENT_WRITE_COOLDOWN_MILLIS) {
            sendDenied(player, "永続データの更新は1秒以上間隔を空けてください");
            return 0;
        }
        long token = ++stateIoGeneration;
        if (token == 0) token = ++stateIoGeneration;
        stateIoInFlight.put(playerId, token);
        if (rateLimited) lastPersistentWrites.put(playerId, now);
        return token;
    }

    private void finishStateIo(
            Player player,
            long token,
            MobEditorManager.Snapshot snapshot
    ) {
        stateIoInFlight.remove(player.getUniqueId(), token);
        sendIfOnline(player, snapshot);
    }

    private static void requireEnd(DataInputStream input) throws IOException {
        if (input.available() != 0) throw new IOException("Trailing bytes");
    }

    public void clear() {
        lastRequests.clear();
        lastPersistentWrites.clear();
        stateIoInFlight.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastRequests.remove(event.getPlayer().getUniqueId());
    }
}
