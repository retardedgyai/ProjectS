package io.github.gyai.projects.beta.activation.track1.player;

import io.github.gyai.projects.persistence.player.FilePlayerProgressRepository;
import io.github.gyai.projects.persistence.player.PlayerProgressSaveResult;
import io.github.gyai.projects.persistence.player.PlayerProgressRepository;
import io.github.gyai.projects.persistence.player.StagingPlayerProgressRepositoryFactory;
import io.github.gyai.projects.persistence.player.StagingPlayerProgressYamlReader;
import io.github.gyai.projects.player.progress.PlayerProgressRecordV1;
import io.github.gyai.projects.player.progress.PlayerProgressSnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Staging-only store. READ operations never create directories or quarantine copies. */
public final class StagingPlayerProgressFileStore implements StagingPlayerProgressStore {
    private final Path playersDirectory;
    private final StagingPlayerProgressYamlReader reader;
    private final Set<String> settingWhitelist;
    private PlayerProgressRepository writeRepository;
    private boolean closed;

    public StagingPlayerProgressFileStore(Path playersDirectory, Set<String> settingWhitelist) {
        if (playersDirectory == null) throw new IllegalArgumentException("players path is required");
        this.playersDirectory = playersDirectory.toAbsolutePath().normalize();
        requireStagingPlayersPath(this.playersDirectory);
        this.settingWhitelist = Set.copyOf(
                settingWhitelist == null ? Set.of() : settingWhitelist);
        reader = new StagingPlayerProgressYamlReader(this.settingWhitelist);
    }

    @Override
    public synchronized Load load(UUID playerId) {
        if (closed) return new Load(Load.Status.CLOSED, java.util.Optional.empty(), "store is closed");
        if (playerId == null) throw new IllegalArgumentException("playerId is required");
        Path source = playersDirectory.resolve(playerId + ".yml");
        if (!Files.isRegularFile(source)) {
            return new Load(Load.Status.MISSING, java.util.Optional.empty(), "");
        }
        try {
            if (Files.size(source) > FilePlayerProgressRepository.MAX_FILE_BYTES) {
                return malformed("staging record exceeds size limit");
            }
            String yaml = Files.readString(source, StandardCharsets.UTF_8);
            StagingPlayerProgressYamlReader.Result decoded = reader.decode(yaml);
            if (decoded.status() != StagingPlayerProgressYamlReader.Status.LOADED) {
                return malformed(decoded.detail());
            }
            PlayerProgressSnapshot snapshot = decoded.snapshot().orElseThrow();
            if (!snapshot.playerId().equals(playerId)) {
                return malformed("player UUID mismatch");
            }
            return new Load(Load.Status.LOADED,
                    java.util.Optional.of(snapshot), "");
        } catch (IOException | RuntimeException exception) {
            return malformed("malformed staging record: " + exception.getClass().getSimpleName());
        }
    }

    @Override
    public synchronized CompletionStage<PlayerProgressSaveObservation> save(
            PlayerProgressSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot is required");
        if (closed) return java.util.concurrent.CompletableFuture.completedFuture(
                result(snapshot, PlayerProgressSaveObservation.Status.CLOSED,
                        null, "store is closed"));
        if (writeRepository == null) {
            writeRepository = StagingPlayerProgressRepositoryFactory.create(
                    playersDirectory, settingWhitelist);
        }
        UUID requestId = UUID.nameUUIDFromBytes((snapshot.playerId() + ":" + snapshot.revision())
                .getBytes(StandardCharsets.UTF_8));
        return writeRepository.save(new PlayerProgressRecordV1(snapshot), requestId)
                .thenApply(value -> map(snapshot, value));
    }

    private PlayerProgressSaveObservation map(
            PlayerProgressSnapshot snapshot, PlayerProgressSaveResult value) {
        PlayerProgressSaveObservation.Status status = switch (value.status()) {
            case COMMITTED, IDEMPOTENT -> PlayerProgressSaveObservation.Status.COMMITTED;
            case STALE, CONFLICT -> PlayerProgressSaveObservation.Status.STALE;
            case CLOSED -> PlayerProgressSaveObservation.Status.CLOSED;
            case QUEUE_FULL, FAILED -> PlayerProgressSaveObservation.Status.FAILED;
        };
        return result(snapshot, status, value.committedPath(), value.detail());
    }

    private static PlayerProgressSaveObservation result(
            PlayerProgressSnapshot snapshot,
            PlayerProgressSaveObservation.Status status,
            Path path,
            String detail) {
        return new PlayerProgressSaveObservation(snapshot.playerId(), status,
                snapshot.revision(), java.util.Optional.ofNullable(path), detail);
    }

    private Load malformed(String detail) {
        return new Load(Load.Status.MALFORMED, java.util.Optional.empty(), detail);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (writeRepository != null) writeRepository.close();
    }

    public Path playersDirectory() {
        return playersDirectory;
    }

    private static void requireStagingPlayersPath(Path path) {
        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!normalized.endsWith("/beta-staging/players")) {
            throw new IllegalArgumentException("path must end in beta-staging/players");
        }
    }
}
