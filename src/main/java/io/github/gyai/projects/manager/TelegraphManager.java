package io.github.gyai.projects.manager;

import io.github.gyai.projects.combat.telegraph.TelegraphInstance;
import io.github.gyai.projects.combat.telegraph.TelegraphOperation;
import io.github.gyai.projects.combat.telegraph.TelegraphRequest;
import io.github.gyai.projects.combat.telegraph.TelegraphTimeline;
import io.github.gyai.projects.combat.telegraph.TelegraphViewerPolicy;
import io.github.gyai.projects.combat.telegraph.TelegraphCapacityPolicy;
import io.github.gyai.projects.network.TelegraphPacket;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class TelegraphManager
        implements Listener, PluginMessageListener {
    private static final Particle.DustOptions DAMAGE_DUST =
            new Particle.DustOptions(
                    Color.fromRGB(205, 67, 32), 1.0f);
    private static final Particle.DustOptions DEBUFF_DUST =
            new Particle.DustOptions(
                    Color.fromRGB(145, 55, 190), 1.0f);
    private static final Particle.DustOptions POISON_DUST =
            new Particle.DustOptions(
                    Color.fromRGB(65, 175, 65), 1.0f);
    private static final Particle.DustOptions SAFE_DUST =
            new Particle.DustOptions(
                    Color.fromRGB(50, 155, 220), 1.0f);
    private static final Particle.DustOptions OPPORTUNITY_DUST =
            new Particle.DustOptions(
                    Color.fromRGB(245, 205, 85), 1.0f);
    private static final int VIEWER_SYNC_INTERVAL = 2;
    private static final int FALLBACK_INTERVAL = 4;
    private static final int CANCEL_VISIBLE_TICKS = 4;

    private final JavaPlugin plugin;
    private final Settings settings;
    private final TelegraphCapacityPolicy capacityPolicy;
    private final Map<UUID, TelegraphInstance> active =
            new LinkedHashMap<>();
    private final Map<UUID, ViewerState> viewerStates =
            new HashMap<>();
    private final Set<UUID> helloConfirmedViewers =
            new HashSet<>();
    private BukkitTask tickTask;
    private long packetSequence;

    public TelegraphManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(
                plugin, "plugin");
        settings = Settings.load(plugin);
        capacityPolicy = new TelegraphCapacityPolicy(
                settings.maximumActiveGlobal(),
                settings.maximumActivePerSource());
    }

    public void start() {
        if (tickTask != null) {
            return;
        }
        tickTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        long currentTick =
                plugin.getServer().getCurrentTick();
        for (TelegraphInstance instance
                : List.copyOf(active.values())) {
            cancel(
                    instance.id(),
                    TelegraphInstance.CancellationReason
                            .PLUGIN_STOP);
        }
        sendClearToAll(currentTick);
        active.clear();
        viewerStates.clear();
        helloConfirmedViewers.clear();
    }

    public UUID create(
            LivingEntity source,
            TelegraphRequest request
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(request, "request");
        if (!source.isValid()
                || source.isDead()
                || !source.getWorld().getUID().equals(
                request.worldId())
                || !source.getWorld().getKey().toString()
                .equals(request.dimension())) {
            throw new IllegalArgumentException(
                    "Invalid telegraph source");
        }
        UUID id = UUID.randomUUID();
        TelegraphInstance instance =
                new TelegraphInstance(
                        id,
                        source.getUniqueId(),
                        source.getEntityId(),
                        request);
        enforceCapacity(source.getUniqueId());
        active.put(id, instance);
        if (settings.enabled()) {
            broadcast(
                    TelegraphOperation.CREATE,
                    instance,
                    request.startTick(),
                    false);
        }
        return id;
    }

    private void enforceCapacity(UUID sourceId) {
        List<TelegraphCapacityPolicy.ActiveEntry> entries = active.values().stream()
                .map(instance -> new TelegraphCapacityPolicy.ActiveEntry(
                        instance.id(), instance.sourceId()))
                .toList();
        for (UUID id : capacityPolicy.evictionsBeforeInsert(entries, sourceId)) {
            TelegraphInstance instance = active.get(id);
            if (instance == null) continue;
            cancel(id, TelegraphInstance.CancellationReason.CAPACITY_LIMIT);
            remove(instance, plugin.getServer().getCurrentTick());
        }
    }

    public Optional<TelegraphInstance> get(UUID id) {
        return Optional.ofNullable(active.get(id));
    }

    public Location center(UUID id) {
        TelegraphInstance instance = active.get(id);
        if (instance == null) {
            return null;
        }
        World world = plugin.getServer().getWorld(
                instance.request().worldId());
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                instance.centerX(),
                instance.centerY(),
                instance.centerZ());
    }

    public boolean contains(
            UUID id,
            Location location
    ) {
        TelegraphInstance instance = active.get(id);
        return instance != null
                && location != null
                && location.getWorld() != null
                && location.getWorld().getUID().equals(
                instance.request().worldId())
                && instance.contains(
                location.getX(),
                location.getY(),
                location.getZ());
    }

    public boolean detonate(UUID id) {
        TelegraphInstance instance = active.get(id);
        if (instance == null || !instance.detonate()) {
            return false;
        }
        if (settings.enabled()) {
            broadcast(
                    TelegraphOperation.DETONATE,
                    instance,
                    plugin.getServer().getCurrentTick(),
                    true);
        }
        if (settings.enabled()
                && settings.fallbackServerParticles()) {
            renderDetonationFallback(instance);
        }
        return true;
    }

    public boolean cancel(
            UUID id,
            TelegraphInstance.CancellationReason reason
    ) {
        TelegraphInstance instance = active.get(id);
        long currentTick =
                plugin.getServer().getCurrentTick();
        if (instance == null
                || !instance.cancel(reason, currentTick)) {
            return false;
        }
        if (settings.enabled()) {
            broadcast(
                    TelegraphOperation.CANCEL,
                    instance,
                    currentTick,
                    true);
        }
        return true;
    }

    public boolean removeNow(UUID id) {
        TelegraphInstance instance = active.get(id);
        if (instance == null) {
            return false;
        }
        remove(
                instance,
                plugin.getServer().getCurrentTick());
        return true;
    }

    public void cancelSource(
            UUID sourceId,
            TelegraphInstance.CancellationReason reason
    ) {
        for (TelegraphInstance instance
                : List.copyOf(active.values())) {
            if (instance.sourceId().equals(sourceId)) {
                cancel(instance.id(), reason);
            }
        }
    }

    public long trackingLockTick(
            long startTick,
            long detonateTick
    ) {
        return TelegraphTimeline.trackingLockTick(
                startTick,
                detonateTick,
                settings.trackingLockThreshold());
    }

    public double warningPhaseThreshold() {
        return settings.warningPhaseThreshold();
    }

    private void tick() {
        long currentTick =
                plugin.getServer().getCurrentTick();
        for (TelegraphInstance instance
                : List.copyOf(active.values())) {
            tickInstance(instance, currentTick);
        }
        if (currentTick % VIEWER_SYNC_INTERVAL == 0L) {
            syncViewers(currentTick);
        }
        if (settings.enabled()
                && settings.fallbackServerParticles()
                && currentTick % FALLBACK_INTERVAL == 0L) {
            renderFallback(currentTick);
        }
    }

    private void tickInstance(
            TelegraphInstance instance,
            long currentTick
    ) {
        if (instance.removed()) {
            active.remove(instance.id(), instance);
            return;
        }
        TelegraphRequest request = instance.request();
        Entity source = plugin.getServer().getEntity(
                instance.sourceId());
        if (!instance.cancelled()
                && !instance.detonated()
                && (source == null
                || !source.isValid()
                || !source.getWorld().getUID().equals(
                request.worldId()))) {
            cancel(
                    instance.id(),
                    TelegraphInstance.CancellationReason
                            .SOURCE_REMOVED);
        }
        if (!instance.cancelled()
                && !instance.detonated()
                && request.trackingMode()
                == TelegraphInstance.TrackingMode.TARGET
                && !instance.locked()) {
            updateTracking(instance, currentTick);
        }
        if (!instance.cancelled()
                && !instance.detonated()
                && currentTick
                > request.detonateTick() + 2L) {
            cancel(
                    instance.id(),
                    TelegraphInstance.CancellationReason
                            .EXPIRED);
        }
        boolean removeCancelled = instance.cancelled()
                && currentTick - instance.cancelledAtTick()
                >= CANCEL_VISIBLE_TICKS;
        if (removeCancelled
                || currentTick >= request.expireTick()) {
            remove(instance, currentTick);
        }
    }

    private void updateTracking(
            TelegraphInstance instance,
            long currentTick
    ) {
        TelegraphRequest request = instance.request();
        Player target = request.targetId() == null
                ? null
                : plugin.getServer().getPlayer(
                request.targetId());
        boolean mustLock = currentTick >= request.lockTick()
                || target == null
                || !target.isValid()
                || target.isDead()
                || !target.getWorld().getUID().equals(
                request.worldId());
        if (!mustLock
                && currentTick % VIEWER_SYNC_INTERVAL == 0L) {
            Location location = target.getLocation();
            if (instance.updateCenter(
                    location.getX(),
                    location.getY(),
                    location.getZ())
                    && settings.enabled()) {
                broadcast(
                        TelegraphOperation.UPDATE,
                        instance,
                        currentTick,
                        true);
            }
        }
        if (mustLock && instance.lock()) {
            if (settings.enabled()) {
                broadcast(
                        TelegraphOperation.LOCK,
                        instance,
                        currentTick,
                        true);
            }
        }
    }

    private void remove(
            TelegraphInstance instance,
            long currentTick
    ) {
        if (!instance.markRemoved()) {
            return;
        }
        if (settings.enabled()) {
            broadcast(
                    TelegraphOperation.REMOVE,
                    instance,
                    currentTick,
                    true);
        }
        active.remove(instance.id(), instance);
        for (ViewerState state : viewerStates.values()) {
            state.sent.remove(instance.id());
        }
    }

    private void syncViewers(long currentTick) {
        Set<UUID> online = new HashSet<>();
        for (Player player
                : plugin.getServer().getOnlinePlayers()) {
            online.add(player.getUniqueId());
            if (!settings.enabled()
                    || !listens(player)) {
                viewerStates.remove(
                        player.getUniqueId());
                continue;
            }
            ViewerState state = viewerStates.computeIfAbsent(
                    player.getUniqueId(),
                    ignored -> new ViewerState(
                            player.getWorld().getUID()));
            if (!state.worldId.equals(
                    player.getWorld().getUID())) {
                send(
                        player,
                        TelegraphPacket.clear(
                                ++packetSequence,
                                currentTick));
                state.sent.clear();
                state.worldId =
                        player.getWorld().getUID();
            }
            List<TelegraphInstance> visible =
                    active.values().stream()
                            .filter(instance -> visibleTo(
                                    player, instance))
                            .sorted(Comparator.comparing(
                                    instance -> distanceSquared(
                                            player, instance)))
                            .limit(settings.maximumActivePerPlayer())
                            .toList();
            Set<UUID> visibleIds = new HashSet<>();
            for (TelegraphInstance instance : visible) {
                visibleIds.add(instance.id());
                if (state.sent.add(instance.id())) {
                    send(
                            player,
                            TelegraphPacket.from(
                                    TelegraphOperation.CREATE,
                                    ++packetSequence,
                                    currentTick,
                                    instance));
                }
            }
            for (UUID previous : Set.copyOf(state.sent)) {
                if (visibleIds.contains(previous)) {
                    continue;
                }
                TelegraphInstance instance =
                        active.get(previous);
                if (instance != null) {
                    send(
                            player,
                            TelegraphPacket.from(
                                    TelegraphOperation.REMOVE,
                                    ++packetSequence,
                                    currentTick,
                                    instance));
                }
                state.sent.remove(previous);
            }
        }
        viewerStates.keySet().removeIf(
                id -> !online.contains(id));
        helloConfirmedViewers.retainAll(online);
    }

    private void broadcast(
            TelegraphOperation operation,
            TelegraphInstance instance,
            long currentTick,
            boolean onlyPreviouslySent
    ) {
        for (Player player
                : plugin.getServer().getOnlinePlayers()) {
            if (!listens(player)
                    || !visibleTo(player, instance)) {
                continue;
            }
            ViewerState state = viewerStates.computeIfAbsent(
                    player.getUniqueId(),
                    ignored -> new ViewerState(
                            player.getWorld().getUID()));
            if (!state.worldId.equals(
                    player.getWorld().getUID())) {
                state.sent.clear();
                state.worldId =
                        player.getWorld().getUID();
            }
            if (onlyPreviouslySent
                    && !state.sent.contains(instance.id())) {
                continue;
            }
            state.sent.add(instance.id());
            send(
                    player,
                    TelegraphPacket.from(
                            operation,
                            ++packetSequence,
                            currentTick,
                            instance));
        }
    }

    private boolean visibleTo(
            Player player,
            TelegraphInstance instance
    ) {
        return player.getWorld().getUID().equals(
                instance.request().worldId())
                && distanceSquared(player, instance)
                <= settings.displayRange()
                * settings.displayRange();
    }

    private double distanceSquared(
            Player player,
            TelegraphInstance instance
    ) {
        Location location = player.getLocation();
        double dx = location.getX() - instance.centerX();
        double dy = location.getY() - instance.centerY();
        double dz = location.getZ() - instance.centerZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean listens(Player player) {
        return player.getListeningPluginChannels()
                .contains(TelegraphPacket.CHANNEL);
    }

    private boolean supportsClientRendering(
            Player player
    ) {
        return !TelegraphViewerPolicy.shouldSendFallback(
                listens(player),
                helloConfirmedViewers.contains(
                        player.getUniqueId()));
    }

    @Override
    public void onPluginMessageReceived(
            String channel,
            Player player,
            byte[] message
    ) {
        if (!TelegraphPacket.HELLO_CHANNEL.equals(channel)
                || message.length != 1
                || Byte.toUnsignedInt(message[0])
                != TelegraphPacket.PROTOCOL_VERSION) {
            return;
        }
        helloConfirmedViewers.add(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        helloConfirmedViewers.remove(playerId);
        viewerStates.remove(playerId);
    }

    private void send(
            Player player,
            TelegraphPacket packet
    ) {
        player.sendPluginMessage(
                plugin,
                TelegraphPacket.CHANNEL,
                packet.encode());
    }

    private void sendClearToAll(long currentTick) {
        for (Player player
                : plugin.getServer().getOnlinePlayers()) {
            if (listens(player)) {
                send(
                        player,
                        TelegraphPacket.clear(
                                ++packetSequence,
                                currentTick));
            }
        }
    }

    private void renderFallback(long currentTick) {
        for (TelegraphInstance instance
                : active.values()) {
            if (instance.cancelled()
                    || instance.detonated()
                    || currentTick
                    >= instance.request().detonateTick()) {
                continue;
            }
            for (Player player
                    : plugin.getServer()
                    .getOnlinePlayers()) {
                if (supportsClientRendering(player)
                        || !visibleTo(player, instance)) {
                    continue;
                }
                renderFallback(player, instance);
            }
        }
    }

    private void renderFallback(
            Player player,
            TelegraphInstance instance
    ) {
        TelegraphRequest request = instance.request();
        Particle.DustOptions dust = dust(request.theme());
        if (request.shape()
                == TelegraphInstance.Shape.LINE) {
            renderFallbackLine(
                    player, instance, dust);
            return;
        }
        renderFallbackCircle(
                player,
                instance,
                request.radius(),
                dust);
        if (request.shape()
                == TelegraphInstance.Shape.DONUT
                && request.innerRadius() > 0.0) {
            renderFallbackCircle(
                    player,
                    instance,
                    request.innerRadius(),
                    dust);
        }
    }

    private void renderDetonationFallback(
            TelegraphInstance instance
    ) {
        World world = plugin.getServer().getWorld(
                instance.request().worldId());
        if (world == null) {
            return;
        }
        Location center = new Location(
                world,
                instance.centerX(),
                instance.centerY() + 0.2,
                instance.centerZ());
        for (Player player : world.getPlayers()) {
            if (supportsClientRendering(player)
                    || !visibleTo(player, instance)) {
                continue;
            }
            player.spawnParticle(
                    Particle.BLOCK,
                    center,
                    14,
                    0.8, 0.25, 0.8,
                    0.08,
                    center.clone()
                            .subtract(0.0, 0.3, 0.0)
                            .getBlock()
                            .getBlockData());
            player.spawnParticle(
                    Particle.CLOUD,
                    center,
                    10,
                    0.8, 0.15, 0.8,
                    0.03);
        }
    }

    private void renderFallbackCircle(
            Player player,
            TelegraphInstance instance,
            double radius,
            Particle.DustOptions dust
    ) {
        int points = Math.max(
                16,
                (int) Math.ceil(radius * 3.0));
        World world = player.getWorld();
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0
                    * index / points;
            Location point = new Location(
                    world,
                    instance.centerX()
                            + Math.cos(angle) * radius,
                    instance.centerY() + 0.12,
                    instance.centerZ()
                            + Math.sin(angle) * radius);
            player.spawnParticle(
                    Particle.DUST,
                    point,
                    1,
                    0.0, 0.0, 0.0,
                    0.0,
                    dust);
        }
    }

    private void renderFallbackLine(
            Player player,
            TelegraphInstance instance,
            Particle.DustOptions dust
    ) {
        TelegraphRequest request = instance.request();
        double sideX = -request.directionZ()
                * request.width() * 0.5;
        double sideZ = request.directionX()
                * request.width() * 0.5;
        for (double distance = 0.0;
             distance <= request.length();
             distance += 1.25) {
            for (int sign : new int[]{-1, 1}) {
                Location point = new Location(
                        player.getWorld(),
                        instance.centerX()
                                + request.directionX()
                                * distance
                                + sideX * sign,
                        instance.centerY() + 0.12,
                        instance.centerZ()
                                + request.directionZ()
                                * distance
                                + sideZ * sign);
                player.spawnParticle(
                        Particle.DUST,
                        point,
                        1,
                        0.0, 0.0, 0.0,
                        0.0,
                        dust);
            }
        }
    }

    private Particle.DustOptions dust(
            TelegraphInstance.VisualTheme theme
    ) {
        return switch (theme) {
            case DAMAGE -> DAMAGE_DUST;
            case DEBUFF -> DEBUFF_DUST;
            case POISON -> POISON_DUST;
            case SAFE -> SAFE_DUST;
            case OPPORTUNITY -> OPPORTUNITY_DUST;
        };
    }

    public record Settings(
            boolean enabled,
            double displayRange,
            boolean fallbackServerParticles,
            int maximumActivePerPlayer,
            int maximumActiveGlobal,
            int maximumActivePerSource,
            int groundSearchUp,
            int groundSearchDown,
            double warningPhaseThreshold,
            double trackingLockThreshold,
            double cancellationAnimationSeconds,
            double detonationAnimationSeconds
    ) {
        private static Settings load(JavaPlugin plugin) {
            String root = "telegraphs.";
            return new Settings(
                    plugin.getConfig().getBoolean(
                            root + "enabled", true),
                    clamp(
                            plugin.getConfig().getDouble(
                                    root + "display-range",
                                    64.0),
                            8.0, 128.0),
                    plugin.getConfig().getBoolean(
                            root
                                    + "fallback-server-particles",
                            true),
                    Math.clamp(
                            plugin.getConfig().getInt(
                                    root
                                            + "maximum-active-per-player",
                                    32),
                            1, 64),
                    Math.clamp(
                            plugin.getConfig().getInt(
                                    root + "maximum-active-global",
                                    512),
                            1, 4_096),
                    Math.clamp(
                            plugin.getConfig().getInt(
                                    root + "maximum-active-per-source",
                                    32),
                            1, 256),
                    Math.clamp(
                            plugin.getConfig().getInt(
                                    root + "ground-search-up",
                                    3),
                            0, 8),
                    Math.clamp(
                            plugin.getConfig().getInt(
                                    root + "ground-search-down",
                                    3),
                            0, 8),
                    clamp(
                            plugin.getConfig().getDouble(
                                    root
                                            + "warning-phase-threshold",
                                    0.30),
                            0.05, 0.95),
                    clamp(
                            plugin.getConfig().getDouble(
                                    root
                                            + "tracking-lock-threshold",
                                    0.40),
                            0.05, 0.95),
                    clamp(
                            plugin.getConfig().getDouble(
                                    root
                                            + "cancellation-animation-seconds",
                                    0.15),
                            0.05, 1.0),
                    clamp(
                            plugin.getConfig().getDouble(
                                    root
                                            + "detonation-animation-seconds",
                                    0.20),
                            0.05, 1.0));
        }

        private static double clamp(
                double value,
                double minimum,
                double maximum
        ) {
            if (!Double.isFinite(value)) {
                return minimum;
            }
            return Math.clamp(value, minimum, maximum);
        }
    }

    private static final class ViewerState {
        private UUID worldId;
        private final Set<UUID> sent = new HashSet<>();

        private ViewerState(UUID worldId) {
            this.worldId = worldId;
        }
    }
}
