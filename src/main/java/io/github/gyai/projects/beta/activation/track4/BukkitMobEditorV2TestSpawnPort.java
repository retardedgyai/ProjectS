package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.monster.editor.v2.MobEditorV2Service;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.UUID;

/** Callback-local staging test-spawn boundary; it never retains Bukkit objects. */
public final class BukkitMobEditorV2TestSpawnPort
        implements MobEditorV2Service.TestSpawnPort, AutoCloseable {
    public static final int MAXIMUM_ACTIVE_SPAWNS = 128;
    private final LinkedHashMap<UUID, UUID> entitiesByHandle = new LinkedHashMap<>();
    private boolean closed;

    @Override public synchronized MobEditorV2Service.TestSpawnHandle spawn(
            MobEditorV2Service.TestSpawnRequest request
    ) {
        if (closed || request == null || entitiesByHandle.size() >= MAXIMUM_ACTIVE_SPAWNS) {
            return null;
        }
        Player player = Bukkit.getPlayer(request.playerId());
        if (player == null || !player.isOnline()) return null;
        EntityType type;
        try { type = EntityType.valueOf(request.definition().entityType()); }
        catch (IllegalArgumentException invalid) { return null; }
        Entity entity;
        try { entity = player.getWorld().spawnEntity(player.getLocation(), type); }
        catch (RuntimeException failure) { return null; }
        UUID handleId = request.requestId();
        entitiesByHandle.put(handleId, entity.getUniqueId());
        return new MobEditorV2Service.TestSpawnHandle(handleId, request.playerId(),
                request.definition().mobId(), request.revision());
    }

    @Override public synchronized void cleanup(MobEditorV2Service.TestSpawnHandle handle) {
        if (handle == null) return;
        UUID entityId = entitiesByHandle.remove(handle.handleId());
        Entity entity = entityId == null ? null : Bukkit.getEntity(entityId);
        if (entity != null) entity.remove();
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        for (UUID entityId : entitiesByHandle.values()) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) entity.remove();
        }
        entitiesByHandle.clear();
    }

    public synchronized int activeCount() { return entitiesByHandle.size(); }
}
