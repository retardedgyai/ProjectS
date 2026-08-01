package io.github.gyai.projects.monster.editor;

import io.github.gyai.projects.combat.damage.DamageService;
import io.github.gyai.projects.manager.ItemManager;
import io.github.gyai.projects.manager.MonsterManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class MobEditorManager implements Listener {
    private static final long SESSION_TIMEOUT_MILLIS = 30 * 60 * 1_000L;
    private static final int MOB_PAGE_SIZE = 5;
    private static final int HEAD_PAGE_SIZE = 4;

    private final JavaPlugin plugin;
    private final MonsterManager monsterManager;
    private final MobDefinitionValidator mobValidator;
    private final HeadDefinitionValidator headValidator;
    private final MobDefinitionRepository mobRepository;
    private final HeadDefinitionRepository headRepository;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Object repositoryIoLock = new Object();
    private volatile boolean reloading;

    public MobEditorManager(
            JavaPlugin plugin,
            MonsterManager monsterManager,
            ItemManager itemManager,
            DamageService damageService
    ) {
        this.plugin = plugin;
        this.monsterManager = monsterManager;
        Path data = plugin.getDataFolder().toPath();
        Set<String> itemIds = itemManager.getItems().stream()
                .map(io.github.gyai.projects.item.CustomItem::getId)
                .collect(Collectors.toUnmodifiableSet());
        Map<String, String> itemMaterials = itemManager.getItems().stream()
                .collect(Collectors.toUnmodifiableMap(
                        io.github.gyai.projects.item.CustomItem::getId,
                        item -> item.getMaterial().name()));
        headValidator = new HeadDefinitionValidator(
                itemIds::contains, id -> itemMaterials.getOrDefault(id, ""));
        headRepository = new HeadDefinitionRepository(
                data.resolve("heads"), headValidator,
                message -> plugin.getLogger().warning("[MobEditor] " + message));
        Set<String> livingTypes = java.util.Arrays.stream(EntityType.values())
                .filter(type -> type.getEntityClass() != null
                        && Mob.class.isAssignableFrom(type.getEntityClass())
                        && !Player.class.isAssignableFrom(type.getEntityClass())
                        && !ArmorStand.class.isAssignableFrom(type.getEntityClass()))
                .map(Enum::name).collect(Collectors.toUnmodifiableSet());
        Set<String> materials = java.util.Arrays.stream(Material.values())
                .map(Enum::name).collect(Collectors.toUnmodifiableSet());
        mobValidator = new MobDefinitionValidator(
                livingTypes,
                materials::contains,
                itemIds::contains,
                id -> itemMaterials.getOrDefault(id, ""),
                id -> headRepository.get(id) != null);
        mobRepository = new MobDefinitionRepository(
                data.resolve("mobs"), mobValidator,
                message -> plugin.getLogger().warning("[MobEditor] " + message));
        var headLoad = headRepository.reload();
        var mobLoad = mobRepository.reload();
        if (!headLoad.success()) plugin.getLogger().warning(headLoad.message());
        if (!mobLoad.success()) plugin.getLogger().warning(mobLoad.message());
        MobAppearanceApplier appearanceApplier = new MobAppearanceApplier(
                itemManager, headRepository);
        monsterManager.configureEditor(damageService, appearanceApplier);
        monsterManager.replaceEditorDefinitions(mobRepository.all());
        damageService.setMobStatsResolver(monsterManager::editorStats);
    }

    public Snapshot open(Player player, String message) {
        cleanupExpired();
        sessions.computeIfAbsent(player.getUniqueId(), ignored -> new Session())
                .touch();
        return snapshot(player, true, false, message, "", 0);
    }

    public Snapshot select(Player player, String id) {
        Session session = session(player);
        MobDefinition definition = mobRepository.get(id);
        if (definition == null) {
            return snapshot(player, false, false,
                    "モブ定義が見つかりません", "", 0);
        }
        session.draft = definition;
        session.originalId = definition.id();
        session.baseRevision = definition.revision();
        session.draftMutation++;
        session.targetGeneration++;
        return snapshot(player, true, false, "", "", 0);
    }

    public Snapshot create(Player player, String id) {
        Session session = session(player);
        if (mobRepository.get(id) != null
                || monsterManager.isBuiltInDefinitionId(id)) {
            return snapshot(player, false, false,
                    "同じ内部IDが既存モブまたはMob定義に存在します", "", 0);
        }
        MobDefinition draft = MobDefinition.create(id);
        ValidationResult result = mobValidator.validate(draft);
        if (!result.valid()) {
            return snapshot(player, false, false, result.message(), "", 0);
        }
        session.draft = draft;
        session.originalId = id;
        session.baseRevision = 0;
        session.draftMutation++;
        session.targetGeneration++;
        return snapshot(player, true, false, "新規Draftを作成しました", "", 0);
    }

    public Snapshot update(Player player, MobDefinition draft) {
        Session session = activeSession(player);
        if (session == null || session.originalId == null || session.draft == null) {
            return snapshot(player, false, false,
                    "編集セッションが終了しています。Mobを再選択してください", "", 0);
        }
        if (session.originalId != null && !session.originalId.equals(draft.id())) {
            return snapshot(player, false, false,
                    "作成後に内部IDは変更できません", "", 0);
        }
        if (draft.revision() != session.baseRevision) {
            return snapshot(player, false, true,
                    "別の編集によってモブ定義が更新されています。最新状態を再読み込みしてください",
                    "", 0);
        }
        if (monsterManager.isBuiltInDefinitionId(draft.id())) {
            return snapshot(player, false, false,
                    "既存のハードコードMobと内部IDが競合しています", "", 0);
        }
        session.draft = draft;
        session.draftMutation++;
        ValidationResult result = mobValidator.validate(draft);
        return snapshot(player, result.valid(), false,
                result.message(), "", 0);
    }

    public void saveAsync(Player player, Consumer<Snapshot> callback) {
        Session session = session(player);
        MobDefinition draft = session.draft;
        long mutation = session.draftMutation;
        long expectedRevision = session.baseRevision;
        String savedId = session.originalId;
        long targetGeneration = session.targetGeneration;
        if (draft == null) {
            callback.accept(snapshot(player, false, false,
                    "保存するDraftがありません", "", 0));
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            MobDefinitionRepository.SaveResult result;
            try {
                synchronized (repositoryIoLock) {
                    result = mobRepository.save(draft, expectedRevision);
                }
            } catch (RuntimeException exception) {
                result = MobDefinitionRepository.SaveResult.failure(
                        "保存処理中にエラーが発生しました");
            }
            MobDefinitionRepository.SaveResult completedResult = result;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (completedResult.success()) {
                    if (sessions.get(player.getUniqueId()) == session
                            && session.targetGeneration == targetGeneration
                            && java.util.Objects.equals(session.originalId, savedId)) {
                        session.baseRevision = completedResult.definition().revision();
                        session.draft = session.draftMutation == mutation
                                ? completedResult.definition()
                                : session.draft.withRevision(session.baseRevision);
                        session.originalId = completedResult.definition().id();
                        session.draftMutation++;
                    }
                    monsterManager.replaceEditorDefinitions(mobRepository.all());
                }
                callback.accept(snapshot(player, completedResult.success(),
                        completedResult.revisionConflict(), completedResult.message(), "", 0));
            });
        });
    }

    public Snapshot apply(Player player) {
        Session session = session(player);
        if (session.draft == null) {
            return snapshot(player, false, false,
                    "適用する定義がありません", "", 0);
        }
        MobDefinition saved = mobRepository.get(session.draft.id());
        if (saved == null || saved.revision() != session.draft.revision()) {
            return snapshot(player, false, true,
                    "保存済みの最新revisionを読み込んでください", "", 0);
        }
        var result = monsterManager.applyEditorDefinition(saved);
        boolean complete = result.blockedByEffects() == 0 && result.failed() == 0;
        String message = result.applied() + "体へ適用しました";
        if (!complete) {
            if (result.blockedByEffects() > 0) {
                message += "。" + result.blockedByEffects()
                        + "体はCC/Status中のため型変更を保留しました";
            }
            if (result.failed() > 0) {
                message += "。" + result.failed()
                        + "体は再生成に失敗したため旧個体を維持しました";
            }
        }
        return snapshot(player, complete, false, message, "", 0);
    }

    public Snapshot testSpawn(Player player, boolean cursorLocation) {
        Session session = session(player);
        if (session.draft == null) {
            return snapshot(player, false, false,
                    "テストするDraftがありません", "", 0);
        }
        ValidationResult validation = mobValidator.validate(session.draft);
        if (!validation.valid()) {
            return snapshot(player, false, false, validation.message(), "", 0);
        }
        if (!monsterManager.canSpawnTestMob(player)) {
            return snapshot(player, false, false,
                    "テスト個体は管理者ごとに32体までです", "", 0);
        }
        Location location = testLocation(player, cursorLocation);
        if (monsterManager.spawnEditorMob(session.draft, location, player) == null) {
            return snapshot(player, false, false,
                    "テスト召喚に失敗しました", "", 0);
        }
        return snapshot(player, true, false, "Draftからテスト召喚しました", "", 0);
    }

    public Snapshot controlTests(Player player, MonsterManager.TestControl control) {
        int count = monsterManager.controlTestMobs(player, control);
        return snapshot(player, true, false,
                count + "体のテスト個体を更新しました", "", 0);
    }

    public Snapshot despawnTests(Player player) {
        int count = monsterManager.removeTestMobs(player);
        return snapshot(player, true, false,
                count + "体のテスト個体を削除しました", "", 0);
    }

    public Snapshot selectHead(Player player, String id, String query, int page) {
        Session session = session(player);
        session.selectedHead = headRepository.get(id);
        session.headMutation++;
        return snapshot(player, session.selectedHead != null, false,
                session.selectedHead == null ? "Headが見つかりません" : "",
                query, page);
    }

    public Snapshot headList(Player player, String query, int page) {
        return snapshot(player, true, false, "", query, page);
    }

    public Snapshot mobList(Player player, String query, int page) {
        Session session = session(player);
        session.mobQuery = query;
        session.mobPage = Math.max(0, page);
        return snapshot(player, true, false, "", "", 0);
    }

    public void createHeadAsync(
            Player player,
            HeadDefinition head,
            Consumer<Snapshot> callback
    ) {
        Session editingSession = session(player);
        long headMutation = ++editingSession.headMutation;
        if (headRepository.get(head.id()) != null) {
            callback.accept(snapshot(player, false, false,
                    "同じHead IDが既に存在します", "", 0));
            return;
        }
        ValidationResult validation = headValidator.validate(head);
        if (!validation.valid()) {
            callback.accept(snapshot(player, false, false,
                    validation.message(), "", 0));
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            HeadDefinitionRepository.SaveResult result;
            try {
                synchronized (repositoryIoLock) {
                    result = headRepository.create(head);
                }
            } catch (RuntimeException exception) {
                result = HeadDefinitionRepository.SaveResult.failure(
                        "Head保存処理中にエラーが発生しました");
            }
            HeadDefinitionRepository.SaveResult completedResult = result;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (completedResult.success()
                        && sessions.get(player.getUniqueId()) == editingSession
                        && editingSession.headMutation == headMutation) {
                    editingSession.selectedHead = completedResult.definition();
                }
                callback.accept(snapshot(player, completedResult.success(),
                        completedResult.revisionConflict(), completedResult.message(), "", 0));
            });
        });
    }

    public void updateHeadFavoriteAsync(
            Player player,
            String id,
            long expectedRevision,
            boolean favorite,
            Consumer<Snapshot> callback
    ) {
        Session editingSession = activeSession(player);
        HeadDefinition selected = editingSession == null
                ? null : editingSession.selectedHead;
        HeadDefinition current = headRepository.get(id);
        if (selected == null || current == null || !selected.id().equals(id)) {
            callback.accept(snapshot(player, false, false,
                    "更新するHeadを再選択してください", "", 0));
            return;
        }
        if (selected.revision() != expectedRevision
                || current.revision() != expectedRevision) {
            callback.accept(snapshot(player, false, true,
                    "Head定義のrevisionが競合しました", "", 0));
            return;
        }
        HeadDefinition updated = new HeadDefinition(
                current.schemaVersion(), current.revision(), current.id(),
                current.displayName(), current.sourceType(), current.playerName(),
                current.textureValue(), current.projectsItemId(), current.tags(),
                favorite, current.sourceNote());
        long mutation = ++editingSession.headMutation;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            HeadDefinitionRepository.SaveResult result;
            try {
                synchronized (repositoryIoLock) {
                    result = headRepository.save(updated, expectedRevision);
                }
            } catch (RuntimeException exception) {
                result = HeadDefinitionRepository.SaveResult.failure(
                        "お気に入り保存処理中にエラーが発生しました");
            }
            HeadDefinitionRepository.SaveResult completedResult = result;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (completedResult.success()
                        && sessions.get(player.getUniqueId()) == editingSession
                        && editingSession.headMutation == mutation) {
                    editingSession.selectedHead = completedResult.definition();
                }
                callback.accept(snapshot(player, completedResult.success(),
                        completedResult.revisionConflict(), completedResult.message(), "", 0));
            });
        });
    }

    public void reloadAsync(Player player, Consumer<Snapshot> callback) {
        if (reloading) {
            callback.accept(snapshot(player, false, false,
                    "Mob Editorの再読み込み中です", "", 0));
            return;
        }
        reloading = true;
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                ReloadOutcome outcome;
                List<HeadDefinition> previousHeads = null;
                List<MobDefinition> previousMobs = null;
                try {
                    MobDefinitionRepository.LoadResult heads;
                    MobDefinitionRepository.LoadResult mobs;
                    synchronized (repositoryIoLock) {
                        previousHeads = headRepository.all();
                        previousMobs = mobRepository.all();
                        heads = headRepository.reload();
                        mobs = heads.success() ? mobRepository.reload()
                                : new MobDefinitionRepository.LoadResult(
                                false, mobRepository.all().size(), 0,
                                "Head定義の失敗によりMob再読み込みを中止しました");
                        if (!mobs.success() && heads.success()) {
                            headRepository.replaceState(previousHeads);
                        }
                    }
                    outcome = new ReloadOutcome(heads, mobs);
                } catch (RuntimeException exception) {
                    synchronized (repositoryIoLock) {
                        if (previousHeads != null) {
                            headRepository.replaceState(previousHeads);
                        }
                        if (previousMobs != null) {
                            mobRepository.replaceState(previousMobs);
                        }
                    }
                    plugin.getLogger().warning("Mob Editor再読み込みに失敗しました: "
                            + exception.getClass().getSimpleName());
                    var failure = new MobDefinitionRepository.LoadResult(
                            false, 0, 0, "再読み込み処理中にエラーが発生しました");
                    outcome = new ReloadOutcome(failure, failure);
                }
                ReloadOutcome completed = outcome;
                try {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        boolean success = completed.heads().success()
                                && completed.mobs().success();
                        String message = success ? "再読み込みしました"
                                : completed.heads().message() + " / "
                                + completed.mobs().message();
                        try {
                            monsterManager.replaceEditorDefinitions(mobRepository.all());
                        } catch (RuntimeException exception) {
                            success = false;
                            message = "再読み込み結果の反映に失敗しました";
                            plugin.getLogger().warning(message + ": "
                                    + exception.getClass().getSimpleName());
                        }
                        reloading = false;
                        callback.accept(snapshot(player, success, false,
                                message, "", 0));
                    });
                } catch (RuntimeException exception) {
                    reloading = false;
                    plugin.getLogger().warning("Mob Editor再読み込み完了通知に失敗しました: "
                            + exception.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException exception) {
            reloading = false;
            callback.accept(snapshot(player, false, false,
                    "再読み込み処理を開始できませんでした", "", 0));
        }
    }

    public boolean reloading() {
        return reloading;
    }

    private record ReloadOutcome(
            MobDefinitionRepository.LoadResult heads,
            MobDefinitionRepository.LoadResult mobs
    ) { }

    public void close(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public Snapshot snapshot(
            Player player,
            boolean success,
            boolean conflict,
            String message,
            String headQuery,
            int headPage
    ) {
        Session session = activeSession(player);
        return new Snapshot(
                success, conflict, message,
                mobRepository.search(
                        session == null ? "" : session.mobQuery,
                        session == null ? 0 : session.mobPage, MOB_PAGE_SIZE),
                session == null ? null : session.draft,
                headRepository.search(headQuery, Math.max(0, headPage), HEAD_PAGE_SIZE),
                session == null ? null : session.selectedHead);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        close(event.getPlayer());
        monsterManager.removeTestMobs(event.getPlayer());
    }

    public void clear() {
        sessions.clear();
        monsterManager.removeAllTestMobs();
    }

    private Session session(Player player) {
        Session active = activeSession(player);
        if (active != null) return active;
        Session created = new Session();
        sessions.put(player.getUniqueId(), created);
        created.touch();
        return created;
    }

    private Session activeSession(Player player) {
        UUID playerId = player.getUniqueId();
        Session existing = sessions.get(playerId);
        if (existing != null && System.currentTimeMillis() - existing.lastTouched
                > SESSION_TIMEOUT_MILLIS) {
            sessions.remove(playerId);
            existing = null;
        }
        if (existing != null) existing.touch();
        return existing;
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry ->
                now - entry.getValue().lastTouched > SESSION_TIMEOUT_MILLIS);
    }

    private static Location testLocation(Player player, boolean cursor) {
        if (cursor) {
            var result = player.rayTraceBlocks(32);
            if (result != null && result.getHitPosition() != null) {
                return result.getHitPosition().toLocation(player.getWorld()).add(0, 1, 0);
            }
        }
        var direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < .0001) direction.setZ(1);
        return player.getLocation().clone().add(direction.normalize().multiply(3));
    }

    private static final class Session {
        private MobDefinition draft;
        private String originalId;
        private HeadDefinition selectedHead;
        private String mobQuery = "";
        private int mobPage;
        private long lastTouched;
        private long draftMutation;
        private long headMutation;
        private long baseRevision;
        private long targetGeneration;

        void touch() {
            lastTouched = System.currentTimeMillis();
        }
    }

    public record Snapshot(
            boolean success,
            boolean revisionConflict,
            String message,
            List<MobDefinition> mobs,
            MobDefinition detail,
            List<HeadDefinition> heads,
            HeadDefinition headDetail
    ) { }
}
