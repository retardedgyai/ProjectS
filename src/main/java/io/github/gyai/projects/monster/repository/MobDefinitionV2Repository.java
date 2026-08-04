package io.github.gyai.projects.monster.repository;

import io.github.gyai.projects.monster.definition.v2.MobDefinitionV2;
import io.github.gyai.projects.monster.definition.v2.MobDefinitionV2Policy;
import io.github.gyai.projects.monster.definition.v2.MobDefinitionV2Validator;
import io.github.gyai.projects.monster.definition.v2.MobDefinitionValidation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Revisioned v1/v2 reader and v2-only writer. All methods are pure file I/O. */
public final class MobDefinitionV2Repository implements AutoCloseable {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private final Path root;
    private final Path historyRoot;
    private final Path quarantineRoot;
    private final MobDefinitionV2Codec codec;
    private final MobDefinitionV2Validator validator;
    private final MobDefinitionV2Policy policy;
    private final AtomicWriter atomicWriter;
    private final Map<String, Long> lastKnownGood = new HashMap<>();
    private final Map<String, Set<Long>> referencedRollbackTargets = new HashMap<>();
    private boolean closed;

    public MobDefinitionV2Repository(
            Path root,
            MobDefinitionV2Codec codec,
            MobDefinitionV2Validator validator,
            MobDefinitionV2Policy policy
    ) {
        this(root, codec, validator, policy, strictAtomicWriter(
                (temporary, target) -> Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING)));
    }

    public MobDefinitionV2Repository(
            Path root,
            MobDefinitionV2Codec codec,
            MobDefinitionV2Validator validator,
            MobDefinitionV2Policy policy,
            AtomicWriter atomicWriter
    ) {
        this.root = absolute(root);
        this.historyRoot = this.root.resolve(".history");
        this.quarantineRoot = this.root.resolve(".quarantine");
        this.codec = java.util.Objects.requireNonNull(codec, "codec");
        this.validator = java.util.Objects.requireNonNull(validator, "validator");
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
        this.atomicWriter = java.util.Objects.requireNonNull(atomicWriter, "atomicWriter");
    }

    public synchronized ReadResult read(String mobId) {
        try {
            ensureOpen();
            Path target = definitionPath(mobId);
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return ReadResult.notFound();
            rejectUnsafeFile(target);
            long size = Files.size(target);
            if (size > policy.maximumFileBytes()) return quarantine(target, ReadStatus.OVERSIZED, "file oversized");
            byte[] bytes = Files.readAllBytes(target);
            int schema = MobDefinitionV2Codec.inspectSchemaVersion(bytes);
            if (schema == 1) {
                var header = MobDefinitionV2Codec.inspectLegacyHeader(bytes);
                if (!header.mobId().equals(mobId)) return quarantine(target, ReadStatus.CORRUPT, "file ID mismatch");
                return ReadResult.v1(header, bytes);
            }
            if (schema != MobDefinitionV2.SCHEMA_VERSION) {
                return quarantine(target, ReadStatus.UNKNOWN_VERSION, "unknown schema " + schema);
            }
            MobDefinitionV2 definition = codec.decode(bytes);
            if (!definition.mobId().equals(mobId)) return quarantine(target, ReadStatus.CORRUPT, "file ID mismatch");
            MobDefinitionValidation validation = validator.validate(definition);
            if (!validation.valid()) return new ReadResult(ReadStatus.INVALID, schema, mobId,
                    definition.revision(), definition, bytes, null, validation.details().toString());
            return ReadResult.v2(definition, bytes);
        } catch (UnsafePathException exception) {
            return new ReadResult(ReadStatus.UNSAFE_PATH, 0, safe(mobId), 0,
                    null, new byte[0], null, exception.getMessage());
        } catch (IOException | RuntimeException exception) {
            Path target = safePath(mobId);
            if (target != null && Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                return quarantine(target, ReadStatus.CORRUPT, exception.getMessage());
            }
            return new ReadResult(ReadStatus.CORRUPT, 0, safe(mobId), 0,
                    null, new byte[0], null, bounded(exception.getMessage()));
        }
    }

    public synchronized SaveResult save(MobDefinitionV2 draft, long expectedRevision) {
        try {
            ensureOpen();
            MobDefinitionValidation validation = validator.validate(draft);
            if (!validation.valid()) return SaveResult.rejected(validation);
            Path target = definitionPath(draft.mobId());
            ReadResult current = read(draft.mobId());
            if (current.status() != ReadStatus.NOT_FOUND
                    && current.status() != ReadStatus.V1
                    && current.status() != ReadStatus.V2) {
                return SaveResult.failure("current definition is not safely writable");
            }
            long currentRevision = current.status() == ReadStatus.NOT_FOUND ? 0 : current.revision();
            if (current.status() == ReadStatus.NOT_FOUND
                    && currentDefinitionCount() >= policy.maximumDefinitions()) {
                return SaveResult.failure("definition capacity reached");
            }
            if (currentRevision != expectedRevision) {
                return SaveResult.conflict(current.definition(), currentRevision);
            }
            if (current.status() == ReadStatus.V2 && draft.schemaVersion() < current.schemaVersion()) {
                return SaveResult.failure("downgrade is forbidden");
            }
            long nextRevision = Math.addExact(currentRevision, 1);
            MobDefinitionV2 saved = draft.withRevision(nextRevision);
            byte[] encoded = codec.encode(saved);
            if (current.status() != ReadStatus.NOT_FOUND) backupCurrent(draft.mobId(), current);
            atomicWriter.write(target, encoded);
            writeHistory(saved, encoded);
            pruneHistory(saved.mobId(), saved.revision());
            return SaveResult.success(saved);
        } catch (IOException | ArithmeticException | IllegalStateException
                 | UnsafePathException exception) {
            return SaveResult.failure(bounded(exception.getMessage()));
        }
    }

    public synchronized UpgradeProposal proposeUpgrade(String mobId, MobDefinitionV2 candidate) {
        ReadResult current = read(mobId);
        if (current.status() != ReadStatus.V1 || candidate == null
                || !mobId.equals(candidate.mobId())) return null;
        MobDefinitionValidation validation = validator.validate(candidate);
        if (!validation.valid()) return null;
        return new UpgradeProposal(current.legacyHeader(), candidate,
                sha256(current.originalBytes()));
    }

    public synchronized SaveResult commitUpgrade(UpgradeProposal proposal, boolean confirmed) {
        if (!confirmed || proposal == null) return SaveResult.failure("explicit confirmation required");
        ReadResult current = read(proposal.header().mobId());
        if (current.status() != ReadStatus.V1
                || !MessageDigest.isEqual(proposal.sourceSha256(), sha256(current.originalBytes()))) {
            return SaveResult.conflict(current.definition(), current.revision());
        }
        return save(proposal.candidate(), current.revision());
    }

    public synchronized SaveResult rollback(String mobId, long selectedRevision,
                                            long expectedCurrentRevision) {
        try {
            ensureOpen();
            Path history = historyPath(mobId, selectedRevision);
            rejectUnsafeFile(history);
            MobDefinitionV2 selected = codec.decode(Files.readAllBytes(history));
            referencedRollbackTargets.computeIfAbsent(mobId, key -> new HashSet<>())
                    .add(selectedRevision);
            return save(selected, expectedCurrentRevision);
        } catch (IOException | RuntimeException exception) {
            return SaveResult.failure(bounded(exception.getMessage()));
        } finally {
            Set<Long> targets = referencedRollbackTargets.get(mobId);
            if (targets != null) targets.remove(selectedRevision);
        }
    }

    public synchronized void markLastKnownGood(String mobId, long revision) {
        ensureOpen(); id(mobId); if (revision < 1) throw new IllegalArgumentException("revision");
        lastKnownGood.put(mobId, revision);
    }

    public synchronized List<Long> history(String mobId) {
        try {
            ensureOpen();
            Path directory = historyDirectory(mobId);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
            try (var paths = Files.list(directory)) {
                return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                                && path.getFileName().toString().endsWith(".yml")
                                && !path.getFileName().toString().endsWith(".source.yml"))
                        .map(path -> revisionFromName(path.getFileName().toString()))
                        .filter(value -> value > 0).sorted().toList();
            }
        } catch (IOException | RuntimeException exception) {
            return List.of();
        }
    }

    public synchronized List<ReadResult> list() {
        try {
            ensureOpen(); Files.createDirectories(root);
            try (var paths = Files.list(root)) {
                List<String> ids = paths.filter(path -> path.getFileName().toString().endsWith(".yml"))
                        .limit((long) policy.maximumDefinitions() + 1)
                        .map(path -> path.getFileName().toString().replaceFirst("\\.yml$", ""))
                        .sorted().toList();
                if (ids.size() > policy.maximumDefinitions()) return List.of();
                return ids.stream().map(this::read).toList();
            }
        } catch (IOException | RuntimeException exception) { return List.of(); }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true; lastKnownGood.clear(); referencedRollbackTargets.clear();
    }

    private ReadResult quarantine(Path target, ReadStatus status, String message) {
        try {
            rejectSymlinkDirectoryChain(quarantineRoot);
            Files.createDirectories(quarantineRoot);
            Path destination = quarantineRoot.resolve(target.getFileName().toString()
                    + "." + System.nanoTime() + ".quarantine");
            try { Files.move(target, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException exception) {
                Files.move(target, destination);
            }
            return new ReadResult(status, 0, fileId(target), 0, null,
                    new byte[0], destination, bounded(message));
        } catch (IOException exception) {
            return new ReadResult(status, 0, fileId(target), 0, null,
                    new byte[0], null, bounded(message));
        }
    }

    private void backupCurrent(String mobId, ReadResult current) throws IOException {
        Path backup = current.status() == ReadStatus.V1
                ? historyDirectory(mobId).resolve("legacy-v1-backup.yml")
                : historyPath(mobId, current.revision());
        if (!Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            strictAtomicWriter((temporary, target) -> Files.move(temporary, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING))
                    .write(backup, current.originalBytes());
        }
    }

    private void writeHistory(MobDefinitionV2 definition, byte[] bytes) throws IOException {
        strictAtomicWriter((temporary, target) -> Files.move(temporary, target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING))
                .write(historyPath(definition.mobId(), definition.revision()), bytes);
    }

    private void pruneHistory(String mobId, long currentRevision) throws IOException {
        Path directory = historyDirectory(mobId);
        if (!Files.exists(directory)) return;
        Set<Long> protectedRevisions = new HashSet<>();
        protectedRevisions.add(currentRevision);
        Optional.ofNullable(lastKnownGood.get(mobId)).ifPresent(protectedRevisions::add);
        protectedRevisions.addAll(referencedRollbackTargets.getOrDefault(mobId, Set.of()));
        List<Path> commits;
        try (var paths = Files.list(directory)) {
            commits = paths.filter(path -> path.getFileName().toString().endsWith(".yml")
                            && !path.getFileName().toString().endsWith(".source.yml"))
                    .sorted(Comparator.comparingLong(path ->
                            revisionFromName(path.getFileName().toString())))
                    .toList();
        }
        int removable = Math.max(0, commits.size() - policy.maximumHistory());
        for (Path path : commits) {
            if (removable == 0) break;
            long revision = revisionFromName(path.getFileName().toString());
            if (!protectedRevisions.contains(revision)) {
                Files.deleteIfExists(path); removable--;
            }
        }
    }

    private long currentDefinitionCount() throws IOException {
        Files.createDirectories(root);
        try (var paths = Files.list(root)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .limit((long) policy.maximumDefinitions() + 1).count();
        }
    }

    private Path definitionPath(String mobId) {
        rejectSymlinkDirectoryChain(root);
        return contained(root.resolve(id(mobId) + ".yml"), root);
    }
    private Path historyDirectory(String mobId) {
        rejectSymlinkDirectoryChain(historyRoot);
        Path directory = contained(historyRoot.resolve(id(mobId)), historyRoot);
        rejectSymlinkDirectoryChain(directory);
        return directory;
    }
    private Path historyPath(String mobId, long revision) {
        if (revision < 1) throw new UnsafePathException("invalid history revision");
        return contained(historyDirectory(mobId).resolve(revision + ".yml"), historyRoot);
    }

    private static Path contained(Path candidate, Path parent) {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(parent.toAbsolutePath().normalize())) throw new UnsafePathException("path traversal rejected");
        return normalized;
    }

    private static String id(String value) {
        if (value == null || !ID.matcher(value).matches() || value.contains("..")) throw new UnsafePathException("invalid mob ID");
        return value;
    }

    private static void rejectUnsafeFile(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new UnsafePathException("symbolic link rejected");
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new UnsafePathException("non-regular file rejected");
    }

    public static AtomicWriter strictAtomicWriter(AtomicMover mover) {
        java.util.Objects.requireNonNull(mover, "mover");
        return (target, bytes) -> writeAtomic(target, bytes, mover);
    }

    private static void writeAtomic(Path target, byte[] bytes, AtomicMover mover)
            throws IOException {
        rejectSymlinkDirectoryChain(target.toAbsolutePath().normalize().getParent());
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            rejectSymlinkDirectoryChain(target.toAbsolutePath().normalize().getParent());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                    && (Files.isSymbolicLink(target)
                    || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))) {
                throw new IOException("atomic target is not a regular file");
            }
            mover.move(temporary, target);
            moved = true;
        } finally { if (!moved) Files.deleteIfExists(temporary); }
    }

    private static void rejectSymlinkDirectoryChain(Path directory) {
        Path absolute = directory.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        if (current == null) throw new UnsafePathException("path has no root");
        for (Path component : absolute) {
            current = current.resolve(component);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) continue;
            if (Files.isSymbolicLink(current)) {
                throw new UnsafePathException("symbolic-link ancestor rejected: "
                        + current.getFileName());
            }
            if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new UnsafePathException("non-directory ancestor rejected: "
                        + current.getFileName());
            }
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static Path absolute(Path path) { return java.util.Objects.requireNonNull(path, "root").toAbsolutePath().normalize(); }
    private void ensureOpen() {
        if (closed) throw new IllegalStateException("repository closed");
        rejectSymlinkDirectoryChain(root);
    }
    private Path safePath(String mobId) { try { return definitionPath(mobId); } catch (RuntimeException e) { return null; } }
    private static String safe(String value) { return value == null ? "" : bounded(value); }
    private static String fileId(Path path) { return path.getFileName().toString().replaceFirst("\\.yml$", ""); }
    private static String bounded(String value) { String text = value == null ? "I/O failure" : value; return text.length() <= 256 ? text : text.substring(0, 255) + "…"; }
    private static long revisionFromName(String value) { try { return Long.parseLong(value.replaceFirst("\\..*$", "")); } catch (NumberFormatException e) { return -1; } }

    @FunctionalInterface public interface AtomicWriter { void write(Path target, byte[] contents) throws IOException; }
    @FunctionalInterface public interface AtomicMover { void move(Path temporary, Path target) throws IOException; }

    public enum ReadStatus {
        V1, V2, NOT_FOUND, INVALID, UNKNOWN_VERSION, CORRUPT, OVERSIZED, UNSAFE_PATH;
        public boolean quarantined() { return this == UNKNOWN_VERSION || this == CORRUPT || this == OVERSIZED; }
    }

    public record ReadResult(ReadStatus status, int schemaVersion, String mobId,
                             long revision, MobDefinitionV2 definition,
                             byte[] originalBytes, Path quarantinePath, String message) {
        public ReadResult { originalBytes = originalBytes == null ? new byte[0] : originalBytes.clone(); }
        @Override public byte[] originalBytes() { return originalBytes.clone(); }
        static ReadResult notFound() { return new ReadResult(ReadStatus.NOT_FOUND, 0, "", 0, null, new byte[0], null, "not found"); }
        static ReadResult v1(MobDefinitionV2Codec.LegacyHeader header, byte[] bytes) { return new ReadResult(ReadStatus.V1, 1, header.mobId(), header.revision(), null, bytes, null, "legacy v1 read-only"); }
        static ReadResult v2(MobDefinitionV2 value, byte[] bytes) { return new ReadResult(ReadStatus.V2, 2, value.mobId(), value.revision(), value, bytes, null, "valid"); }
        public MobDefinitionV2Codec.LegacyHeader legacyHeader() { return status == ReadStatus.V1 ? new MobDefinitionV2Codec.LegacyHeader(1, mobId, revision) : null; }
    }

    public record SaveResult(boolean success, boolean conflict, String message,
                             MobDefinitionV2 saved, MobDefinitionV2 conflictSnapshot,
                             long currentRevision, MobDefinitionValidation validation) {
        static SaveResult success(MobDefinitionV2 value) { return new SaveResult(true, false, "saved", value, null, value.revision(), null); }
        static SaveResult conflict(MobDefinitionV2 value, long revision) { return new SaveResult(false, true, "revision conflict", null, value, revision, new MobDefinitionValidation(MobDefinitionValidation.Status.CONFLICT, List.of("stale revision"))); }
        static SaveResult rejected(MobDefinitionValidation validation) { return new SaveResult(false, false, "validation rejected", null, null, 0, validation); }
        static SaveResult failure(String message) { return new SaveResult(false, false, bounded(message), null, null, 0, null); }
    }

    public record UpgradeProposal(MobDefinitionV2Codec.LegacyHeader header,
                                  MobDefinitionV2 candidate, byte[] sourceSha256) {
        public UpgradeProposal { sourceSha256 = sourceSha256.clone(); }
        @Override public byte[] sourceSha256() { return sourceSha256.clone(); }
    }

    private static final class UnsafePathException extends RuntimeException { UnsafePathException(String message) { super(message); } }
}
