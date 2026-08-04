package io.github.gyai.projects.persistence.player;

import io.github.gyai.projects.player.progress.PlayerProgressBuilder;
import io.github.gyai.projects.player.progress.PlayerProgressRecordV1;
import io.github.gyai.projects.player.progress.PlayerProgressSnapshot;
import io.github.gyai.projects.player.progress.QuestProgressState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class PlayerProgressRepositoryTest {
    private PlayerProgressRepositoryTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("projects-player-repository-");
        try {
            roundTripMissingBackupAndUtf8(root.resolve("roundtrip"));
            corruptAndUnknownAreQuarantined(root.resolve("quarantine"));
            atomicReplacementFailurePreservesSource(root.resolve("atomic"));
            staleAndDuplicateRequestsAreSafe(root.resolve("ordering"));
            simultaneousNewerSaveRejectsStaleCompletion(root.resolve("simultaneous"));
            boundedQueueRejectsOverflow(root.resolve("bounded"));
            closeDrainsAndIsIdempotent(root.resolve("close"));
        } finally {
            deleteRecursively(root);
        }
    }

    private static void roundTripMissingBackupAndUtf8(Path directory)
            throws Exception {
        UUID player = UUID.randomUUID();
        try (FilePlayerProgressRepository repository = repository(directory)) {
            assert repository.load(player).status() == PlayerProgressLoadStatus.MISSING;
            PlayerProgressRecordV1 first = record(player, 1, "日本語");
            assert repository.save(first, UUID.randomUUID()).get().status()
                    == PlayerProgressSaveStatus.COMMITTED;
            PlayerProgressLoadResult loaded = repository.load(player);
            assert loaded.status() == PlayerProgressLoadStatus.LOADED;
            assert loaded.loadedRecord().orElseThrow().equals(first);
            String source = Files.readString(
                    directory.resolve(player + ".yml"), StandardCharsets.UTF_8);
            assert source.contains("日本語");

            PlayerProgressRecordV1 second = record(player, 2, "fr_fr");
            assert repository.save(second, UUID.randomUUID()).get().status()
                    == PlayerProgressSaveStatus.COMMITTED;
            Path backup = directory.resolve("backups")
                    .resolve(player + ".previous.yml");
            assert Files.exists(backup);
            assert Files.readString(backup, StandardCharsets.UTF_8)
                    .contains("revision: 1");
            assert repository.load(player).loadedRecord().orElseThrow().equals(second);
        }
    }

    private static void corruptAndUnknownAreQuarantined(Path directory)
            throws Exception {
        Files.createDirectories(directory);
        UUID corrupt = UUID.randomUUID();
        Path corruptPath = directory.resolve(corrupt + ".yml");
        Files.writeString(corruptPath, "schema-id: [broken", StandardCharsets.UTF_8);
        byte[] corruptBefore = Files.readAllBytes(corruptPath);
        try (FilePlayerProgressRepository repository = repository(directory)) {
            PlayerProgressLoadResult result = repository.load(corrupt);
            assert result.status() == PlayerProgressLoadStatus.QUARANTINED_CORRUPT;
            assert result.quarantinePath().filter(Files::exists).isPresent();
            assert java.util.Arrays.equals(corruptBefore, Files.readAllBytes(corruptPath));
        }

        UUID unknown = UUID.randomUUID();
        Path unknownPath = directory.resolve(unknown + ".yml");
        Files.writeString(unknownPath,
                "schema-id: player-data\nschema-version: 999\n",
                StandardCharsets.UTF_8);
        try (FilePlayerProgressRepository repository = repository(directory)) {
            PlayerProgressLoadResult result = repository.load(unknown);
            assert result.status()
                    == PlayerProgressLoadStatus.QUARANTINED_UNKNOWN_VERSION;
            assert result.quarantinePath().filter(Files::exists).isPresent();
            assert Files.readString(unknownPath).contains("999");
        }
    }

    private static void atomicReplacementFailurePreservesSource(Path directory)
            throws Exception {
        UUID player = UUID.randomUUID();
        try (FilePlayerProgressRepository seed = repository(directory)) {
            assert seed.save(record(player, 1, "ja_jp"), UUID.randomUUID())
                    .get().successful();
        }
        byte[] before = Files.readAllBytes(directory.resolve(player + ".yml"));
        PlayerProgressFileOperations failure = new DelegatingOperations() {
            @Override
            public void atomicReplace(Path temporary, Path target)
                    throws IOException {
                throw new IOException("injected atomic failure");
            }
        };
        try (FilePlayerProgressRepository repository = new FilePlayerProgressRepository(
                directory, Set.of("locale"), 4, failure,
                Duration.ofSeconds(5))) {
            UUID requestId = UUID.randomUUID();
            PlayerProgressRecordV1 attempted = record(player, 2, "en_us");
            PlayerProgressSaveResult result = repository.save(
                    attempted, requestId).get();
            assert result.status() == PlayerProgressSaveStatus.FAILED;
            assert repository.save(attempted, requestId).get().status()
                    == PlayerProgressSaveStatus.FAILED;
            assert java.util.Arrays.equals(before,
                    Files.readAllBytes(directory.resolve(player + ".yml")));
            try (var files = Files.list(directory)) {
                assert files.noneMatch(path -> path.getFileName().toString()
                        .endsWith(".tmp"));
            }
        }
    }

    private static void staleAndDuplicateRequestsAreSafe(Path directory)
            throws Exception {
        UUID player = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        PlayerProgressRecordV1 revisionTwo = record(player, 2, "ja_jp");
        try (FilePlayerProgressRepository repository = repository(directory)) {
            assert repository.save(revisionTwo, request).get().status()
                    == PlayerProgressSaveStatus.COMMITTED;
            assert repository.save(revisionTwo, request).get().status()
                    == PlayerProgressSaveStatus.IDEMPOTENT;
            assert repository.save(revisionTwo, UUID.randomUUID()).get().status()
                    == PlayerProgressSaveStatus.IDEMPOTENT;
            assert repository.save(record(player, 1, "ja_jp"), UUID.randomUUID())
                    .get().status() == PlayerProgressSaveStatus.STALE;
            assert repository.save(record(player, 2, "different"), UUID.randomUUID())
                    .get().status() == PlayerProgressSaveStatus.CONFLICT;
        }
    }

    private static void simultaneousNewerSaveRejectsStaleCompletion(Path directory)
            throws Exception {
        UUID player = UUID.randomUUID();
        BlockingOperations operations = new BlockingOperations();
        try (FilePlayerProgressRepository repository = new FilePlayerProgressRepository(
                directory, Set.of("locale"), 8, operations,
                Duration.ofSeconds(5))) {
            var older = repository.save(record(player, 1, "old"), UUID.randomUUID());
            assert operations.started.await(5, TimeUnit.SECONDS);
            var newer = repository.save(record(player, 2, "new"), UUID.randomUUID());
            operations.release.countDown();
            assert older.get().status() == PlayerProgressSaveStatus.STALE;
            assert newer.get().status() == PlayerProgressSaveStatus.COMMITTED;
            assert repository.load(player).loadedRecord().orElseThrow()
                    .snapshot().revision() == 2;
        }
    }

    private static void closeDrainsAndIsIdempotent(Path directory)
            throws Exception {
        UUID player = UUID.randomUUID();
        FilePlayerProgressRepository repository = repository(directory);
        var pending = repository.save(record(player, 1, "ja_jp"), UUID.randomUUID());
        repository.close();
        repository.close();
        assert pending.get().successful();
        assert !repository.acceptingWrites();
        assert repository.save(record(player, 2, "ja_jp"), UUID.randomUUID())
                .get().status() == PlayerProgressSaveStatus.CLOSED;
    }

    private static void boundedQueueRejectsOverflow(Path directory)
            throws Exception {
        BlockingOperations operations = new BlockingOperations();
        try (FilePlayerProgressRepository repository = new FilePlayerProgressRepository(
                directory, Set.of("locale"), 1, operations,
                Duration.ofSeconds(5))) {
            var running = repository.save(
                    record(UUID.randomUUID(), 1, "one"), UUID.randomUUID());
            assert operations.started.await(5, TimeUnit.SECONDS);
            var queued = repository.save(
                    record(UUID.randomUUID(), 1, "two"), UUID.randomUUID());
            var rejected = repository.save(
                    record(UUID.randomUUID(), 1, "three"), UUID.randomUUID());
            assert rejected.get().status() == PlayerProgressSaveStatus.QUEUE_FULL;
            operations.release.countDown();
            assert running.get().successful();
            assert queued.get().successful();
        }
    }

    private static FilePlayerProgressRepository repository(Path directory) {
        return new FilePlayerProgressRepository(
                directory, Set.of("locale"), 16,
                new NioPlayerProgressFileOperations(), Duration.ofSeconds(5));
    }

    private static PlayerProgressRecordV1 record(
            UUID player,
            long revision,
            String locale
    ) {
        PlayerProgressSnapshot snapshot = new PlayerProgressBuilder(
                player, Set.of("locale"))
                .level(12)
                .experience(Long.MAX_VALUE)
                .passivePoints(3, 1)
                .allocatedPassiveNodeIds(Set.of("warrior.node"))
                .selectedClassId("warrior")
                .professionMastery(Map.of("smithing", 5L))
                .questStates(Map.of("quest.intro", new QuestProgressState(
                        "active", Map.of("step", 1L), Set.of())))
                .unlockIds(Set.of("region.start"))
                .currencies(Map.of("beta.coin", 7L))
                .persistentResources(Map.of("account.token", 1L))
                .settings(Map.of("locale", locale))
                .revision(revision)
                .lastSavedAt(Instant.parse("2026-08-05T00:00:00Z")
                        .plusSeconds(revision))
                .build();
        return new PlayerProgressRecordV1(snapshot);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static class DelegatingOperations
            implements PlayerProgressFileOperations {
        private final NioPlayerProgressFileOperations delegate =
                new NioPlayerProgressFileOperations();

        @Override
        public void writeAndFlush(Path temporary, byte[] bytes) throws IOException {
            delegate.writeAndFlush(temporary, bytes);
        }

        @Override
        public void copyPrevious(Path source, Path backup) throws IOException {
            delegate.copyPrevious(source, backup);
        }

        @Override
        public void atomicReplace(Path temporary, Path target) throws IOException {
            delegate.atomicReplace(temporary, target);
        }
    }

    private static final class BlockingOperations extends DelegatingOperations {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void writeAndFlush(Path temporary, byte[] bytes) throws IOException {
            super.writeAndFlush(temporary, bytes);
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("test release timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException(exception);
            }
        }
    }
}
