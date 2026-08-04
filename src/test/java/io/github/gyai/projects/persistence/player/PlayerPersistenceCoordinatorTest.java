package io.github.gyai.projects.persistence.player;

import io.github.gyai.projects.feature.FeatureFlagSnapshot;
import io.github.gyai.projects.player.progress.PlayerProgressBuilder;
import io.github.gyai.projects.player.progress.PlayerProgressRecordV1;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class PlayerPersistenceCoordinatorTest {
    private PlayerPersistenceCoordinatorTest() {
    }

    public static void main(String[] args) throws Exception {
        flagFalsePreservesMemoryOnlyBehavior();
        loadMustSucceedBeforeActivation();
        logoutFinalSaveAndReconnect();
        closeDrainsAndRejectsFurtherSessions();
    }

    private static void flagFalsePreservesMemoryOnlyBehavior() {
        CountingRepository repository = new CountingRepository();
        PlayerPersistenceCoordinator coordinator =
                new PlayerPersistenceCoordinator(
                        FeatureFlagSnapshot.allDisabled(), repository, Set.of());
        PlayerPersistenceSession session = coordinator.connect(UUID.randomUUID());
        assert session.status()
                == PlayerPersistenceSessionStatus.DISABLED_MEMORY_ONLY;
        assert !session.persistenceBackedFeaturesAllowed();
        assert repository.loads.get() == 0;
        assert repository.saves.get() == 0;
        coordinator.close();
    }

    private static void loadMustSucceedBeforeActivation() {
        CountingRepository repository = new CountingRepository();
        repository.loadResult = new PlayerProgressLoadResult(
                PlayerProgressLoadStatus.QUARANTINED_CORRUPT,
                null, Path.of("quarantine.yml"), "corrupt");
        PlayerPersistenceCoordinator coordinator =
                new PlayerPersistenceCoordinator(
                        true, repository, Set.of(), 2);
        UUID player = UUID.randomUUID();
        PlayerPersistenceSession blocked = coordinator.connect(player);
        assert blocked.status()
                == PlayerPersistenceSessionStatus.BLOCKED_LOAD_FAILURE;
        assert !blocked.persistenceBackedFeaturesAllowed();
        assert coordinator.activeSessionCount() == 0;
        coordinator.close();
    }

    private static void logoutFinalSaveAndReconnect() throws Exception {
        Path root = Files.createTempDirectory("projects-player-lifecycle-");
        UUID player = UUID.randomUUID();
        try {
            FilePlayerProgressRepository repository = fileRepository(root);
            PlayerPersistenceCoordinator coordinator =
                    new PlayerPersistenceCoordinator(
                            true, repository, Set.of(), 8);
            PlayerPersistenceSession initial = coordinator.connect(player);
            assert initial.status() == PlayerPersistenceSessionStatus.ACTIVE_NEW;
            assert coordinator.connect(player).status()
                    == PlayerPersistenceSessionStatus.DUPLICATE_CONNECTION;
            PlayerProgressRecordV1 finalRecord = new PlayerProgressRecordV1(
                    new PlayerProgressBuilder(player)
                            .level(8)
                            .experience(500)
                            .revision(1)
                            .lastSavedAt(Instant.parse("2026-08-05T02:00:00Z"))
                            .build());
            assert coordinator.disconnect(finalRecord, UUID.randomUUID())
                    .get().status() == PlayerProgressSaveStatus.COMMITTED;
            assert coordinator.activeSessionCount() == 0;
            coordinator.close();

            FilePlayerProgressRepository reconnectedRepository =
                    fileRepository(root);
            PlayerPersistenceCoordinator reconnected =
                    new PlayerPersistenceCoordinator(
                            true, reconnectedRepository, Set.of(), 8);
            PlayerPersistenceSession loaded = reconnected.connect(player);
            assert loaded.status() == PlayerPersistenceSessionStatus.ACTIVE_LOADED;
            assert loaded.progress().orElseThrow().equals(finalRecord);
            reconnected.close();
        } finally {
            deleteRecursively(root);
        }
    }

    private static void closeDrainsAndRejectsFurtherSessions()
            throws Exception {
        Path root = Files.createTempDirectory("projects-player-disable-");
        UUID player = UUID.randomUUID();
        try {
            FilePlayerProgressRepository repository = fileRepository(root);
            PlayerProgressRecordV1 record = new PlayerProgressRecordV1(
                    new PlayerProgressBuilder(player)
                            .revision(1)
                            .lastSavedAt(Instant.now())
                            .build());
            CompletableFuture<PlayerProgressSaveResult> pending =
                    repository.save(record, UUID.randomUUID());
            PlayerPersistenceCoordinator coordinator =
                    new PlayerPersistenceCoordinator(
                            true, repository, Set.of(), 8);
            coordinator.close();
            coordinator.close();
            assert pending.get().successful();
            assert coordinator.connect(UUID.randomUUID()).status()
                    == PlayerPersistenceSessionStatus.CLOSED;
            assert !repository.acceptingWrites();
        } finally {
            deleteRecursively(root);
        }
    }

    private static FilePlayerProgressRepository fileRepository(Path root) {
        return new FilePlayerProgressRepository(
                root, Set.of(), 8,
                new NioPlayerProgressFileOperations(), Duration.ofSeconds(5));
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class CountingRepository
            implements PlayerProgressRepository {
        private final AtomicInteger loads = new AtomicInteger();
        private final AtomicInteger saves = new AtomicInteger();
        private PlayerProgressLoadResult loadResult =
                new PlayerProgressLoadResult(
                        PlayerProgressLoadStatus.MISSING,
                        null, null, "");
        private boolean accepting = true;

        @Override
        public PlayerProgressLoadResult load(UUID playerId) {
            loads.incrementAndGet();
            return loadResult;
        }

        @Override
        public CompletableFuture<PlayerProgressSaveResult> save(
                PlayerProgressRecordV1 record,
                UUID requestId
        ) {
            saves.incrementAndGet();
            return CompletableFuture.completedFuture(
                    new PlayerProgressSaveResult(
                            PlayerProgressSaveStatus.COMMITTED,
                            record.snapshot().playerId(),
                            record.snapshot().revision(), requestId,
                            null, ""));
        }

        @Override
        public boolean acceptingWrites() {
            return accepting;
        }

        @Override
        public void close() {
            accepting = false;
        }
    }
}
