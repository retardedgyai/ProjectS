package io.github.gyai.projects.content.persistence;

import io.github.gyai.projects.content.definition.DefinitionSupport;
import io.github.gyai.projects.content.definition.MobDefinition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Atomic, revisioned repository for current Mob JSON documents.
 *
 * <p>This facade owns Mob-specific validation, revisions, paths, and result
 * semantics. Shared lock, history, no-follow, bounded-read, and atomic-write
 * mechanics live in the package-private {@link RevisionedFileStore}.</p>
 */
public final class MobDefinitionJsonRepository implements AutoCloseable {
    private static final String KIND_DIRECTORY = "mobs";
    private static final String HISTORY_DIRECTORY = ".history";
    static final String LOCK_FILE_NAME = ".projects-mob-content.lock";

    private final MobDefinitionJsonCodec codec;
    private final RevisionedFileStore<MobDefinition> store;

    public MobDefinitionJsonRepository(Path root) {
        this(root, new MobDefinitionJsonCodec());
    }

    public MobDefinitionJsonRepository(Path root, MobDefinitionJsonCodec codec) {
        this(root, codec, MobDefinitionJsonRepository::writeAtomic);
    }

    MobDefinitionJsonRepository(Path root, MobDefinitionJsonCodec codec,
                                AtomicFileCommitter committer) {
        if (root == null) throw new IllegalArgumentException("root is required");
        this.codec = Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(committer, "committer");
        this.store = new RevisionedFileStore<>(root, new MobLayout(), new MobCodec(codec),
                MobDefinitionJsonCodec.MAX_DOCUMENT_BYTES, committer::commit);
    }

    /** Create a new document from base revision zero. */
    public synchronized SaveResult create(MobDefinition draft) {
        return create(draft, 0);
    }

    /** Create only when the caller's expected base revision is zero. */
    public synchronized SaveResult create(MobDefinition draft, long expectedBaseRevision) {
        try {
            try (RevisionedFileStore.MutationLock ignored = store.acquireMutationLock()) {
                return createLocked(draft, expectedBaseRevision);
            }
        } catch (RevisionedFileStore.Failure failure) {
            return rejected(toMobError(failure.error()));
        } catch (IOException | RuntimeException exception) {
            return failed(MobPersistenceError.IO_FAILURE, "$",
                    bounded(exception.getMessage(), "create failed"));
        }
    }

    /** Update only when both expected and draft base revisions equal the disk revision. */
    public synchronized SaveResult update(MobDefinition draft, long expectedBaseRevision) {
        try {
            try (RevisionedFileStore.MutationLock ignored = store.acquireMutationLock()) {
                return updateLocked(draft, expectedBaseRevision);
            }
        } catch (RevisionedFileStore.Failure failure) {
            return rejected(toMobError(failure.error()));
        } catch (IOException | RuntimeException exception) {
            return failed(MobPersistenceError.IO_FAILURE, "$.revision",
                    bounded(exception.getMessage(), "update failed"));
        }
    }

    /** Choose create or update according to whether the target currently exists. */
    public synchronized SaveResult save(MobDefinition draft, long expectedBaseRevision) {
        if (draft == null) {
            return rejected(new MobPersistenceError(
                    MobPersistenceError.INVALID_DEFINITION, "$",
                    "MobDefinition is required"));
        }
        try {
            try (RevisionedFileStore.MutationLock ignored = store.acquireMutationLock()) {
                LoadResult current = loadInternal(draft.mobId());
                if (current.status() == LoadStatus.NOT_FOUND) {
                    return createLocked(draft, expectedBaseRevision);
                }
                if (current.status() == LoadStatus.LOADED) {
                    return updateLocked(draft, expectedBaseRevision);
                }
                return fromLoadFailure(current);
            }
        } catch (RevisionedFileStore.Failure failure) {
            return rejected(toMobError(failure.error()));
        } catch (IOException | RuntimeException exception) {
            return failed(MobPersistenceError.IO_FAILURE, "$.revision",
                    bounded(exception.getMessage(), "save failed"));
        }
    }

    /** Load one current document by its canonical namespaced ID. */
    public synchronized LoadResult load(String mobId) {
        try {
            return loadInternal(mobId);
        } catch (RevisionedFileStore.Failure failure) {
            return LoadResult.failure(mobId, null, toMobError(failure.error()));
        } catch (IOException | RuntimeException exception) {
            return LoadResult.failure(mobId, null,
                    new MobPersistenceError(MobPersistenceError.IO_FAILURE, "$",
                            bounded(exception.getMessage(), "load failed")));
        }
    }

    /**
     * Deterministically list all current JSON files. A malformed file produces
     * one failed result and does not prevent other files from loading.
     */
    public synchronized List<LoadResult> list() {
        Path kind = kindDirectory();
        try {
            List<Path> candidates = store.currentJsonCandidates();
            List<LoadResult> results = new ArrayList<>(candidates.size());
            for (Path candidate : candidates) {
                try {
                    String mobId = store.idFromCurrentPath(candidate);
                    results.add(load(mobId));
                } catch (RevisionedFileStore.Failure failure) {
                    results.add(LoadResult.failure("", candidate, toMobError(failure.error())));
                } catch (RuntimeException exception) {
                    results.add(LoadResult.failure("", candidate,
                            new MobPersistenceError(MobPersistenceError.UNSAFE_PATH,
                                    candidate.toString(),
                                    bounded(exception.getMessage(), "unsafe content path"))));
                }
            }
            results.sort(Comparator.comparing(LoadResult::id)
                    .thenComparing(result -> result.path() == null ? "" : result.path().toString()));
            return List.copyOf(results);
        } catch (RevisionedFileStore.Failure failure) {
            return List.of(LoadResult.failure("", kind, toMobError(failure.error())));
        } catch (IOException | RuntimeException exception) {
            return List.of(LoadResult.failure("", kind,
                    new MobPersistenceError(MobPersistenceError.IO_FAILURE, "$",
                            bounded(exception.getMessage(), "list failed"))));
        }
    }

    /**
     * Roll back to validated historical payload revision {@code targetRevision}
     * while committing the result as the next revision.
     */
    public synchronized SaveResult rollback(String mobId, long targetRevision,
                                             long expectedCurrentRevision) {
        try {
            try (RevisionedFileStore.MutationLock ignored = store.acquireMutationLock()) {
                return rollbackLocked(mobId, targetRevision, expectedCurrentRevision);
            }
        } catch (RevisionedFileStore.Failure failure) {
            return rejected(toMobError(failure.error()));
        } catch (IOException | RuntimeException exception) {
            return failed(MobPersistenceError.IO_FAILURE, "$.revision",
                    bounded(exception.getMessage(), "rollback failed"));
        }
    }

    /** Return available historical revision numbers in ascending order. */
    public synchronized List<Long> history(String mobId) {
        return store.history(mobId);
    }

    @Override
    public synchronized void close() {
        store.close();
    }

    private SaveResult createLocked(MobDefinition draft, long expectedBaseRevision)
            throws IOException {
        if (expectedBaseRevision != 0) {
            return rejected(new MobPersistenceError(
                    MobPersistenceError.INVALID_BASE_REVISION, "$.revision",
                    "create requires expected base revision 0"));
        }
        MobPersistenceError draftError = validateDraft(draft);
        if (draftError != null) return rejected(draftError);
        if (draft.revision() != 0) {
            return rejected(new MobPersistenceError(
                    MobPersistenceError.INVALID_BASE_REVISION, "$.revision",
                    "create draft revision must be 0"));
        }

        LoadResult current = loadInternal(draft.mobId());
        if (current.status() == LoadStatus.LOADED) {
            return targetExists(current);
        }
        if (current.status() != LoadStatus.NOT_FOUND) return fromLoadFailure(current);

        MobDefinition saved = withRevision(draft, 1);
        MobDefinitionJsonCodec.EncodeResult encoded = codec.encode(saved);
        if (!encoded.success()) return rejected(encoded.error());
        Path target = store.currentPath(saved.mobId());
        store.commit(target, encoded.bytes(), false);
        return saved(saved);
    }

    private SaveResult updateLocked(MobDefinition draft, long expectedBaseRevision)
            throws IOException {
        if (expectedBaseRevision < 1) {
            return rejected(new MobPersistenceError(
                    MobPersistenceError.INVALID_BASE_REVISION, "$.revision",
                    "update requires a positive expected base revision"));
        }
        MobPersistenceError draftError = validateDraft(draft);
        if (draftError != null) return rejected(draftError);
        if (draft.revision() != expectedBaseRevision) {
            return rejected(new MobPersistenceError(
                    MobPersistenceError.INVALID_BASE_REVISION, "$.revision",
                    "draft revision must equal expected base revision"));
        }

        LoadResult current = loadInternal(draft.mobId());
        if (current.status() == LoadStatus.NOT_FOUND) {
            return notFound(current.path(), draft.mobId());
        }
        if (current.status() != LoadStatus.LOADED) return fromLoadFailure(current);
        if (current.definition().revision() != expectedBaseRevision) {
            return conflict(current);
        }

        long nextRevision;
        try {
            nextRevision = Math.addExact(current.definition().revision(), 1);
        } catch (ArithmeticException exception) {
            return failed(MobPersistenceError.REVISION_OVERFLOW, "$.revision",
                    "next revision overflows signed 64-bit range");
        }
        MobDefinition saved = withRevision(draft, nextRevision);
        MobDefinitionJsonCodec.EncodeResult encoded = codec.encode(saved);
        if (!encoded.success()) return rejected(encoded.error());
        store.preserveHistory(draft.mobId(), current.definition().revision(), current.bytes());
        store.commit(current.path(), encoded.bytes(), true);
        return saved(saved);
    }

    private SaveResult rollbackLocked(String mobId, long targetRevision,
                                      long expectedCurrentRevision) throws IOException {
        if (targetRevision < 1) {
            return rejected(new MobPersistenceError(
                    MobPersistenceError.INVALID_VALUE, "$.targetRevision",
                    "history revision must be positive"));
        }
        if (expectedCurrentRevision < 1) {
            return rejected(new MobPersistenceError(
                    MobPersistenceError.INVALID_BASE_REVISION, "$.revision",
                    "expected current revision must be positive"));
        }
        LoadResult current = loadInternal(mobId);
        if (current.status() == LoadStatus.NOT_FOUND) return notFound(current.path(), mobId);
        if (current.status() != LoadStatus.LOADED) return fromLoadFailure(current);
        if (current.definition().revision() != expectedCurrentRevision) {
            return conflict(current);
        }
        if (targetRevision >= current.definition().revision()) {
            return rejected(new MobPersistenceError(
                    MobPersistenceError.INVALID_VALUE, "$.targetRevision",
                    "rollback target must be an older historical revision"));
        }

        RevisionedFileStore.Historical<MobDefinition> historical =
                store.readHistory(mobId, targetRevision);
        long nextRevision;
        try {
            nextRevision = Math.addExact(current.definition().revision(), 1);
        } catch (ArithmeticException exception) {
            return failed(MobPersistenceError.REVISION_OVERFLOW, "$.revision",
                    "next revision overflows signed 64-bit range");
        }
        MobDefinition saved = withRevision(historical.definition(), nextRevision);
        MobDefinitionJsonCodec.EncodeResult encoded = codec.encode(saved);
        if (!encoded.success()) return rejected(encoded.error());
        store.preserveHistory(mobId, current.definition().revision(), current.bytes());
        store.commit(current.path(), encoded.bytes(), true);
        return saved(saved);
    }

    private LoadResult loadInternal(String mobId) throws IOException {
        RevisionedFileStore.ReadResult<MobDefinition> result = store.readCurrent(mobId);
        return switch (result.status()) {
            case LOADED -> LoadResult.loaded(result.id(), result.path(), result.bytes(),
                    result.definition());
            case NOT_FOUND -> LoadResult.notFound(result.id(), result.path());
            case INVALID -> LoadResult.invalid(result.id(), result.path(), result.bytes(),
                    toMobError(result.error()));
        };
    }

    private MobPersistenceError validateDraft(MobDefinition draft) {
        MobDefinitionJsonCodec.EncodeResult validation = codec.encode(draft);
        return validation.success() ? null : validation.error();
    }

    private Path kindDirectory() {
        return store.currentDirectory();
    }

    private static MobPersistenceError toMobError(RevisionedFileStore.StorageError error) {
        return new MobPersistenceError(error.code(), error.path(), error.detail());
    }

    private static MobDefinition withRevision(MobDefinition source, long revision) {
        return new MobDefinition(
                MobDefinition.SCHEMA_VERSION,
                source.mobId(),
                revision,
                source.presentation(),
                source.entityType(),
                source.category(),
                source.stats(),
                source.elementValues(),
                source.resistanceValues(),
                source.equipmentReferences(),
                source.abilityReferences());
    }

    private static String bounded(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value;
        return result.length() <= 256 ? result : result.substring(0, 255) + "…";
    }

    private static SaveResult saved(MobDefinition definition) {
        return new SaveResult(SaveStatus.SAVED, definition, null, definition.revision(), null);
    }

    private static SaveResult rejected(MobPersistenceError error) {
        return new SaveResult(SaveStatus.REJECTED, null, null, 0,
                Objects.requireNonNull(error, "error"));
    }

    private static SaveResult failed(String code, String path, String detail) {
        return rejected(new MobPersistenceError(code, path, bounded(detail, "save failed")));
    }

    private static SaveResult conflict(LoadResult current) {
        return new SaveResult(SaveStatus.CONFLICT, null, current.definition(),
                current.definition().revision(), new MobPersistenceError(
                MobPersistenceError.CONFLICT, "$.revision",
                "expected revision does not match current revision"));
    }

    private static SaveResult targetExists(LoadResult current) {
        return new SaveResult(SaveStatus.TARGET_EXISTS, null, current.definition(),
                current.definition().revision(), new MobPersistenceError(
                MobPersistenceError.TARGET_EXISTS, "$.id",
                "a current document already exists for this ID"));
    }

    private static SaveResult notFound(Path path, String mobId) {
        return new SaveResult(SaveStatus.NOT_FOUND, null, null, 0,
                new MobPersistenceError(MobPersistenceError.NOT_FOUND,
                        path == null ? "$.id" : path.toString(),
                        "current document was not found for " + mobId));
    }

    private static SaveResult fromLoadFailure(LoadResult result) {
        MobPersistenceError error = result.error() == null
                ? new MobPersistenceError(MobPersistenceError.IO_FAILURE, "$.id",
                "current document cannot be read")
                : result.error();
        return new SaveResult(SaveStatus.REJECTED, null, result.definition(),
                result.definition() == null ? 0 : result.definition().revision(), error);
    }

    private static void writeAtomic(Path target, byte[] bytes, boolean replaceExisting)
            throws IOException {
        RevisionedFileStore.writeAtomic(target, bytes, replaceExisting);
    }

    private static final class MobCodec implements RevisionedFileStore.Codec<MobDefinition> {
        private final MobDefinitionJsonCodec codec;

        private MobCodec(MobDefinitionJsonCodec codec) {
            this.codec = codec;
        }

        @Override
        public RevisionedFileStore.Decoded<MobDefinition> decode(byte[] bytes) {
            MobDefinitionJsonCodec.DecodeResult result = codec.decode(bytes);
            return result.success()
                    ? new RevisionedFileStore.Decoded<>(result.definition(), null)
                    : new RevisionedFileStore.Decoded<>(null, toStorageError(result.error()));
        }

        @Override
        public String id(MobDefinition definition) {
            return definition.mobId();
        }

        @Override
        public long revision(MobDefinition definition) {
            return definition.revision();
        }

        private static RevisionedFileStore.StorageError toStorageError(
                MobPersistenceError error) {
            return new RevisionedFileStore.StorageError(error.code(), error.path(), error.detail());
        }
    }

    private static final class MobLayout implements RevisionedFileStore.Layout {
        @Override
        public String kindName() {
            return KIND_DIRECTORY;
        }

        @Override
        public Path currentDirectory(Path root) {
            return root.resolve(KIND_DIRECTORY).toAbsolutePath().normalize();
        }

        @Override
        public Path historyDirectory(Path root) {
            return root.resolve(HISTORY_DIRECTORY).resolve(KIND_DIRECTORY)
                    .toAbsolutePath().normalize();
        }

        @Override
        public Path lockPath(Path root) {
            return root.resolve(LOCK_FILE_NAME).toAbsolutePath().normalize();
        }

        @Override
        public Path currentPath(Path root, String mobId) {
            String[] components = safeIdComponents(mobId);
            Path current = currentDirectory(root).resolve(components[0]);
            for (int index = 1; index < components.length - 1; index++) {
                current = current.resolve(components[index]);
            }
            String fileName = components[components.length - 1]
                    + RevisionedFileStore.JSON_SUFFIX;
            return current.resolve(fileName).toAbsolutePath().normalize();
        }

        @Override
        public Path historyPath(Path root, String mobId, long revision) {
            String[] components = safeIdComponents(mobId);
            Path current = historyDirectory(root).resolve(components[0]);
            for (int index = 1; index < components.length; index++) {
                current = current.resolve(components[index]);
            }
            return current.resolve(Long.toString(revision)
                            + RevisionedFileStore.JSON_SUFFIX)
                    .toAbsolutePath().normalize();
        }

        @Override
        public String idFromCurrentPath(Path root, Path currentDirectory, Path candidate) {
            Path relative = currentDirectory.toAbsolutePath().normalize()
                    .relativize(candidate.toAbsolutePath().normalize());
            if (relative.getNameCount() < 2) {
                throw RevisionedFileStore.failure("UNSAFE_PATH", candidate,
                        "Mob file must contain namespace and path directories");
            }
            String namespace = relative.getName(0).toString();
            String last = relative.getName(relative.getNameCount() - 1).toString();
            if (!last.endsWith(RevisionedFileStore.JSON_SUFFIX)
                    || last.length() == RevisionedFileStore.JSON_SUFFIX.length()) {
                throw RevisionedFileStore.failure("UNSAFE_PATH", candidate,
                        "Mob file name is invalid");
            }
            StringBuilder id = new StringBuilder(namespace).append(':');
            for (int index = 1; index < relative.getNameCount(); index++) {
                if (index > 1) id.append('/');
                String part = relative.getName(index).toString();
                if (index == relative.getNameCount() - 1) {
                    part = part.substring(0, part.length()
                            - RevisionedFileStore.JSON_SUFFIX.length());
                }
                id.append(part);
            }
            String mobId = id.toString();
            Path expected = currentPath(root, mobId);
            if (!expected.equals(candidate.toAbsolutePath().normalize())) {
                throw RevisionedFileStore.failure("UNSAFE_PATH", candidate,
                        "file path is not the canonical ID mapping");
            }
            return mobId;
        }

        private static String[] safeIdComponents(String mobId) {
            if (!DefinitionSupport.isNamespacedId(mobId)) {
                throw RevisionedFileStore.failure("UNSAFE_PATH", "$.id",
                        "ID is not a canonical namespaced ID");
            }
            int separator = mobId.indexOf(':');
            String namespace = mobId.substring(0, separator);
            String path = mobId.substring(separator + 1);
            String[] pathParts = path.split("/", -1);
            String[] components = new String[pathParts.length + 1];
            components[0] = namespace;
            for (int index = 0; index < pathParts.length; index++) {
                String part = pathParts[index];
                if (part.isEmpty() || part.equals(".") || part.equals("..")
                        || part.contains("\\") || part.contains(":")
                        || part.contains("\u0000")) {
                    throw RevisionedFileStore.failure("UNSAFE_PATH", "$.id",
                            "ID contains an unsafe path segment");
                }
                components[index + 1] = part;
            }
            if (namespace.isEmpty() || namespace.equals(".") || namespace.equals("..")
                    || namespace.contains("\\") || namespace.contains("/")
                    || namespace.contains(":")) {
                throw RevisionedFileStore.failure("UNSAFE_PATH", "$.id",
                        "ID contains an unsafe namespace segment");
            }
            return components;
        }
    }

    @FunctionalInterface
    interface AtomicFileCommitter {
        void commit(Path target, byte[] bytes, boolean replaceExisting) throws IOException;
    }

    public enum LoadStatus {
        LOADED,
        NOT_FOUND,
        INVALID,
        UNSAFE_PATH,
        IO_FAILURE,
        CLOSED
    }

    public record LoadResult(LoadStatus status, String id, Path path,
                             MobDefinition definition, byte[] bytes,
                             MobPersistenceError error) {
        public LoadResult {
            status = Objects.requireNonNull(status, "status");
            id = id == null ? "" : id;
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        public boolean success() {
            return status == LoadStatus.LOADED;
        }

        private static LoadResult loaded(String id, Path path, byte[] bytes,
                                         MobDefinition definition) {
            return new LoadResult(LoadStatus.LOADED, id, path, definition, bytes, null);
        }

        private static LoadResult notFound(String id, Path path) {
            return new LoadResult(LoadStatus.NOT_FOUND, id, path, null, null,
                    new MobPersistenceError(MobPersistenceError.NOT_FOUND,
                            path == null ? "$.id" : path.toString(), "document was not found"));
        }

        private static LoadResult invalid(String id, Path path, byte[] bytes,
                                          MobPersistenceError error) {
            return new LoadResult(LoadStatus.INVALID, id, path, null, bytes, error);
        }

        private static LoadResult failure(String id, Path path, MobPersistenceError error) {
            LoadStatus status = switch (error.code()) {
                case MobPersistenceError.UNSAFE_PATH -> LoadStatus.UNSAFE_PATH;
                case MobPersistenceError.CLOSED -> LoadStatus.CLOSED;
                case MobPersistenceError.DOCUMENT_TOO_LARGE,
                        MobPersistenceError.INVALID_UTF8,
                        MobPersistenceError.BOM_REJECTED,
                        MobPersistenceError.INVALID_JSON,
                        MobPersistenceError.TRAILING_DATA,
                        MobPersistenceError.DUPLICATE_KEY,
                        MobPersistenceError.UNKNOWN_KEY,
                        MobPersistenceError.MISSING_VALUE,
                        MobPersistenceError.NULL_REQUIRED_FIELD,
                        MobPersistenceError.INVALID_VALUE,
                        MobPersistenceError.WRONG_FORMAT,
                        MobPersistenceError.UNSUPPORTED_SCHEMA,
                        MobPersistenceError.WRONG_KIND,
                        MobPersistenceError.NON_INTEGRAL_NUMBER,
                        MobPersistenceError.REVISION_OVERFLOW,
                        MobPersistenceError.NEGATIVE_REVISION,
                        MobPersistenceError.NON_FINITE_NUMBER,
                        MobPersistenceError.UNSUPPORTED_ENUM,
                        MobPersistenceError.NESTING_TOO_DEEP,
                        MobPersistenceError.COLLECTION_TOO_LARGE,
                        MobPersistenceError.STRING_TOO_LONG,
                        MobPersistenceError.INVALID_NAMESPACED_ID,
                        MobPersistenceError.DUPLICATE_REFERENCE,
                        MobPersistenceError.NUMBER_OUT_OF_RANGE
                        -> LoadStatus.INVALID;
                default -> LoadStatus.IO_FAILURE;
            };
            return new LoadResult(status, id, path, null, null, error);
        }
    }

    public enum SaveStatus {
        SAVED,
        CONFLICT,
        TARGET_EXISTS,
        NOT_FOUND,
        REJECTED
    }

    public record SaveResult(SaveStatus status, MobDefinition definition,
                             MobDefinition current, long currentRevision,
                             MobPersistenceError error) {
        public SaveResult {
            status = Objects.requireNonNull(status, "status");
        }

        public boolean success() {
            return status == SaveStatus.SAVED;
        }

        public boolean conflict() {
            return status == SaveStatus.CONFLICT;
        }

        public MobDefinition saved() {
            return definition;
        }
    }
}
