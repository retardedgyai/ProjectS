package io.github.gyai.projects.persistence.player;

import io.github.gyai.projects.feature.FeatureFlagSnapshot;
import io.github.gyai.projects.feature.FeatureKey;
import io.github.gyai.projects.player.progress.PlayerProgressBuilder;
import io.github.gyai.projects.player.progress.PlayerProgressRecordV1;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Bukkit-free login/logout lifecycle. A future Paper adapter captures snapshots
 * before calling this boundary; the default-disabled flag performs no I/O.
 */
public final class PlayerPersistenceCoordinator implements AutoCloseable {
    public static final int DEFAULT_MAX_ACTIVE_SESSIONS = 10_000;

    private final boolean persistenceEnabled;
    private final PlayerProgressRepository repository;
    private final Set<String> settingWhitelist;
    private final int maximumActiveSessions;
    private final Map<UUID, PlayerPersistenceSession> active =
            new LinkedHashMap<>();
    private boolean closed;

    public PlayerPersistenceCoordinator(
            FeatureFlagSnapshot flags,
            PlayerProgressRepository repository,
            Set<String> settingWhitelist
    ) {
        this(flags != null && flags.isEnabled(FeatureKey.PLAYER_PERSISTENCE),
                repository, settingWhitelist, DEFAULT_MAX_ACTIVE_SESSIONS);
    }

    public PlayerPersistenceCoordinator(
            boolean persistenceEnabled,
            PlayerProgressRepository repository,
            Set<String> settingWhitelist,
            int maximumActiveSessions
    ) {
        if (maximumActiveSessions < 1 || maximumActiveSessions > 100_000) {
            throw new IllegalArgumentException("active session limit is invalid");
        }
        this.persistenceEnabled = persistenceEnabled;
        this.repository = Objects.requireNonNull(repository, "repository");
        this.settingWhitelist = Set.copyOf(
                settingWhitelist == null ? Set.of() : settingWhitelist);
        this.maximumActiveSessions = maximumActiveSessions;
    }

    public synchronized PlayerPersistenceSession connect(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (closed) return session(PlayerPersistenceSessionStatus.CLOSED,
                playerId, null, "coordinator is closed");
        if (!persistenceEnabled) {
            return session(PlayerPersistenceSessionStatus.DISABLED_MEMORY_ONLY,
                    playerId, null, "PLAYER_PERSISTENCE is disabled");
        }
        PlayerPersistenceSession existing = active.get(playerId);
        if (existing != null) {
            return session(PlayerPersistenceSessionStatus.DUPLICATE_CONNECTION,
                    playerId, existing.record(), "player already has an active writer");
        }
        if (active.size() >= maximumActiveSessions) {
            return session(PlayerPersistenceSessionStatus.CAPACITY_REACHED,
                    playerId, null, "active session limit reached");
        }
        PlayerProgressLoadResult loaded = repository.load(playerId);
        PlayerPersistenceSession opened;
        if (loaded.status() == PlayerProgressLoadStatus.LOADED) {
            opened = session(PlayerPersistenceSessionStatus.ACTIVE_LOADED,
                    playerId, loaded.loadedRecord().orElseThrow(), "");
        } else if (loaded.status() == PlayerProgressLoadStatus.MISSING) {
            PlayerProgressRecordV1 initial = new PlayerProgressRecordV1(
                    new PlayerProgressBuilder(playerId, settingWhitelist)
                            .revision(0)
                            .lastSavedAt(Instant.EPOCH)
                            .build());
            opened = session(PlayerPersistenceSessionStatus.ACTIVE_NEW,
                    playerId, initial, "");
        } else {
            return session(PlayerPersistenceSessionStatus.BLOCKED_LOAD_FAILURE,
                    playerId, null,
                    loaded.status() + ": " + loaded.detail());
        }
        active.put(playerId, opened);
        return opened;
    }

    public synchronized CompletableFuture<PlayerProgressSaveResult> disconnect(
            PlayerProgressRecordV1 finalRecord,
            UUID requestId
    ) {
        Objects.requireNonNull(finalRecord, "finalRecord");
        Objects.requireNonNull(requestId, "requestId");
        UUID playerId = finalRecord.snapshot().playerId();
        if (closed || !persistenceEnabled) {
            return CompletableFuture.completedFuture(new PlayerProgressSaveResult(
                    PlayerProgressSaveStatus.CLOSED, playerId,
                    finalRecord.snapshot().revision(), requestId, null,
                    closed ? "coordinator is closed" : "persistence is disabled"));
        }
        PlayerPersistenceSession removed = active.remove(playerId);
        if (removed == null) {
            return CompletableFuture.completedFuture(new PlayerProgressSaveResult(
                    PlayerProgressSaveStatus.CONFLICT, playerId,
                    finalRecord.snapshot().revision(), requestId, null,
                    "player has no active persistence session"));
        }
        return repository.save(finalRecord, requestId);
    }

    public synchronized int activeSessionCount() {
        return active.size();
    }

    public boolean persistenceEnabled() {
        return persistenceEnabled;
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            active.clear();
        }
        repository.close();
    }

    private static PlayerPersistenceSession session(
            PlayerPersistenceSessionStatus status,
            UUID playerId,
            PlayerProgressRecordV1 record,
            String detail
    ) {
        return new PlayerPersistenceSession(status, playerId, record, detail);
    }
}
