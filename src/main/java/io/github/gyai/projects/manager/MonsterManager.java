package io.github.gyai.projects.manager;

import io.github.gyai.projects.combat.skill.CcResistanceProfile;
import io.github.gyai.projects.combat.skill.CrowdControlManager;
import io.github.gyai.projects.combat.skill.HardControlRemovalReason;
import io.github.gyai.projects.combat.skill.HardControlState;
import io.github.gyai.projects.combat.skill.HardControlType;
import io.github.gyai.projects.model.MonsterStats;
import io.github.gyai.projects.monster.CustomMonster;
import io.github.gyai.projects.monster.MonsterData;
import io.github.gyai.projects.monster.MonsterRank;
import io.github.gyai.projects.monster.boss.HarborDevourerBoss;
import io.github.gyai.projects.monster.editor.EditorCustomMonster;
import io.github.gyai.projects.monster.editor.MobAppearanceApplier;
import io.github.gyai.projects.monster.editor.MobDefinition;
import io.github.gyai.projects.monster.editor.MobStatsDefinition;
import io.github.gyai.projects.combat.damage.DamageService;
import io.github.gyai.projects.ability.BossAbilityCaster;
import io.github.gyai.projects.network.MonsterUiMath;
import io.github.gyai.projects.network.MonsterUiPacket;
import io.github.gyai.projects.status.StatusEffectManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MonsterManager {
    public static final String CUSTOM_MONSTER_KEY = "custom_monster_id";
    public enum TestControl {
        PAUSE_AI,
        RESUME_AI,
        INVULNERABLE,
        VULNERABLE,
        HEALTH_25,
        HEALTH_50,
        HEALTH_100,
        REAPPLY_APPEARANCE
    }
    private static final String CONFIG_ROOT =
            "monsters.bosses.harbor-devourer-grohm.";
    private static final int TICK_INTERVAL = 2;
    private static final int SAFE_RESYNC_TICKS = 40;
    private static final int MAX_TEST_MOBS_PER_OWNER = 32;

    private final JavaPlugin plugin;
    private final CrowdControlManager crowdControlManager;
    private final StatusEffectManager statusEffectManager;
    private final PlayerManager playerManager;
    private final TelegraphManager telegraphManager;
    private final NamespacedKey customMonsterKey;
    private final NamespacedKey editorTestKey;
    private final NamespacedKey editorTestOwnerKey;
    private final Map<String, MonsterData> definitions = new HashMap<>();
    private final Map<UUID, CustomMonster> activeMonsters = new HashMap<>();
    private final Map<UUID, ViewerState> viewerStates = new HashMap<>();
    private final Map<String, MobDefinition> editorDefinitions = new HashMap<>();
    private final Map<UUID, Set<UUID>> testMonstersByOwner = new HashMap<>();
    private DamageService damageService;
    private MobAppearanceApplier appearanceApplier;
    private EditorCustomMonster.AssignedAbilityCaster editorAbilityCaster =
            (source, definition, target) -> null;
    private BossAbilityCaster bossAbilityCaster;
    private HarborDevourerBoss.Settings grohmSettings;
    private BukkitTask tickTask;
    private double uiDisplayRange = 48.0;
    private long packetSequence;

    public MonsterManager(
            JavaPlugin plugin,
            CrowdControlManager crowdControlManager,
            StatusEffectManager statusEffectManager,
            PlayerManager playerManager,
            TelegraphManager telegraphManager
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.crowdControlManager = Objects.requireNonNull(
                crowdControlManager, "crowdControlManager");
        this.statusEffectManager = Objects.requireNonNull(
                statusEffectManager, "statusEffectManager");
        this.playerManager = Objects.requireNonNull(
                playerManager, "playerManager");
        this.telegraphManager = Objects.requireNonNull(
                telegraphManager, "telegraphManager");
        this.customMonsterKey =
                new NamespacedKey(plugin, CUSTOM_MONSTER_KEY);
        editorTestKey = new NamespacedKey(plugin, "mob_editor_test");
        editorTestOwnerKey = new NamespacedKey(plugin, "mob_editor_test_owner");
        crowdControlManager.setResistanceResolver(this::resistanceFor);
        statusEffectManager.setResistanceResolver(this::resistanceFor);
        crowdControlManager.setChangeListener(this::onHardControlChanged);
    }

    public void initialize() {
        MonsterStats stats = new MonsterStats(
                getClampedDouble("max-health", 1000.0, 1.0, 2048.0),
                getClampedDouble("attack-damage", 14.0, 0.0, 2048.0),
                getClampedDouble("movement-speed", 0.26, 0.01, 1.0),
                getClampedDouble("knockback-resistance", 1.0, 0.0, 1.0),
                getClampedDouble("follow-range", 32.0, 1.0, 128.0),
                getClampedDouble("scale", 1.35, 0.1, 4.0));
        int level = getClampedInt("level", 30, 1, 999);
        MonsterRank rank = getRank("rank", MonsterRank.BOSS);
        CcResistanceProfile resistanceProfile = new CcResistanceProfile(
                getImmunities("cc-resistance.immune-types"),
                getClampedDouble(
                        "cc-resistance.hard-duration-multiplier",
                        1.0, 0.0, 10.0),
                getClampedDouble(
                        "cc-resistance.status-duration-multiplier",
                        1.0, 0.0, 10.0));
        register(new MonsterData(
                HarborDevourerBoss.MONSTER_ID,
                "港喰らいの巨獣 グローム",
                EntityType.RAVAGER,
                stats,
                level,
                rank,
                resistanceProfile));

        double resetSeconds = getClampedDouble(
                "reset-after-seconds", 10.0, 1.0, 600.0);
        grohmSettings = new HarborDevourerBoss.Settings(
                getClampedDouble("leash-range", 36.0, 4.0, 256.0),
                getClampedDouble("boss-bar-range", 48.0, 4.0, 256.0),
                Math.max(1, (int) Math.round(resetSeconds * 20.0)),
                getClampedDouble("phase-two-health-ratio", 0.5, 0.05, 1.0),
                getClampedDouble(
                        "breakwater-slam.cooldown-seconds", 7.0, 0.1, 600.0),
                getClampedInt("breakwater-slam.warning-ticks", 24, 1, 200),
                getClampedDouble("breakwater-slam.radius", 5.0, 0.5, 64.0),
                getClampedDouble("breakwater-slam.damage", 18.0, 0.0, 2048.0),
                getClampedInt(
                        "breakwater-slam.phase-two-shockwave-warning-ticks",
                        14, 4, 100),
                getClampedDouble(
                        "hullbreaker-charge.cooldown-seconds", 12.0, 0.1, 600.0),
                getClampedInt("hullbreaker-charge.warning-ticks", 20, 1, 200),
                getClampedDouble("hullbreaker-charge.distance", 14.0, 1.0, 64.0),
                getClampedDouble("hullbreaker-charge.damage", 24.0, 0.0, 2048.0),
                TICK_INTERVAL);
        uiDisplayRange = getClampedGlobalDouble(
                "monsters.ui.display-range", 48.0, 8.0, 128.0);
    }

    public void register(MonsterData data) {
        Objects.requireNonNull(data, "data");
        MonsterData previous =
                definitions.putIfAbsent(data.id(), data);
        if (previous != null) {
            throw new IllegalArgumentException(
                    "Monster id is already registered: " + data.id());
        }
    }

    public void configureEditor(
            DamageService damageService,
            MobAppearanceApplier appearanceApplier
    ) {
        this.damageService = Objects.requireNonNull(damageService, "damageService");
        this.appearanceApplier = Objects.requireNonNull(
                appearanceApplier, "appearanceApplier");
    }

    public void configureEditorAbilityCaster(
            EditorCustomMonster.AssignedAbilityCaster abilityCaster
    ) {
        editorAbilityCaster = Objects.requireNonNull(
                abilityCaster, "abilityCaster");
    }

    public void configureBossAbilityCaster(BossAbilityCaster abilityCaster) {
        bossAbilityCaster = Objects.requireNonNull(abilityCaster, "abilityCaster");
    }

    public void replaceEditorDefinitions(List<MobDefinition> updated) {
        editorDefinitions.clear();
        for (MobDefinition definition : updated) {
            if (definitions.containsKey(definition.id())) {
                plugin.getLogger().warning(
                        "既存モブとMob Editor定義のIDが衝突しました: "
                                + definition.id());
                continue;
            }
            editorDefinitions.put(definition.id(), definition);
        }
    }

    public boolean isBuiltInDefinitionId(String id) {
        return definitions.containsKey(id);
    }

    public EditorCustomMonster spawnEditorMob(
            MobDefinition definition,
            Location location,
            Player testOwner
    ) {
        return spawnEditorMob(definition, location, location, testOwner, false);
    }

    private EditorCustomMonster spawnEditorMob(
            MobDefinition definition,
            Location location,
            Location homeLocation,
            Player testOwner,
            boolean bypassTestLimit
    ) {
        if (damageService == null || appearanceApplier == null
                || location == null || location.getWorld() == null) return null;
        if (!bypassTestLimit && testOwner != null && testMonstersByOwner.getOrDefault(
                testOwner.getUniqueId(), Set.of()).size() >= MAX_TEST_MOBS_PER_OWNER) {
            return null;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(definition.entityType());
        } catch (IllegalArgumentException exception) {
            return null;
        }
        Entity spawned = location.getWorld().spawnEntity(location, type);
        if (!(spawned instanceof LivingEntity living)) {
            spawned.remove();
            return null;
        }
        living.getPersistentDataContainer().set(
                customMonsterKey, PersistentDataType.STRING, definition.id());
        living.setPersistent(false);
        if (testOwner != null) {
            living.getPersistentDataContainer().set(
                    editorTestKey, PersistentDataType.BOOLEAN, true);
            living.getPersistentDataContainer().set(
                    editorTestOwnerKey, PersistentDataType.STRING,
                    testOwner.getUniqueId().toString());
        }
        BossBar bossBar = plugin.getServer().createBossBar(
                definition.displayName(), BarColor.RED, BarStyle.SOLID);
        EditorCustomMonster monster;
        try {
            monster = new EditorCustomMonster(
                    plugin, definition, living, homeLocation, bossBar,
                    crowdControlManager, statusEffectManager,
                    damageService, appearanceApplier, editorAbilityCaster);
            monster.initializeEntity();
        } catch (RuntimeException exception) {
            bossBar.removeAll();
            living.remove();
            plugin.getLogger().warning("Editor Mob初期化に失敗しました: "
                    + exception.getClass().getSimpleName());
            return null;
        }
        activeMonsters.put(living.getUniqueId(), monster);
        if (testOwner != null) {
            testMonstersByOwner.computeIfAbsent(
                    testOwner.getUniqueId(), ignored -> new HashSet<>())
                    .add(living.getUniqueId());
        }
        return monster;
    }

    public DefinitionApplyResult applyEditorDefinition(MobDefinition definition) {
        editorDefinitions.put(definition.id(), definition);
        int applied = 0;
        int blocked = 0;
        int failed = 0;
        for (EditorCustomMonster monster : activeMonsters.values().stream()
                .filter(EditorCustomMonster.class::isInstance)
                .map(EditorCustomMonster.class::cast)
                .filter(value -> value.definition().id().equals(definition.id()))
                .toList()) {
            if (monster.applyDefinition(definition)) {
                applied++;
                continue;
            }
            LivingEntity previous = monster.getEntity();
            if (crowdControlManager.isControlled(previous)
                    || !statusEffectManager.snapshots(
                    previous, plugin.getServer().getCurrentTick()).isEmpty()) {
                blocked++;
                continue;
            }
            Location location = previous.getLocation().clone();
            Location homeLocation = monster.homeLocation();
            Player owner = testOwner(previous);
            var oldMaximum = previous.getAttribute(Attribute.MAX_HEALTH);
            double healthRatio = oldMaximum == null || oldMaximum.getValue() <= 0
                    ? 1 : Math.clamp(previous.getHealth() / oldMaximum.getValue(), 0, 1);
            LivingEntity target = previous instanceof org.bukkit.entity.Mob mob
                    ? mob.getTarget() : null;
            boolean aiPaused = monster.aiPaused();
            boolean invulnerable = previous.isInvulnerable();
            EditorCustomMonster replacement = spawnEditorMob(
                    definition, location, homeLocation, owner, true);
            if (replacement != null) {
                remove(monster.getEntityId());
                setHealthFraction(replacement.getEntity(), healthRatio);
                replacement.getEntity().setInvulnerable(invulnerable);
                replacement.setAiPaused(aiPaused);
                replacement.restoreTarget(target);
                applied++;
            } else {
                failed++;
            }
        }
        return new DefinitionApplyResult(applied, blocked, failed);
    }

    public record DefinitionApplyResult(
            int applied,
            int blockedByEffects,
            int failed
    ) { }

    public MobStatsDefinition editorStats(LivingEntity entity) {
        CustomMonster monster = activeMonsters.get(entity.getUniqueId());
        return monster instanceof EditorCustomMonster editor
                ? editor.definition().stats() : null;
    }

    public MobStatsDefinition abilityStats(LivingEntity entity) {
        CustomMonster monster = activeMonsters.get(entity.getUniqueId());
        if (monster instanceof EditorCustomMonster editor) {
            return editor.definition().stats();
        }
        if (monster == null) return null;
        MonsterStats stats = monster.getData().stats();
        return new MobStatsDefinition(
                stats.maxHealth(),
                stats.attackDamage(),
                0.0,
                0.0,
                0.0,
                stats.movementSpeed(),
                1.0,
                0.0,
                1.0,
                0.0);
    }

    /** Returns the definition applied to this exact live Editor Mob instance. */
    public MobDefinition editorDefinition(LivingEntity entity) {
        CustomMonster monster = activeMonsters.get(entity.getUniqueId());
        return monster instanceof EditorCustomMonster editor
                ? editor.definition() : null;
    }

    public boolean isEditorMonster(Entity entity) {
        return activeMonsters.get(entity.getUniqueId()) instanceof EditorCustomMonster;
    }

    public boolean isApplyingEditorDamage(
            LivingEntity attacker,
            LivingEntity target
    ) {
        return damageService != null && damageService.isApplying(attacker, target);
    }

    public int removeTestMobs(Player owner) {
        Set<UUID> ids = testMonstersByOwner.remove(owner.getUniqueId());
        if (ids == null) return 0;
        int removedCount = 0;
        for (UUID id : Set.copyOf(ids)) {
            if (remove(id)) removedCount++;
        }
        return removedCount;
    }

    public boolean canSpawnTestMob(Player owner) {
        return testMonstersByOwner.getOrDefault(
                owner.getUniqueId(), Set.of()).size() < MAX_TEST_MOBS_PER_OWNER;
    }

    public int removeAllTestMobs() {
        int count = 0;
        for (UUID ownerId : Set.copyOf(testMonstersByOwner.keySet())) {
            Set<UUID> ids = testMonstersByOwner.remove(ownerId);
            if (ids == null) continue;
            for (UUID id : ids) if (remove(id)) count++;
        }
        return count;
    }

    public int controlTestMobs(Player owner, TestControl control) {
        Set<UUID> ids = testMonstersByOwner.get(owner.getUniqueId());
        if (ids == null) return 0;
        int changed = 0;
        for (UUID id : Set.copyOf(ids)) {
            CustomMonster value = activeMonsters.get(id);
            if (!(value instanceof EditorCustomMonster monster)
                    || !monster.isValid()) continue;
            switch (control) {
                case PAUSE_AI -> monster.setAiPaused(true);
                case RESUME_AI -> monster.setAiPaused(false);
                case INVULNERABLE -> monster.getEntity().setInvulnerable(true);
                case VULNERABLE -> monster.getEntity().setInvulnerable(false);
                case HEALTH_25 -> setHealthFraction(monster.getEntity(), .25);
                case HEALTH_50 -> setHealthFraction(monster.getEntity(), .50);
                case HEALTH_100 -> setHealthFraction(monster.getEntity(), 1);
                case REAPPLY_APPEARANCE ->
                        monster.applyDefinition(monster.definition());
            }
            changed++;
        }
        return changed;
    }

    public void start() {
        if (tickTask != null) {
            return;
        }
        tickTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        sendClearPackets();
        removeAll();
        crowdControlManager.clear(HardControlRemovalReason.PLUGIN_STOP);
        statusEffectManager.clear();
        viewerStates.clear();
    }

    public CustomMonster spawnHarborDevourer(Location location) {
        Objects.requireNonNull(location, "location");
        if (hasActiveHarborDevourer()
                || grohmSettings == null
                || damageService == null
                || bossAbilityCaster == null) {
            return null;
        }
        MonsterData data =
                definitions.get(HarborDevourerBoss.MONSTER_ID);
        if (data == null || data.entityType() != EntityType.RAVAGER) {
            plugin.getLogger().warning(
                    "グロームのモブ定義が初期化されていません。");
            return null;
        }

        Ravager ravager = location.getWorld().spawn(
                location,
                Ravager.class,
                spawned -> configureEntity(spawned, data));
        BossBar bossBar = plugin.getServer().createBossBar(
                data.displayName(),
                BarColor.RED,
                BarStyle.SEGMENTED_10,
                new BarFlag[0]);
        HarborDevourerBoss boss = new HarborDevourerBoss(
                plugin,
                data,
                ravager,
                location,
                bossBar,
                grohmSettings,
                crowdControlManager,
                statusEffectManager,
                telegraphManager,
                damageService,
                bossAbilityCaster);
        activeMonsters.put(ravager.getUniqueId(), boss);
        return boss;
    }

    public boolean hasActiveHarborDevourer() {
        return activeMonsters.values().stream()
                .anyMatch(monster -> monster.isValid()
                        && monster.getData().id().equals(
                                HarborDevourerBoss.MONSTER_ID));
    }

    public boolean removeHarborDevourer() {
        Optional<CustomMonster> active =
                activeMonsters.values().stream()
                        .filter(monster -> monster.getData().id().equals(
                                HarborDevourerBoss.MONSTER_ID))
                        .findFirst();
        return active.isPresent()
                && remove(active.get().getEntityId());
    }

    public boolean resetHarborDevourer() {
        return activeMonsters.values().stream()
                .filter(HarborDevourerBoss.class::isInstance)
                .map(HarborDevourerBoss.class::cast)
                .filter(HarborDevourerBoss::isValid)
                .findFirst()
                .map(HarborDevourerBoss::reset)
                .orElse(false);
    }

    public CustomMonster get(UUID entityId) {
        return activeMonsters.get(entityId);
    }

    public CustomMonster findTargetedCustomMonster(
            Player player,
            double range
    ) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                0.4,
                entity -> activeMonsters.containsKey(
                        entity.getUniqueId()));
        if (result == null || result.getHitEntity() == null) {
            return null;
        }
        return activeMonsters.get(
                result.getHitEntity().getUniqueId());
    }

    public boolean isCustomMonster(Entity entity) {
        if (activeMonsters.containsKey(entity.getUniqueId())) return true;
        String id = entity.getPersistentDataContainer().get(
                customMonsterKey, PersistentDataType.STRING);
        return id != null && (definitions.containsKey(id)
                || editorDefinitions.containsKey(id));
    }

    public String getCustomMonsterId(Entity entity) {
        return entity.getPersistentDataContainer().get(
                customMonsterKey, PersistentDataType.STRING);
    }

    public boolean remove(UUID entityId) {
        CustomMonster monster =
                activeMonsters.remove(entityId);
        if (monster == null) {
            return false;
        }
        untrackTestMob(entityId);
        monster.remove();
        return true;
    }

    public void forget(UUID entityId) {
        CustomMonster monster =
                activeMonsters.remove(entityId);
        untrackTestMob(entityId);
        if (monster != null) {
            monster.clearManagedEffects(
                    HardControlRemovalReason.MONSTER_REMOVED);
        }
    }

    public int removeAll() {
        int count = activeMonsters.size();
        for (CustomMonster monster
                : activeMonsters.values().toArray(
                        CustomMonster[]::new)) {
            monster.remove();
        }
        activeMonsters.clear();
        testMonstersByOwner.clear();
        return count;
    }

    public Location findSafeSpawnLocation(Player player) {
        Vector direction =
                player.getLocation().getDirection().setY(0.0);
        if (direction.lengthSquared() < 0.0001) {
            direction.setZ(1.0);
        }
        direction.normalize();
        for (double distance : new double[]{5.0, 4.0, 6.0}) {
            Location candidate = player.getLocation().clone()
                    .add(direction.clone().multiply(distance));
            candidate.setX(Math.floor(candidate.getX()) + 0.5);
            candidate.setY(Math.floor(candidate.getY()));
            candidate.setZ(Math.floor(candidate.getZ()) + 0.5);
            candidate.setYaw(
                    player.getLocation().getYaw() + 180.0f);
            candidate.setPitch(0.0f);
            if (isSafeSpawnVolume(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void configureEntity(
            Ravager ravager,
            MonsterData data
    ) {
        MonsterStats stats = data.stats();
        ravager.customName(null);
        ravager.setCustomNameVisible(false);
        ravager.setPersistent(false);
        ravager.setRemoveWhenFarAway(false);
        ravager.setCanPickupItems(false);
        ravager.setFireTicks(0);
        ravager.getPersistentDataContainer().set(
                customMonsterKey,
                PersistentDataType.STRING,
                data.id());

        setAttribute(ravager, Attribute.MAX_HEALTH, stats.maxHealth());
        setAttribute(ravager, Attribute.ATTACK_DAMAGE, stats.attackDamage());
        setAttribute(ravager, Attribute.MOVEMENT_SPEED, stats.movementSpeed());
        setAttribute(
                ravager,
                Attribute.KNOCKBACK_RESISTANCE,
                stats.knockbackResistance());
        setAttribute(ravager, Attribute.FOLLOW_RANGE, stats.followRange());
        setAttribute(ravager, Attribute.SCALE, stats.scale());
        ravager.setHealth(stats.maxHealth());
    }

    private void setAttribute(
            Ravager ravager,
            Attribute attribute,
            double value
    ) {
        AttributeInstance instance =
                ravager.getAttribute(attribute);
        if (instance == null) {
            plugin.getLogger().warning(
                    "エンティティ " + ravager.getType()
                            + " に属性 " + attribute + " がありません。");
            return;
        }
        instance.setBaseValue(value);
    }

    private void tick() {
        long currentTick =
                plugin.getServer().getCurrentTick();
        crowdControlManager.tick(currentTick);
        statusEffectManager.tick(currentTick);
        for (Map.Entry<UUID, CustomMonster> entry
                : Map.copyOf(activeMonsters).entrySet()) {
            CustomMonster monster = entry.getValue();
            if (!monster.isValid()) {
                monster.remove();
                activeMonsters.remove(entry.getKey(), monster);
                untrackTestMob(entry.getKey());
                continue;
            }
            try {
                monster.tick();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("CustomMonster tickに失敗したため削除します: "
                        + monster.getEntityId() + " ("
                        + exception.getClass().getSimpleName() + ")");
                monster.remove();
                activeMonsters.remove(entry.getKey(), monster);
                untrackTestMob(entry.getKey());
            }
        }
        syncMonsterUi(currentTick);
    }

    private void untrackTestMob(UUID entityId) {
        testMonstersByOwner.values().forEach(ids -> ids.remove(entityId));
        testMonstersByOwner.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private void syncMonsterUi(long currentTick) {
        Set<UUID> online = new HashSet<>();
        for (Player player
                : plugin.getServer().getOnlinePlayers()) {
            online.add(player.getUniqueId());
            if (!player.getListeningPluginChannels()
                    .contains(MonsterUiPacket.CHANNEL)) {
                viewerStates.remove(player.getUniqueId());
                continue;
            }
            syncViewer(player, currentTick);
        }
        viewerStates.keySet().removeIf(
                viewerId -> !online.contains(viewerId));
    }

    private void syncViewer(Player viewer, long currentTick) {
        ViewerState state = viewerStates.computeIfAbsent(
                viewer.getUniqueId(), ignored -> new ViewerState());
        boolean fullResync =
                currentTick - state.lastFullSyncTick
                        >= SAFE_RESYNC_TICKS;
        double rangeSquared =
                uiDisplayRange * uiDisplayRange;
        Map<UUID, SentState> visible = new HashMap<>();
        List<MonsterUiPacket.Entry> upserts =
                new ArrayList<>();
        for (CustomMonster monster
                : activeMonsters.values()) {
            LivingEntity entity = monster.getEntity();
            if (!monster.isValid()
                    || !entity.getWorld().equals(viewer.getWorld())
                    || entity.getLocation().distanceSquared(
                            viewer.getLocation()) > rangeSquared) {
                continue;
            }
            int viewerLevel = playerManager
                    .getPlayerData(viewer)
                    .getCombatLevel();
            int fingerprint = 31
                    * fingerprint(monster, currentTick)
                    + MonsterUiMath.threatBand(
                            monster.getData().level(),
                            viewerLevel).ordinal();
            SentState sent = new SentState(
                    entity.getEntityId(), fingerprint);
            visible.put(entity.getUniqueId(), sent);
            SentState previous =
                    state.sent.get(entity.getUniqueId());
            if (fullResync || !sent.equals(previous)) {
                upserts.add(createSnapshot(
                        viewer, monster, currentTick));
            }
        }

        List<MonsterUiPacket.Entry> removals =
                new ArrayList<>();
        for (Map.Entry<UUID, SentState> previous
                : state.sent.entrySet()) {
            if (!visible.containsKey(previous.getKey())) {
                removals.add(MonsterUiPacket.Entry.remove(
                        previous.getValue().networkEntityId,
                        previous.getKey()));
            }
        }
        sendBatches(
                viewer,
                MonsterUiPacket.Operation.UPSERT,
                currentTick,
                upserts);
        sendBatches(
                viewer,
                MonsterUiPacket.Operation.REMOVE,
                currentTick,
                removals);
        state.sent.clear();
        state.sent.putAll(visible);
        if (fullResync) {
            state.lastFullSyncTick = currentTick;
        }
    }

    private MonsterUiPacket.Entry createSnapshot(
            Player viewer,
            CustomMonster monster,
            long currentTick
    ) {
        LivingEntity entity = monster.getEntity();
        MonsterData data = monster.getData();
        double maximumHealth =
                Math.max(1.0, data.stats().maxHealth());
        double currentHealth = MonsterUiMath.clampHealth(
                entity.getHealth(), maximumHealth);
        CrowdControlManager.Snapshot hardControl =
                crowdControlManager.snapshot(entity, currentTick);
        MonsterUiPacket.HardControl packetControl =
                hardControl == null
                        ? null
                        : new MonsterUiPacket.HardControl(
                                hardControl.type(),
                                hardControl.totalTicks(),
                                hardControl.remainingTicks());
        List<MonsterUiPacket.Status> statuses =
                statusEffectManager.snapshots(entity, currentTick)
                        .stream()
                        .limit(MonsterUiPacket.MAX_STATUS_EFFECTS)
                        .map(status -> new MonsterUiPacket.Status(
                                status.type(),
                                status.strength(),
                                status.totalTicks(),
                                status.remainingTicks()))
                        .toList();
        int playerLevel = playerManager
                .getPlayerData(viewer)
                .getCombatLevel();
        return new MonsterUiPacket.Entry(
                entity.getEntityId(),
                entity.getUniqueId(),
                data.id(),
                data.displayName(),
                data.rank(),
                data.level(),
                MonsterUiMath.threatBand(
                        data.level(), playerLevel),
                currentHealth,
                maximumHealth,
                uiDisplayRange,
                packetControl,
                statuses);
    }

    private int fingerprint(
            CustomMonster monster,
            long currentTick
    ) {
        LivingEntity entity = monster.getEntity();
        CrowdControlManager.Snapshot hard =
                crowdControlManager.snapshot(entity, currentTick);
        List<StatusEffectManager.Snapshot> statuses =
                statusEffectManager.snapshots(entity, currentTick);
        int result = Objects.hash(
                monster.getData().id(),
                monster.getData().displayName(),
                monster.getData().rank(),
                monster.getData().level(),
                Double.doubleToLongBits(entity.getHealth()),
                hard == null ? null : hard.type(),
                hard == null ? 0L : hard.endTick(),
                hard == null ? 0 : hard.totalTicks());
        for (StatusEffectManager.Snapshot status : statuses) {
            result = 31 * result + Objects.hash(
                    status.type(),
                    status.endTick(),
                    status.totalTicks(),
                    Double.doubleToLongBits(status.strength()));
        }
        return result;
    }

    private void sendBatches(
            Player player,
            MonsterUiPacket.Operation operation,
            long currentTick,
            List<MonsterUiPacket.Entry> entries
    ) {
        for (int start = 0;
             start < entries.size();
             start += MonsterUiPacket.MAX_MONSTERS_PER_PACKET) {
            int end = Math.min(
                    entries.size(),
                    start + MonsterUiPacket.MAX_MONSTERS_PER_PACKET);
            MonsterUiPacket packet = new MonsterUiPacket(
                    operation,
                    ++packetSequence,
                    currentTick,
                    entries.subList(start, end));
            player.sendPluginMessage(
                    plugin,
                    MonsterUiPacket.CHANNEL,
                    packet.encode());
        }
    }

    private void sendClearPackets() {
        long currentTick =
                plugin.getServer().getCurrentTick();
        for (Player player
                : plugin.getServer().getOnlinePlayers()) {
            if (player.getListeningPluginChannels()
                    .contains(MonsterUiPacket.CHANNEL)) {
                player.sendPluginMessage(
                        plugin,
                        MonsterUiPacket.CHANNEL,
                        MonsterUiPacket.clear(
                                ++packetSequence,
                                currentTick).encode());
            }
        }
    }

    private void onHardControlChanged(
            LivingEntity entity,
            HardControlState previous,
            HardControlState current
    ) {
        CustomMonster monster =
                activeMonsters.get(entity.getUniqueId());
        if (monster != null) {
            monster.handleHardControlChanged(previous, current);
        }
    }

    private CcResistanceProfile resistanceFor(
            LivingEntity entity
    ) {
        CustomMonster monster =
                activeMonsters.get(entity.getUniqueId());
        return monster == null
                ? CcResistanceProfile.DEFAULT
                : monster.getData().resistanceProfile();
    }

    private boolean isSafeSpawnVolume(Location location) {
        if (location.getY()
                <= location.getWorld().getMinHeight() + 1
                || location.getY() + 4
                >= location.getWorld().getMaxHeight()) {
            return false;
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block ground =
                        location.clone().add(x, -1, z).getBlock();
                if (!ground.getType().isSolid()
                        || ground.getType()
                        == Material.MAGMA_BLOCK) {
                    return false;
                }
                for (int y = 0; y <= 3; y++) {
                    if (!location.clone()
                            .add(x, y, z)
                            .getBlock()
                            .isPassable()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private Set<HardControlType> getImmunities(String suffix) {
        String path = CONFIG_ROOT + suffix;
        Set<HardControlType> result =
                EnumSet.noneOf(HardControlType.class);
        for (String configured
                : plugin.getConfig().getStringList(path)) {
            try {
                result.add(HardControlType.valueOf(
                        configured.trim().toUpperCase(
                                java.util.Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning(
                        "不正なCC耐性タイプ " + path + "="
                                + configured + " を無視しました。");
            }
        }
        return result;
    }

    private MonsterRank getRank(
            String suffix,
            MonsterRank defaultValue
    ) {
        String path = CONFIG_ROOT + suffix;
        String configured =
                plugin.getConfig().getString(path, defaultValue.name());
        if (configured == null) {
            warnInvalid(path, "null", defaultValue);
            return defaultValue;
        }
        try {
            return MonsterRank.valueOf(
                    configured.trim().toUpperCase(
                            java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            warnInvalid(path, configured, defaultValue);
            return defaultValue;
        }
    }

    private double getClampedDouble(
            String suffix,
            double defaultValue,
            double minimum,
            double maximum
    ) {
        return getClampedGlobalDouble(
                CONFIG_ROOT + suffix,
                defaultValue,
                minimum,
                maximum);
    }

    private double getClampedGlobalDouble(
            String path,
            double defaultValue,
            double minimum,
            double maximum
    ) {
        double configured =
                plugin.getConfig().getDouble(path, defaultValue);
        if (!Double.isFinite(configured)) {
            warnInvalid(path, configured, defaultValue);
            return defaultValue;
        }
        double clamped =
                Math.clamp(configured, minimum, maximum);
        if (clamped != configured) {
            warnInvalid(path, configured, clamped);
        }
        return clamped;
    }

    private int getClampedInt(
            String suffix,
            int defaultValue,
            int minimum,
            int maximum
    ) {
        String path = CONFIG_ROOT + suffix;
        int configured =
                plugin.getConfig().getInt(path, defaultValue);
        int clamped =
                Math.clamp(configured, minimum, maximum);
        if (clamped != configured) {
            warnInvalid(path, configured, clamped);
        }
        return clamped;
    }

    private void warnInvalid(
            String path,
            Object configured,
            Object replacement
    ) {
        plugin.getLogger().warning(
                "不正な設定値 " + path + "=" + configured
                        + " を " + replacement + " に補正しました。");
    }

    private static final class ViewerState {
        private final Map<UUID, SentState> sent =
                new HashMap<>();
        private long lastFullSyncTick = Long.MIN_VALUE / 2;
    }

    private record SentState(
            int networkEntityId,
            int fingerprint
    ) {
    }

    private Player testOwner(LivingEntity entity) {
        String value = entity.getPersistentDataContainer().get(
                editorTestOwnerKey, PersistentDataType.STRING);
        if (value == null) return null;
        try {
            return plugin.getServer().getPlayer(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static void setHealthFraction(LivingEntity entity, double fraction) {
        AttributeInstance maximum = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maximum != null) {
            entity.setHealth(Math.max(.001, maximum.getValue() * fraction));
        }
    }
}
