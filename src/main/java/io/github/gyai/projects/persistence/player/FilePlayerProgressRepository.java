package io.github.gyai.projects.persistence.player;

import io.github.gyai.projects.player.progress.PlayerProgressRecordV1;
import org.bukkit.configuration.InvalidConfigurationException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One-file-per-player UTF-8 YAML repository with bounded asynchronous writes.
 * Unknown/corrupt sources remain authoritative and are copied to quarantine.
 */
public final class FilePlayerProgressRepository
        implements PlayerProgressRepository {
    public static final int DEFAULT_WRITE_QUEUE_CAPACITY = 128;
    public static final long MAX_FILE_BYTES = 1_048_576L;
    private static final int MAX_COMPLETED_REQUESTS = 2_048;
    private static final int MAX_QUARANTINE_FILES = 256;

    private final Path playersDirectory;
    private final Path backupDirectory;
    private final Path quarantineDirectory;
    private final PlayerProgressYamlCodec codec;
    private final PlayerProgressFileOperations fileOperations;
    private final ThreadPoolExecutor writeExecutor;
    private final Duration closeTimeout;
    private final Object gate = new Object();
    private final Map<UUID, AcceptedWrite> latestAcceptedByPlayer =
            new LinkedHashMap<>();
    private final Map<RequestKey, PendingWrite> pendingByRequest =
            new LinkedHashMap<>();
    private final LinkedHashMap<RequestKey, CompletedWrite> completedRequests =
            new LinkedHashMap<>(32, .75f, true);
    private volatile boolean acceptingWrites = true;

    public FilePlayerProgressRepository(Path playersDirectory) {
        this(playersDirectory, Set.of(), DEFAULT_WRITE_QUEUE_CAPACITY,
                new NioPlayerProgressFileOperations(), Duration.ofSeconds(30));
    }

    public FilePlayerProgressRepository(
            Path playersDirectory,
            Set<String> settingWhitelist,
            int writeQueueCapacity,
            PlayerProgressFileOperations fileOperations,
            Duration closeTimeout
    ) {
        if (playersDirectory == null) {
            throw new IllegalArgumentException("playersDirectory is required");
        }
        if (writeQueueCapacity < 1 || writeQueueCapacity > 4_096) {
            throw new IllegalArgumentException(
                    "write queue capacity must be between 1 and 4096");
        }
        if (fileOperations == null || closeTimeout == null
                || closeTimeout.isNegative() || closeTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "file operations and positive close timeout are required");
        }
        this.playersDirectory = playersDirectory.toAbsolutePath().normalize();
        backupDirectory = this.playersDirectory.resolve("backups");
        quarantineDirectory = this.playersDirectory.resolve("quarantine");
        codec = new PlayerProgressYamlCodec(settingWhitelist);
        this.fileOperations = fileOperations;
        this.closeTimeout = closeTimeout;
        writeExecutor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(writeQueueCapacity),
                daemonThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public PlayerProgressLoadResult load(UUID playerId) {
        if (playerId == null) throw new IllegalArgumentException("playerId is required");
        if (!acceptingWrites && writeExecutor.isTerminated()) {
            return loadResult(PlayerProgressLoadStatus.CLOSED, null, null,
                    "repository is closed");
        }
        Path source = playerPath(playerId);
        if (!Files.exists(source)) {
            return loadResult(PlayerProgressLoadStatus.MISSING, null, null, "");
        }
        try {
            String yaml = readUtf8(source);
            PlayerProgressYamlCodec.Header header = codec.inspectHeader(yaml);
            if (!PlayerProgressRecordV1.SCHEMA_ID.equals(header.schemaId())
                    || header.schemaVersion()
                    != PlayerProgressRecordV1.SCHEMA_VERSION) {
                Path quarantine = quarantineCopy(source, playerId, "unknown");
                return loadResult(
                        PlayerProgressLoadStatus.QUARANTINED_UNKNOWN_VERSION,
                        null, quarantine,
                        "unsupported schema " + header.schemaId()
                                + " v" + header.schemaVersion());
            }
            PlayerProgressRecordV1 record = codec.decode(yaml);
            if (!record.snapshot().playerId().equals(playerId)) {
                throw new IllegalArgumentException("player UUID does not match filename");
            }
            return loadResult(PlayerProgressLoadStatus.LOADED, record, null, "");
        } catch (Exception exception) {
            try {
                Path quarantine = quarantineCopy(source, playerId, "corrupt");
                return loadResult(PlayerProgressLoadStatus.QUARANTINED_CORRUPT,
                        null, quarantine, exception.getClass().getSimpleName());
            } catch (IOException quarantineFailure) {
                return loadResult(PlayerProgressLoadStatus.FAILED, null, null,
                        "quarantine failed: "
                                + quarantineFailure.getClass().getSimpleName());
            }
        }
    }

    @Override
    public CompletableFuture<PlayerProgressSaveResult> save(
            PlayerProgressRecordV1 record,
            UUID requestId
    ) {
        if (record == null || requestId == null) {
            throw new IllegalArgumentException("record and requestId are required");
        }
        UUID playerId = record.snapshot().playerId();
        long revision = record.snapshot().revision();
        RequestKey requestKey = new RequestKey(playerId, requestId);
        synchronized (gate) {
            if (!acceptingWrites) {
                return completed(status(PlayerProgressSaveStatus.CLOSED,
                        record, requestId, null, "repository is closed"));
            }
            CompletedWrite completed = completedRequests.get(requestKey);
            if (completed != null) {
                if (!record.equals(completed.record())) {
                    return completed(status(PlayerProgressSaveStatus.CONFLICT,
                        record, requestId, null,
                        "request ID was already used for different data"));
                }
                return completed(completed.result().successful()
                        ? idempotent(record, requestId,
                        completed.result().committedPath())
                        : completed.result());
            }
            PendingWrite duplicateRequest = pendingByRequest.get(requestKey);
            if (duplicateRequest != null) {
                return record.equals(duplicateRequest.record())
                        ? duplicateRequest.future()
                        : completed(status(PlayerProgressSaveStatus.CONFLICT,
                        record, requestId, null,
                        "request ID is pending with different data"));
            }
            AcceptedWrite accepted = latestAcceptedByPlayer.get(playerId);
            if (accepted != null) {
                if (revision < accepted.revision()) {
                    return completed(status(PlayerProgressSaveStatus.STALE,
                            record, requestId, null,
                            "a newer revision is already accepted"));
                }
                if (revision == accepted.revision()) {
                    if (!record.equals(accepted.record())) {
                        return completed(status(PlayerProgressSaveStatus.CONFLICT,
                            record, requestId, null,
                            "same revision has different data"));
                    }
                    return accepted.future().thenApply(result ->
                            result.successful()
                                    ? idempotent(record, requestId,
                                    result.committedPath())
                                    : new PlayerProgressSaveResult(
                                    result.status(), playerId, revision,
                                    requestId, result.committedPath(),
                                    result.detail()));
                }
            }
            CompletableFuture<PlayerProgressSaveResult> future =
                    new CompletableFuture<>();
            PendingWrite pending = new PendingWrite(record, requestId, future);
            AcceptedWrite replacement = new AcceptedWrite(
                    revision, record, future, requestId);
            latestAcceptedByPlayer.put(playerId, replacement);
            pendingByRequest.put(requestKey, pending);
            try {
                writeExecutor.execute(() -> executeWrite(requestKey, pending));
            } catch (RejectedExecutionException exception) {
                pendingByRequest.remove(requestKey);
                if (accepted == null) latestAcceptedByPlayer.remove(playerId);
                else latestAcceptedByPlayer.put(playerId, accepted);
                future.complete(status(PlayerProgressSaveStatus.QUEUE_FULL,
                        record, requestId, null, "write queue is full"));
            }
            return future;
        }
    }

    @Override
    public boolean acceptingWrites() {
        return acceptingWrites;
    }

    @Override
    public void close() {
        synchronized (gate) {
            if (!acceptingWrites) return;
            acceptingWrites = false;
            writeExecutor.shutdown();
        }
        try {
            if (!writeExecutor.awaitTermination(
                    closeTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                for (Runnable ignored : writeExecutor.shutdownNow()) {
                    // Futures for queued writes are completed below.
                }
                writeExecutor.awaitTermination(
                        Math.min(1_000L, closeTimeout.toMillis()),
                        TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writeExecutor.shutdownNow();
        } finally {
            synchronized (gate) {
                for (PendingWrite pending : pendingByRequest.values()) {
                    pending.future().complete(status(
                            PlayerProgressSaveStatus.CLOSED,
                            pending.record(), pending.requestId(), null,
                            "repository closed before write completed"));
                }
                pendingByRequest.clear();
                latestAcceptedByPlayer.clear();
            }
        }
    }

    public Path playersDirectory() {
        return playersDirectory;
    }

    private void executeWrite(RequestKey key, PendingWrite pending) {
        PlayerProgressSaveResult result;
        try {
            result = commit(pending.record(), pending.requestId());
        } catch (Exception exception) {
            result = status(PlayerProgressSaveStatus.FAILED,
                    pending.record(), pending.requestId(), null,
                    exception.getClass().getSimpleName());
        }
        synchronized (gate) {
            pendingByRequest.remove(key);
            AcceptedWrite accepted = latestAcceptedByPlayer.get(key.playerId());
            if (accepted != null
                    && accepted.requestId().equals(pending.requestId())) {
                latestAcceptedByPlayer.remove(key.playerId());
            }
            rememberCompleted(key, pending.record(), result);
        }
        pending.future().complete(result);
    }

    private PlayerProgressSaveResult commit(
            PlayerProgressRecordV1 record,
            UUID requestId
    ) throws IOException, InvalidConfigurationException {
        UUID playerId = record.snapshot().playerId();
        long revision = record.snapshot().revision();
        if (newerAccepted(playerId, revision)) {
            return status(PlayerProgressSaveStatus.STALE,
                    record, requestId, null, "superseded before serialization");
        }
        Files.createDirectories(playersDirectory);
        Files.createDirectories(backupDirectory);
        Path target = playerPath(playerId);
        if (Files.exists(target)) {
            String currentYaml;
            PlayerProgressYamlCodec.Header currentHeader;
            PlayerProgressRecordV1 current;
            try {
                currentYaml = readUtf8(target);
                currentHeader = codec.inspectHeader(currentYaml);
                if (!PlayerProgressRecordV1.SCHEMA_ID.equals(currentHeader.schemaId())
                        || currentHeader.schemaVersion()
                        != PlayerProgressRecordV1.SCHEMA_VERSION) {
                    quarantineCopy(target, playerId, "unknown-write");
                    return status(PlayerProgressSaveStatus.FAILED,
                            record, requestId, null,
                            "existing record has unsupported version");
                }
                current = codec.decode(currentYaml);
                if (!current.snapshot().playerId().equals(playerId)) {
                    throw new IllegalArgumentException(
                            "existing record UUID does not match filename");
                }
            } catch (IOException | InvalidConfigurationException
                     | IllegalArgumentException exception) {
                quarantineCopy(target, playerId, "corrupt-write");
                return status(PlayerProgressSaveStatus.FAILED,
                        record, requestId, null,
                        "existing record is corrupt");
            }
            long currentRevision = current.snapshot().revision();
            if (currentRevision > revision) {
                return status(PlayerProgressSaveStatus.STALE,
                        record, requestId, target,
                        "disk revision is newer");
            }
            if (currentRevision == revision) {
                return current.equals(record)
                        ? idempotent(record, requestId, target)
                        : status(PlayerProgressSaveStatus.CONFLICT,
                        record, requestId, target,
                        "disk revision has different data");
            }
        }
        byte[] bytes = codec.encode(record).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) {
            return status(PlayerProgressSaveStatus.FAILED,
                    record, requestId, null,
                    "encoded player-data exceeds size limit");
        }
        Path temporary = playersDirectory.resolve(
                "." + playerId + "-r" + revision + "-" + requestId + ".tmp");
        Files.deleteIfExists(temporary);
        try {
            fileOperations.writeAndFlush(temporary, bytes);
            synchronized (gate) {
                if (newerAcceptedLocked(playerId, revision)) {
                    return status(PlayerProgressSaveStatus.STALE,
                            record, requestId, null,
                            "superseded before atomic replacement");
                }
                if (Files.exists(target)) {
                    fileOperations.copyPrevious(
                            target, backupDirectory.resolve(
                                    playerId + ".previous.yml"));
                }
                fileOperations.atomicReplace(temporary, target);
            }
            return status(PlayerProgressSaveStatus.COMMITTED,
                    record, requestId, target, "");
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private boolean newerAccepted(UUID playerId, long revision) {
        synchronized (gate) {
            return newerAcceptedLocked(playerId, revision);
        }
    }

    private boolean newerAcceptedLocked(UUID playerId, long revision) {
        AcceptedWrite accepted = latestAcceptedByPlayer.get(playerId);
        return accepted != null && accepted.revision() > revision;
    }

    private Path quarantineCopy(Path source, UUID playerId, String reason)
            throws IOException {
        Files.createDirectories(quarantineDirectory);
        pruneQuarantine();
        Path target = quarantineDirectory.resolve(
                playerId + "-" + reason + "-" + System.nanoTime() + ".yml");
        return Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private void pruneQuarantine() throws IOException {
        if (!Files.exists(quarantineDirectory)) return;
        try (var paths = Files.list(quarantineDirectory)) {
            var ordered = paths.filter(Files::isRegularFile)
                    .sorted((left, right) -> {
                        try {
                            return Files.getLastModifiedTime(left).compareTo(
                                    Files.getLastModifiedTime(right));
                        } catch (IOException exception) {
                            return left.toString().compareTo(right.toString());
                        }
                    }).toList();
            int remove = Math.max(0, ordered.size() - MAX_QUARANTINE_FILES + 1);
            for (int index = 0; index < remove; index++) {
                Files.deleteIfExists(ordered.get(index));
            }
        }
    }

    private String readUtf8(Path source) throws IOException {
        long size = Files.size(source);
        if (size < 0 || size > MAX_FILE_BYTES) {
            throw new IOException("player-data file exceeds size limit");
        }
        byte[] bytes = Files.readAllBytes(source);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("player-data file is not valid UTF-8", exception);
        }
    }

    private Path playerPath(UUID playerId) {
        return playersDirectory.resolve(playerId + ".yml");
    }

    private void rememberCompleted(
            RequestKey key,
            PlayerProgressRecordV1 record,
            PlayerProgressSaveResult result
    ) {
        completedRequests.put(key, new CompletedWrite(record, result));
        while (completedRequests.size() > MAX_COMPLETED_REQUESTS) {
            Iterator<RequestKey> iterator = completedRequests.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private static CompletableFuture<PlayerProgressSaveResult> completed(
            PlayerProgressSaveResult result
    ) {
        return CompletableFuture.completedFuture(result);
    }

    private static PlayerProgressSaveResult idempotent(
            PlayerProgressRecordV1 record,
            UUID requestId,
            Path path
    ) {
        return status(PlayerProgressSaveStatus.IDEMPOTENT,
                record, requestId, path, "duplicate save is already committed");
    }

    private static PlayerProgressSaveResult status(
            PlayerProgressSaveStatus status,
            PlayerProgressRecordV1 record,
            UUID requestId,
            Path path,
            String detail
    ) {
        return new PlayerProgressSaveResult(
                status, record.snapshot().playerId(),
                record.snapshot().revision(), requestId, path, detail);
    }

    private static PlayerProgressLoadResult loadResult(
            PlayerProgressLoadStatus status,
            PlayerProgressRecordV1 record,
            Path quarantine,
            String detail
    ) {
        return new PlayerProgressLoadResult(status, record, quarantine, detail);
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger number = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(
                    runnable, "projects-player-save-" + number.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private record RequestKey(UUID playerId, UUID requestId) {
    }

    private record PendingWrite(
            PlayerProgressRecordV1 record,
            UUID requestId,
            CompletableFuture<PlayerProgressSaveResult> future
    ) {
    }

    private record AcceptedWrite(
            long revision,
            PlayerProgressRecordV1 record,
            CompletableFuture<PlayerProgressSaveResult> future,
            UUID requestId
    ) {
    }

    private record CompletedWrite(
            PlayerProgressRecordV1 record,
            PlayerProgressSaveResult result
    ) {
    }
}
