package io.github.gyai.projects.content.persistence;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Package-private current/history file mechanics for typed content facades.
 *
 * <p>The facade supplies the kind-specific layout and typed codec. This class
 * owns only bounded reads, no-follow path checks, root/JVM/OS locking, exact
 * history preservation, and atomic file replacement.</p>
 */
final class RevisionedFileStore<T> implements AutoCloseable {
    static final String JSON_SUFFIX = ".json";

    private static final String HISTORY_DIRECTORY = ".history";
    private static final long LOCK_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(500);
    private static final long LOCK_POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(5);
    private static final ConcurrentMap<Path, ReentrantLock> ROOT_LOCKS =
            new ConcurrentHashMap<>();

    private static final FileAttribute<Set<PosixFilePermission>> PRIVATE_DIRECTORY_ATTRIBUTE =
            PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwx------"));
    private static final FileAttribute<Set<PosixFilePermission>> PRIVATE_FILE_ATTRIBUTE =
            PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rw-------"));

    private final Path root;
    private final Layout layout;
    private final Codec<T> codec;
    private final int maxDocumentBytes;
    private final AtomicFileCommitter committer;
    private boolean closed;

    RevisionedFileStore(Path root, Layout layout, Codec<T> codec, int maxDocumentBytes,
                        AtomicFileCommitter committer) {
        if (root == null) throw new IllegalArgumentException("root is required");
        if (maxDocumentBytes < 1) throw new IllegalArgumentException("document bound is required");
        this.root = root.toAbsolutePath().normalize();
        this.layout = Objects.requireNonNull(layout, "layout");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.maxDocumentBytes = maxDocumentBytes;
        this.committer = Objects.requireNonNull(committer, "committer");
    }

    ReadResult<T> readCurrent(String id) throws IOException {
        ensureOpen();
        Path target = currentPath(id);
        if (Files.isSymbolicLink(target)) {
            throw failure("UNSAFE_PATH", target, "symbolic-link target is rejected");
        }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return ReadResult.notFound(id, target);
        }
        rejectRegularTarget(target);
        byte[] bytes = readBytes(target);
        Decoded<T> decoded = codec.decode(bytes);
        if (!decoded.success()) {
            return ReadResult.invalid(id, target, bytes, decoded.error());
        }
        T definition = Objects.requireNonNull(decoded.value(), "decoded value");
        if (!id.equals(codec.id(definition))) {
            return ReadResult.invalid(id, target, bytes,
                    new StorageError("INVALID_VALUE", "$.id",
                            "document ID does not match requested ID"));
        }
        return ReadResult.loaded(id, target, bytes, definition);
    }

    Historical<T> readHistory(String id, long revision) throws IOException {
        Path path = historyPath(id, revision);
        if (Files.isSymbolicLink(path)) {
            throw failure("UNSAFE_PATH", path, "symbolic-link history file is rejected");
        }
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw failure("HISTORY_NOT_FOUND", path, "historical revision does not exist");
        }
        rejectRegularTarget(path);
        byte[] bytes = readBytes(path);
        Decoded<T> decoded = codec.decode(bytes);
        if (!decoded.success()) {
            StorageError error = decoded.error();
            throw failure(error.code(), path, error.detail());
        }
        T definition = Objects.requireNonNull(decoded.value(), "decoded value");
        if (!id.equals(codec.id(definition))) {
            throw failure("HISTORY_ID_MISMATCH", path,
                    "historical document ID does not match its path");
        }
        if (codec.revision(definition) != revision) {
            throw failure("HISTORY_REVISION_MISMATCH", path,
                    "historical document revision does not match its filename");
        }
        return new Historical<>(definition, bytes);
    }

    void preserveHistory(String id, long revision, byte[] bytes) throws IOException {
        Path history = historyPath(id, revision);
        if (Files.isSymbolicLink(history)) {
            throw failure("UNSAFE_PATH", history, "symbolic-link history file is rejected");
        }
        if (Files.exists(history, LinkOption.NOFOLLOW_LINKS)) {
            rejectRegularTarget(history);
            byte[] existing = readBytes(history);
            if (!Arrays.equals(existing, bytes)) {
                throw failure("HISTORY_COLLISION", history,
                        "history revision already contains different bytes");
            }
            return;
        }
        commit(history, bytes.clone(), false);
    }

    List<Long> history(String id) {
        try {
            ensureOpen();
            Path directory = historyPath(id, 1).getParent();
            rejectDirectoryChain(directory);
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
            try (Stream<Path> paths = Files.list(directory)) {
                return paths.filter(path -> path.getFileName().toString().endsWith(JSON_SUFFIX))
                        .filter(path -> !Files.isSymbolicLink(path)
                                && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .map(path -> revisionFromFileName(path.getFileName().toString()))
                        .filter(value -> value > 0)
                        .sorted()
                        .toList();
            }
        } catch (IOException | RuntimeException exception) {
            return List.of();
        }
    }

    List<Path> currentJsonCandidates() throws IOException {
        ensureOpen();
        Path current = currentDirectory();
        rejectDirectoryChain(current);
        if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
            throw failure("UNSAFE_PATH", current,
                    layout.kindName() + " path is not a directory");
        }
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(current)) {
            paths.filter(path -> path.getFileName() != null
                            && path.getFileName().toString().endsWith(JSON_SUFFIX))
                    .forEach(candidates::add);
        }
        return List.copyOf(candidates);
    }

    String idFromCurrentPath(Path candidate) {
        ensureOpen();
        return layout.idFromCurrentPath(root, currentDirectory(), candidate);
    }

    Path currentDirectory() {
        return layout.currentDirectory(root).toAbsolutePath().normalize();
    }

    Path currentPath(String id) {
        Path target = layout.currentPath(root, id).toAbsolutePath().normalize();
        Path current = currentDirectory();
        rejectDirectoryChain(root);
        rejectDirectoryChain(current);
        if (!target.startsWith(current)) {
            throw failure("UNSAFE_PATH", target,
                    "resolved path escapes " + layout.kindName() + " directory");
        }
        rejectDirectoryChain(target.getParent());
        return target;
    }

    Path historyPath(String id, long revision) {
        if (revision < 1) {
            throw failure("INVALID_VALUE", "$.revision", "history revision must be positive");
        }
        Path target = layout.historyPath(root, id, revision).toAbsolutePath().normalize();
        Path historyRoot = layout.historyDirectory(root).toAbsolutePath().normalize();
        rejectDirectoryChain(root);
        rejectDirectoryChain(root.resolve(HISTORY_DIRECTORY));
        rejectDirectoryChain(historyRoot);
        if (!target.startsWith(historyRoot)) {
            throw failure("UNSAFE_PATH", target, "resolved history path escapes history directory");
        }
        rejectDirectoryChain(target.getParent());
        return target;
    }

    MutationLock acquireMutationLock() throws IOException {
        ensureOpen();
        ReentrantLock jvmLock = ROOT_LOCKS.computeIfAbsent(root,
                ignored -> new ReentrantLock(true));
        boolean jvmAcquired = false;
        FileChannel channel = null;
        try {
            if (!tryAcquireJvmLock(jvmLock)) {
                throw lockUnavailable(lockPath(), "root mutation lock is busy");
            }
            jvmAcquired = true;
            Path lockPath = prepareLockPath();
            channel = openLockChannel(lockPath);
            rejectDirectoryChain(root);
            rejectLockFile(lockPath);
            FileLock fileLock = acquireFileLock(channel, lockPath);
            return new MutationLock(jvmLock, channel, fileLock);
        } catch (IOException | RuntimeException exception) {
            closeLockChannel(channel);
            if (jvmAcquired) jvmLock.unlock();
            throw exception;
        }
    }

    void commit(Path target, byte[] bytes, boolean replaceExisting) throws IOException {
        committer.commit(target, bytes, replaceExisting);
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    private Path lockPath() {
        return layout.lockPath(root).toAbsolutePath().normalize();
    }

    private boolean tryAcquireJvmLock(ReentrantLock jvmLock) {
        try {
            return jvmLock.tryLock(LOCK_TIMEOUT_NANOS, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw lockUnavailable(lockPath(), "root mutation lock acquisition was interrupted");
        }
    }

    private Path prepareLockPath() throws IOException {
        rejectDirectoryChain(root);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            createDirectories(root);
        }
        rejectDirectoryChain(root);
        Path lockPath = lockPath();
        if (!lockPath.startsWith(root)) {
            throw failure("UNSAFE_PATH", lockPath, "lock path escapes repository root");
        }
        rejectDirectoryChain(lockPath.getParent());
        return lockPath;
    }

    private static FileChannel openLockChannel(Path lockPath) throws IOException {
        rejectLockFile(lockPath);
        if (Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS)) {
            return openExistingLockChannel(lockPath);
        }

        Set<OpenOption> options = new HashSet<>();
        options.add(StandardOpenOption.READ);
        options.add(StandardOpenOption.WRITE);
        options.add(StandardOpenOption.CREATE_NEW);
        options.add(LinkOption.NOFOLLOW_LINKS);
        try {
            FileChannel channel;
            try {
                channel = FileChannel.open(lockPath, options, PRIVATE_FILE_ATTRIBUTE);
            } catch (UnsupportedOperationException exception) {
                channel = FileChannel.open(lockPath, options);
            }
            try {
                rejectLockFile(lockPath);
                return channel;
            } catch (RuntimeException exception) {
                closeLockChannel(channel);
                throw exception;
            }
        } catch (FileAlreadyExistsException exception) {
            rejectLockFile(lockPath);
            return openExistingLockChannel(lockPath);
        }
    }

    private static FileChannel openExistingLockChannel(Path lockPath) throws IOException {
        rejectLockFile(lockPath);
        Set<OpenOption> options = new HashSet<>();
        options.add(StandardOpenOption.READ);
        options.add(StandardOpenOption.WRITE);
        options.add(LinkOption.NOFOLLOW_LINKS);
        FileChannel channel = FileChannel.open(lockPath, options);
        try {
            rejectLockFile(lockPath);
            return channel;
        } catch (RuntimeException exception) {
            closeLockChannel(channel);
            throw exception;
        }
    }

    private static void rejectLockFile(Path lockPath) {
        if (Files.isSymbolicLink(lockPath)) {
            throw failure("UNSAFE_PATH", lockPath, "symbolic-link lock file is rejected");
        }
        if (!Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)) {
            throw failure("UNSAFE_PATH", lockPath, "lock path is not a regular file");
        }
    }

    private static FileLock acquireFileLock(FileChannel channel, Path lockPath)
            throws IOException {
        long deadline = System.nanoTime() + LOCK_TIMEOUT_NANOS;
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw lockUnavailable(lockPath, "file lock acquisition was interrupted");
            }
            try {
                FileLock fileLock = channel.tryLock();
                if (fileLock != null) return fileLock;
            } catch (OverlappingFileLockException exception) {
                throw lockUnavailable(lockPath, "file lock is already held in this JVM");
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw lockUnavailable(lockPath, "file lock remained unavailable until timeout");
            }
            LockSupport.parkNanos(Math.min(LOCK_POLL_NANOS, remaining));
        }
    }

    private static void closeLockChannel(FileChannel channel) {
        if (channel == null) return;
        try {
            channel.close();
        } catch (IOException | RuntimeException ignored) {
            // The owning operation already has a failure; always continue to unlock the JVM lock.
        }
    }

    private byte[] readBytes(Path path) throws IOException {
        rejectRegularTarget(path);
        long size = Files.size(path);
        if (size > maxDocumentBytes) {
            throw failure("DOCUMENT_TOO_LARGE", path, "document exceeds 1 MiB");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(size, 8_192));
        Set<OpenOption> options = new HashSet<>();
        options.add(StandardOpenOption.READ);
        options.add(LinkOption.NOFOLLOW_LINKS);
        try (FileChannel channel = FileChannel.open(path, options)) {
            ByteBuffer buffer = ByteBuffer.allocate(8_192);
            int total = 0;
            while (true) {
                int read = channel.read(buffer);
                if (read < 0) break;
                if (read == 0) continue;
                total += read;
                if (total > maxDocumentBytes) {
                    throw failure("DOCUMENT_TOO_LARGE", path,
                            "document exceeds 1 MiB");
                }
                output.write(buffer.array(), 0, read);
                buffer.clear();
            }
        }
        return output.toByteArray();
    }

    static void writeAtomic(Path target, byte[] bytes, boolean replaceExisting)
            throws IOException {
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) throw new IOException("target has no parent");
        rejectDirectoryChain(parent);
        createDirectories(parent);
        rejectDirectoryChain(parent);
        if (Files.isSymbolicLink(target)) {
            throw failure("UNSAFE_PATH", target, "symbolic-link target is rejected");
        }
        boolean exists = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (exists && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw failure("UNSAFE_PATH", target, "atomic target is not a regular file");
        }
        if (replaceExisting && !exists) {
            throw new IOException("atomic update target disappeared");
        }
        if (!replaceExisting && exists) throw new FileAlreadyExistsException(target.toString());

        Path temporary = createPrivateTemp(parent, target.getFileName().toString());
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            rejectDirectoryChain(parent);
            if (Files.isSymbolicLink(target)) {
                throw failure("UNSAFE_PATH", target,
                        "symbolic-link target appeared during atomic write");
            }
            boolean targetExists = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
            if (replaceExisting != targetExists) {
                throw new IOException("atomic target changed during write");
            }
            if (targetExists && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw failure("UNSAFE_PATH", target, "atomic target is not a regular file");
            }
            try {
                if (replaceExisting) {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is not supported", exception);
            }
            moved = true;
            forceDirectory(parent);
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static void createDirectories(Path directory) throws IOException {
        try {
            Files.createDirectories(directory, PRIVATE_DIRECTORY_ATTRIBUTE);
        } catch (UnsupportedOperationException exception) {
            Files.createDirectories(directory);
        }
    }

    private static Path createPrivateTemp(Path parent, String targetName) throws IOException {
        try {
            return Files.createTempFile(parent, "." + targetName + ".", ".tmp",
                    PRIVATE_FILE_ATTRIBUTE);
        } catch (UnsupportedOperationException exception) {
            return Files.createTempFile(parent, "." + targetName + ".", ".tmp");
        }
    }

    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Directory force is not available on every supported filesystem.
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw failure("CLOSED", "$", "repository is closed");
        }
        rejectDirectoryChain(root);
    }

    private static void rejectDirectoryChain(Path directory) {
        Path absolute = directory.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        if (current == null) {
            throw failure("UNSAFE_PATH", absolute, "path has no filesystem root");
        }
        for (Path component : absolute) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw failure("UNSAFE_PATH", current,
                        "symbolic-link directory ancestor is rejected");
            }
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw failure("UNSAFE_PATH", current,
                        "non-directory path ancestor is rejected");
            }
        }
    }

    private static void rejectRegularTarget(Path target) {
        if (Files.isSymbolicLink(target)) {
            throw failure("UNSAFE_PATH", target, "symbolic-link file is rejected");
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw failure("UNSAFE_PATH", target, "existing target is not a regular file");
        }
    }

    private static long revisionFromFileName(String fileName) {
        if (!fileName.endsWith(JSON_SUFFIX)) return -1;
        try {
            return Long.parseLong(fileName.substring(0, fileName.length() - JSON_SUFFIX.length()));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    static Failure failure(String code, Path path, String detail) {
        return new Failure(new StorageError(code,
                path == null ? "$" : path.toString(), bounded(detail, "repository failure")));
    }

    static Failure failure(String code, String path, String detail) {
        return new Failure(new StorageError(code, path,
                bounded(detail, "repository failure")));
    }

    private static Failure lockUnavailable(Path path, String detail) {
        return failure("LOCK_UNAVAILABLE", path, bounded(detail, "repository lock is unavailable"));
    }

    private static String bounded(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value;
        return result.length() <= 256 ? result : result.substring(0, 255) + "…";
    }

    @FunctionalInterface
    interface AtomicFileCommitter {
        void commit(Path target, byte[] bytes, boolean replaceExisting) throws IOException;
    }

    interface Layout {
        String kindName();

        Path currentDirectory(Path root);

        Path historyDirectory(Path root);

        Path lockPath(Path root);

        Path currentPath(Path root, String id);

        Path historyPath(Path root, String id, long revision);

        String idFromCurrentPath(Path root, Path currentDirectory, Path candidate);
    }

    interface Codec<T> {
        Decoded<T> decode(byte[] bytes);

        String id(T definition);

        long revision(T definition);
    }

    record Decoded<T>(T value, StorageError error) {
        boolean success() {
            return error == null;
        }
    }

    record StorageError(String code, String path, String detail) {
        StorageError {
            code = Objects.requireNonNull(code, "code");
            path = Objects.requireNonNull(path, "path");
            detail = Objects.requireNonNull(detail, "detail");
        }
    }

    enum ReadStatus {
        LOADED,
        NOT_FOUND,
        INVALID
    }

    record ReadResult<T>(ReadStatus status, String id, Path path, T definition,
                         byte[] bytes, StorageError error) {
        ReadResult {
            status = Objects.requireNonNull(status, "status");
            id = id == null ? "" : id;
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        private static <T> ReadResult<T> loaded(String id, Path path, byte[] bytes,
                                                T definition) {
            return new ReadResult<>(ReadStatus.LOADED, id, path, definition, bytes, null);
        }

        private static <T> ReadResult<T> notFound(String id, Path path) {
            return new ReadResult<>(ReadStatus.NOT_FOUND, id, path, null, null,
                    new StorageError("NOT_FOUND", path == null ? "$.id" : path.toString(),
                            "document was not found"));
        }

        private static <T> ReadResult<T> invalid(String id, Path path, byte[] bytes,
                                                 StorageError error) {
            return new ReadResult<>(ReadStatus.INVALID, id, path, null, bytes,
                    Objects.requireNonNull(error, "error"));
        }
    }

    record Historical<T>(T definition, byte[] bytes) {
        Historical {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    static final class Failure extends RuntimeException {
        private final StorageError error;

        private Failure(StorageError error) {
            super(error.detail());
            this.error = error;
        }

        StorageError error() {
            return error;
        }
    }

    static final class MutationLock implements AutoCloseable {
        private final ReentrantLock jvmLock;
        private final FileChannel channel;
        private final FileLock fileLock;

        private MutationLock(ReentrantLock jvmLock, FileChannel channel, FileLock fileLock) {
            this.jvmLock = jvmLock;
            this.channel = channel;
            this.fileLock = fileLock;
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                try {
                    fileLock.release();
                } catch (IOException exception) {
                    failure = exception;
                }
            } finally {
                try {
                    channel.close();
                } catch (IOException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                } finally {
                    jvmLock.unlock();
                }
            }
            if (failure != null) throw failure;
        }
    }
}
