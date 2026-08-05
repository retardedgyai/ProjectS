package io.github.gyai.projects.beta.activation.track1.player;

import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.beta.activation.BetaMutationPolicy;
import io.github.gyai.projects.player.progress.PlayerProgressBuilder;
import io.github.gyai.projects.player.progress.PlayerProgressSnapshot;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Bounded, Bukkit-free shadow session service. */
public final class StagingPlayerProgressService implements StagingPlayerProgressPort, AutoCloseable {
    public static final int DEFAULT_MAXIMUM_SESSIONS = 512;
    public static final int DEFAULT_MAXIMUM_OBSERVATIONS = 512;

    private final BetaActivationPolicy policy;
    private final StagingPlayerProgressStore store;
    private final Clock clock;
    private final PlayerProgressShadowComparator comparator = new PlayerProgressShadowComparator();
    private final int maximumSessions;
    private final int maximumObservations;
    private final LinkedHashMap<UUID, Session> sessions = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, PlayerProgressObservation> observations = new LinkedHashMap<>();
    private boolean running;
    private boolean closed;

    public StagingPlayerProgressService(BetaActivationPolicy policy,
                                        StagingPlayerProgressStore store,
                                        Clock clock) {
        this(policy, store, clock, DEFAULT_MAXIMUM_SESSIONS, DEFAULT_MAXIMUM_OBSERVATIONS);
    }

    public StagingPlayerProgressService(BetaActivationPolicy policy,
                                        StagingPlayerProgressStore store,
                                        Clock clock,
                                        int maximumSessions,
                                        int maximumObservations) {
        if (policy == null || store == null || clock == null
                || maximumSessions < 1 || maximumSessions > 10_000
                || maximumObservations < 1 || maximumObservations > 10_000) {
            throw new IllegalArgumentException("invalid staging progress service");
        }
        this.policy = policy;
        this.store = store;
        this.clock = clock;
        this.maximumSessions = maximumSessions;
        this.maximumObservations = maximumObservations;
    }

    public synchronized void start() {
        if (closed) throw new IllegalStateException("service is closed");
        running = true;
    }

    @Override
    public synchronized PlayerProgressObservation onJoin(
            PlayerProgressSnapshot legacySnapshot, String worldName,
            boolean compatibleClient) {
        requireSnapshot(legacySnapshot);
        UUID playerId = legacySnapshot.playerId();
        if (!running || closed) return remember(closed(playerId, legacySnapshot));
        if (!allowed(playerId, worldName, compatibleClient)) {
            return remember(observation(playerId, PlayerProgressObservationStatus.POLICY_DENIED,
                    legacySnapshot, Optional.empty(), false, List.of("activationPolicy"),
                    legacySnapshot.revision()));
        }
        if (!sessions.containsKey(playerId) && sessions.size() >= maximumSessions) {
            return remember(observation(playerId, PlayerProgressObservationStatus.CAPACITY_REACHED,
                    legacySnapshot, Optional.empty(), false, List.of("sessionCapacity"),
                    legacySnapshot.revision()));
        }
        StagingPlayerProgressStore.Load loaded;
        try {
            loaded = store.load(playerId);
        } catch (RuntimeException exception) {
            loaded = new StagingPlayerProgressStore.Load(
                    StagingPlayerProgressStore.Load.Status.MALFORMED,
                    Optional.empty(), "staging load failed: "
                    + exception.getClass().getSimpleName());
        }
        PlayerProgressObservation result;
        if (loaded.status() == StagingPlayerProgressStore.Load.Status.LOADED) {
            PlayerProgressSnapshot staging = loaded.snapshot().orElseThrow();
            List<String> differences = comparator.differences(legacySnapshot, staging);
            result = observation(playerId, differences.isEmpty()
                            ? PlayerProgressObservationStatus.OBSERVED_MATCH
                            : PlayerProgressObservationStatus.OBSERVED_MISMATCH,
                    legacySnapshot, Optional.of(staging), differences.isEmpty(), differences,
                    staging.revision());
        } else if (loaded.status() == StagingPlayerProgressStore.Load.Status.MALFORMED) {
            result = observation(playerId, PlayerProgressObservationStatus.QUARANTINED,
                    legacySnapshot, Optional.empty(), false, List.of(loaded.detail()),
                    legacySnapshot.revision());
        } else if (loaded.status() == StagingPlayerProgressStore.Load.Status.CLOSED) {
            result = closed(playerId, legacySnapshot);
        } else {
            result = observation(playerId, PlayerProgressObservationStatus.STAGING_MISSING,
                    legacySnapshot, Optional.empty(), false, List.of(), legacySnapshot.revision());
        }
        sessions.put(playerId, new Session(legacySnapshot, worldName,
                compatibleClient, result.observedRevision()));
        return remember(result);
    }

    @Override
    public CompletionStage<PlayerProgressSaveObservation> onQuit(
            PlayerProgressSnapshot legacySnapshot, String worldName,
            boolean compatibleClient) {
        requireSnapshot(legacySnapshot);
        Session session;
        synchronized (this) {
            if (closed) return completed(save(legacySnapshot,
                    PlayerProgressSaveObservation.Status.CLOSED, "service is closed"));
            session = sessions.remove(legacySnapshot.playerId());
        }
        if (session == null) return completed(save(legacySnapshot,
                PlayerProgressSaveObservation.Status.NO_SESSION, "no active session"));
        if (!allowed(legacySnapshot.playerId(), worldName, compatibleClient)) {
            return completed(save(legacySnapshot,
                    PlayerProgressSaveObservation.Status.DENIED, "activation policy denied save"));
        }
        if (!policy.allowsMutation(BetaMutationPolicy.STAGING_WRITE)) {
            return completed(save(legacySnapshot,
                    PlayerProgressSaveObservation.Status.READ_ONLY, "READ_ONLY: no file written"));
        }
        PlayerProgressSnapshot next = PlayerProgressBuilder.from(legacySnapshot,
                        legacySnapshot.settings().keySet())
                .revision(Math.max(legacySnapshot.revision(), session.observedRevision()) + 1)
                .lastSavedAt(clock.instant())
                .build();
        try {
            return store.save(next).exceptionally(exception -> save(next,
                    PlayerProgressSaveObservation.Status.FAILED,
                    "staging save failed: " + exception.getClass().getSimpleName()));
        } catch (RuntimeException exception) {
            return completed(save(next, PlayerProgressSaveObservation.Status.FAILED,
                    "staging save failed: " + exception.getClass().getSimpleName()));
        }
    }

    @Override
    public synchronized Optional<PlayerProgressObservation> observation(UUID playerId) {
        return Optional.ofNullable(observations.get(playerId));
    }

    @Override
    public synchronized int activeSessions() {
        return sessions.size();
    }

    @Override
    public CompletionStage<Void> drain() {
        List<Session> draining;
        synchronized (this) {
            draining = new ArrayList<>(sessions.values());
        }
        CompletableFuture<?>[] saves = draining.stream()
                .map(session -> onQuit(session.snapshot(), session.worldName(),
                        session.compatibleClient()).toCompletableFuture())
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(saves);
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) return;
        }
        drain().toCompletableFuture().join();
        synchronized (this) {
            closed = true;
            running = false;
            sessions.clear();
            observations.clear();
        }
        store.close();
    }

    public synchronized boolean running() { return running && !closed; }

    private boolean allowed(UUID playerId, String worldName, boolean compatibleClient) {
        return policy.allowsAudience(playerId, compatibleClient)
                && policy.allowsWorld(worldName);
    }

    private PlayerProgressObservation remember(PlayerProgressObservation value) {
        observations.remove(value.playerId());
        observations.put(value.playerId(), value);
        while (observations.size() > maximumObservations) {
            observations.remove(observations.keySet().iterator().next());
        }
        return value;
    }

    private static PlayerProgressObservation observation(
            UUID playerId, PlayerProgressObservationStatus status,
            PlayerProgressSnapshot legacy, Optional<PlayerProgressSnapshot> staging,
            boolean matches, List<String> differences, long revision) {
        return new PlayerProgressObservation(playerId, status, legacy, staging,
                matches, differences, revision);
    }

    private static PlayerProgressObservation closed(UUID playerId,
                                                     PlayerProgressSnapshot legacy) {
        return observation(playerId, PlayerProgressObservationStatus.CLOSED, legacy,
                Optional.empty(), false, List.of("closed"), legacy.revision());
    }

    private static PlayerProgressSaveObservation save(
            PlayerProgressSnapshot snapshot,
            PlayerProgressSaveObservation.Status status,
            String detail) {
        return new PlayerProgressSaveObservation(snapshot.playerId(), status,
                snapshot.revision(), Optional.empty(), detail);
    }

    private static CompletionStage<PlayerProgressSaveObservation> completed(
            PlayerProgressSaveObservation value) {
        return CompletableFuture.completedFuture(value);
    }

    private static void requireSnapshot(PlayerProgressSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("legacy snapshot is required");
    }

    private record Session(PlayerProgressSnapshot snapshot, String worldName,
                           boolean compatibleClient, long observedRevision) { }
}
