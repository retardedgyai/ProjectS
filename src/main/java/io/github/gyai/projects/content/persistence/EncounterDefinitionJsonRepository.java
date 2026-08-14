package io.github.gyai.projects.content.persistence;

import io.github.gyai.projects.content.definition.DefinitionSupport;
import io.github.gyai.projects.content.definition.EncounterDefinition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Atomic, revisioned repository for current encounter JSON documents.
 *
 * <p>Current/history layout, locking, bounded reads, no-follow path checks,
 * and atomic replacement are supplied by the shared package-private store.
 * This facade supplies only encounter typing and revision semantics.</p>
 */
public final class EncounterDefinitionJsonRepository implements AutoCloseable {
    private static final String KIND_DIRECTORY = "encounters";
    private static final String HISTORY_DIRECTORY = ".history";
    static final String LOCK_FILE_NAME = ".projects-encounter-content.lock";

    private final EncounterDefinitionJsonCodec codec;
    private final RevisionedFileStore<EncounterDefinition> store;

    public EncounterDefinitionJsonRepository(Path root) {
        this(root, new EncounterDefinitionJsonCodec());
    }

    public EncounterDefinitionJsonRepository(Path root, EncounterDefinitionJsonCodec codec) {
        this(root, codec, EncounterDefinitionJsonRepository::writeAtomic);
    }

    EncounterDefinitionJsonRepository(Path root, EncounterDefinitionJsonCodec codec,
                                      AtomicFileCommitter committer) {
        if (root == null) throw new IllegalArgumentException("root is required");
        this.codec = Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(committer, "committer");
        this.store = new RevisionedFileStore<>(root, new EncounterLayout(),
                new EncounterCodec(codec), EncounterDefinitionJsonCodec.MAX_DOCUMENT_BYTES,
                committer::commit);
    }

    /** Create a new document from base revision zero. */
    public synchronized SaveResult create(EncounterDefinition draft) {
        return create(draft, 0);
    }

    /** Create only when the caller's expected base revision is zero. */
    public synchronized SaveResult create(EncounterDefinition draft,
                                          long expectedBaseRevision) {
        try {
            try (RevisionedFileStore.MutationLock ignored = store.acquireMutationLock()) {
                return createLocked(draft, expectedBaseRevision);
            }
        } catch (RevisionedFileStore.Failure failure) {
            return rejected(toEncounterError(failure.error()));
        } catch (IOException | RuntimeException exception) {
            return failed(EncounterPersistenceError.IO_FAILURE, "$",
                    bounded(exception.getMessage(), "create failed"));
        }
    }

    /** Update only when draft and disk revisions equal the expected base revision. */
    public synchronized SaveResult update(EncounterDefinition draft,
                                          long expectedBaseRevision) {
        try {
            try (RevisionedFileStore.MutationLock ignored = store.acquireMutationLock()) {
                return updateLocked(draft, expectedBaseRevision);
            }
        } catch (RevisionedFileStore.Failure failure) {
            return rejected(toEncounterError(failure.error()));
        } catch (IOException | RuntimeException exception) {
            return failed(EncounterPersistenceError.IO_FAILURE, "$.revision",
                    bounded(exception.getMessage(), "update failed"));
        }
    }

    /** Choose create or update according to whether the target currently exists. */
    public synchronized SaveResult save(EncounterDefinition draft,
                                        long expectedBaseRevision) {
        if (draft == null) {
            return rejected(new EncounterPersistenceError(
                    EncounterPersistenceError.INVALID_DEFINITION, "$",
                    "EncounterDefinition is required"));
        }
        try {
            try (RevisionedFileStore.MutationLock ignored = store.acquireMutationLock()) {
                LoadResult current = loadInternal(draft.encounterId());
                if (current.status() == LoadStatus.NOT_FOUND) {
                    return createLocked(draft, expectedBaseRevision);
                }
                if (current.status() == LoadStatus.LOADED) {
                    return updateLocked(draft, expectedBaseRevision);
                }
                return fromLoadFailure(current);
            }
        } catch (RevisionedFileStore.Failure failure) {
            return rejected(toEncounterError(failure.error()));
        } catch (IOException | RuntimeException exception) {
            return failed(EncounterPersistenceError.IO_FAILURE, "$.revision",
                    bounded(exception.getMessage(), "save failed"));
        }
    }

    /** Load one current document by its canonical namespaced ID. */
    public synchronized LoadResult load(String encounterId) {
        try {
            return loadInternal(encounterId);
        } catch (RevisionedFileStore.Failure failure) {
            return LoadResult.failure(encounterId, null, toEncounterError(failure.error()));
        } catch (IOException | RuntimeException exception) {
            return LoadResult.failure(encounterId, null,
                    new EncounterPersistenceError(EncounterPersistenceError.IO_FAILURE, "$",
                            bounded(exception.getMessage(), "load failed")));
        }
    }

    /** List all current JSON files deterministically, isolating malformed files. */
    public synchronized List<LoadResult> list() {
        Path kind = kindDirectory();
        try {
            List<Path> candidates = store.currentJsonCandidates();
            List<LoadResult> results = new ArrayList<>(candidates.size());
            for (Path candidate : candidates) {
                try {
                    String encounterId = store.idFromCurrentPath(candidate);
                    results.add(load(encounterId));
                } catch (RevisionedFileStore.Failure failure) {
                    results.add(LoadResult.failure("", candidate,
                            toEncounterError(failure.error())));
                } catch (RuntimeException exception) {
                    results.add(LoadResult.failure("", candidate,
                            new EncounterPersistenceError(EncounterPersistenceError.UNSAFE_PATH,
                                    candidate.toString(),
                                    bounded(exception.getMessage(), "unsafe content path"))));
                }
            }
            results.sort(Comparator.comparing(LoadResult::id)
                    .thenComparing(result -> result.path() == null ? "" : result.path().toString()));
            return List.copyOf(results);
        } catch (RevisionedFileStore.Failure failure) {
            return List.of(LoadResult.failure("", kind, toEncounterError(failure.error())));
        } catch (IOException | RuntimeException exception) {
            return List.of(LoadResult.failure("", kind,
                    new EncounterPersistenceError(EncounterPersistenceError.IO_FAILURE, "$",
                            bounded(exception.getMessage(), "list failed"))));
        }
    }

    /** Roll back validated historical content as the next current revision. */
    public synchronized SaveResult rollback(String encounterId, long targetRevision,
                                            long expectedCurrentRevision) {
        try {
            try (RevisionedFileStore.MutationLock ignored = store.acquireMutationLock()) {
                return rollbackLocked(encounterId, targetRevision, expectedCurrentRevision);
            }
        } catch (RevisionedFileStore.Failure failure) {
            return rejected(toEncounterError(failure.error()));
        } catch (IOException | RuntimeException exception) {
            return failed(EncounterPersistenceError.IO_FAILURE, "$.revision",
                    bounded(exception.getMessage(), "rollback failed"));
        }
    }

    /** Return available historical revision numbers in ascending order. */
    public synchronized List<Long> history(String encounterId) {
        return store.history(encounterId);
    }

    @Override
    public synchronized void close() {
        store.close();
    }

    private SaveResult createLocked(EncounterDefinition draft, long expectedBaseRevision)
            throws IOException {
        if (expectedBaseRevision != 0) {
            return rejected(new EncounterPersistenceError(
                    EncounterPersistenceError.INVALID_BASE_REVISION, "$.revision",
                    "create requires expected base revision 0"));
        }
        EncounterPersistenceError draftError = validateDraft(draft);
        if (draftError != null) return rejected(draftError);
        if (draft.revision() != 0) {
            return rejected(new EncounterPersistenceError(
                    EncounterPersistenceError.INVALID_BASE_REVISION, "$.revision",
                    "create draft revision must be 0"));
        }
        LoadResult current = loadInternal(draft.encounterId());
        if (current.status() == LoadStatus.LOADED) return targetExists(current);
        if (current.status() != LoadStatus.NOT_FOUND) return fromLoadFailure(current);

        EncounterDefinition saved = withRevision(draft, 1);
        EncounterDefinitionJsonCodec.EncodeResult encoded = codec.encode(saved);
        if (!encoded.success()) return rejected(encoded.error());
        store.commit(store.currentPath(saved.encounterId()), encoded.bytes(), false);
        return saved(saved);
    }

    private SaveResult updateLocked(EncounterDefinition draft, long expectedBaseRevision)
            throws IOException {
        if (expectedBaseRevision < 1) {
            return rejected(new EncounterPersistenceError(
                    EncounterPersistenceError.INVALID_BASE_REVISION, "$.revision",
                    "update requires a positive expected base revision"));
        }
        EncounterPersistenceError draftError = validateDraft(draft);
        if (draftError != null) return rejected(draftError);
        if (draft.revision() != expectedBaseRevision) {
            return rejected(new EncounterPersistenceError(
                    EncounterPersistenceError.INVALID_BASE_REVISION, "$.revision",
                    "draft revision must equal expected base revision"));
        }
        LoadResult current = loadInternal(draft.encounterId());
        if (current.status() == LoadStatus.NOT_FOUND) {
            return notFound(current.path(), draft.encounterId());
        }
        if (current.status() != LoadStatus.LOADED) return fromLoadFailure(current);
        if (current.definition().revision() != expectedBaseRevision) return conflict(current);

        long nextRevision;
        try {
            nextRevision = Math.addExact(current.definition().revision(), 1);
        } catch (ArithmeticException exception) {
            return failed(EncounterPersistenceError.REVISION_OVERFLOW, "$.revision",
                    "next revision overflows signed 64-bit range");
        }
        EncounterDefinition saved = withRevision(draft, nextRevision);
        EncounterDefinitionJsonCodec.EncodeResult encoded = codec.encode(saved);
        if (!encoded.success()) return rejected(encoded.error());
        store.preserveHistory(draft.encounterId(), current.definition().revision(), current.bytes());
        store.commit(current.path(), encoded.bytes(), true);
        return saved(saved);
    }

    private SaveResult rollbackLocked(String encounterId, long targetRevision,
                                      long expectedCurrentRevision) throws IOException {
        if (targetRevision < 1) {
            return rejected(new EncounterPersistenceError(
                    EncounterPersistenceError.INVALID_VALUE, "$.targetRevision",
                    "history revision must be positive"));
        }
        if (expectedCurrentRevision < 1) {
            return rejected(new EncounterPersistenceError(
                    EncounterPersistenceError.INVALID_BASE_REVISION, "$.revision",
                    "expected current revision must be positive"));
        }
        LoadResult current = loadInternal(encounterId);
        if (current.status() == LoadStatus.NOT_FOUND) return notFound(current.path(), encounterId);
        if (current.status() != LoadStatus.LOADED) return fromLoadFailure(current);
        if (current.definition().revision() != expectedCurrentRevision) return conflict(current);
        if (targetRevision >= current.definition().revision()) {
            return rejected(new EncounterPersistenceError(
                    EncounterPersistenceError.INVALID_VALUE, "$.targetRevision",
                    "rollback target must be an older historical revision"));
        }

        RevisionedFileStore.Historical<EncounterDefinition> historical =
                store.readHistory(encounterId, targetRevision);
        long nextRevision;
        try {
            nextRevision = Math.addExact(current.definition().revision(), 1);
        } catch (ArithmeticException exception) {
            return failed(EncounterPersistenceError.REVISION_OVERFLOW, "$.revision",
                    "next revision overflows signed 64-bit range");
        }
        EncounterDefinition saved = withRevision(historical.definition(), nextRevision);
        EncounterDefinitionJsonCodec.EncodeResult encoded = codec.encode(saved);
        if (!encoded.success()) return rejected(encoded.error());
        store.preserveHistory(encounterId, current.definition().revision(), current.bytes());
        store.commit(current.path(), encoded.bytes(), true);
        return saved(saved);
    }

    private LoadResult loadInternal(String encounterId) throws IOException {
        RevisionedFileStore.ReadResult<EncounterDefinition> result = store.readCurrent(encounterId);
        return switch (result.status()) {
            case LOADED -> LoadResult.loaded(result.id(), result.path(), result.bytes(),
                    result.definition());
            case NOT_FOUND -> LoadResult.notFound(result.id(), result.path());
            case INVALID -> LoadResult.invalid(result.id(), result.path(), result.bytes(),
                    toEncounterError(result.error()));
        };
    }

    private EncounterPersistenceError validateDraft(EncounterDefinition draft) {
        EncounterDefinitionJsonCodec.EncodeResult validation = codec.encode(draft);
        return validation.success() ? null : validation.error();
    }

    private Path kindDirectory() {
        return store.currentDirectory();
    }

    private static EncounterPersistenceError toEncounterError(
            RevisionedFileStore.StorageError error) {
        return new EncounterPersistenceError(error.code(), error.path(), error.detail());
    }

    private static EncounterDefinition withRevision(EncounterDefinition source, long revision) {
        return new EncounterDefinition(
                EncounterDefinition.SCHEMA_VERSION,
                source.encounterId(),
                revision,
                source.actors(),
                source.phases(),
                source.resetPolicy(),
                source.victoryPolicy(),
                source.failurePolicy(),
                source.rewardReferences());
    }

    private static String bounded(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value;
        return result.length() <= 256 ? result : result.substring(0, 255) + "…";
    }

    private static SaveResult saved(EncounterDefinition definition) {
        return new SaveResult(SaveStatus.SAVED, definition, null, definition.revision(), null);
    }

    private static SaveResult rejected(EncounterPersistenceError error) {
        return new SaveResult(SaveStatus.REJECTED, null, null, 0,
                Objects.requireNonNull(error, "error"));
    }

    private static SaveResult failed(String code, String path, String detail) {
        return rejected(new EncounterPersistenceError(code, path, bounded(detail, "save failed")));
    }

    private static SaveResult conflict(LoadResult current) {
        return new SaveResult(SaveStatus.CONFLICT, null, current.definition(),
                current.definition().revision(), new EncounterPersistenceError(
                EncounterPersistenceError.CONFLICT, "$.revision",
                "expected revision does not match current revision"));
    }

    private static SaveResult targetExists(LoadResult current) {
        return new SaveResult(SaveStatus.TARGET_EXISTS, null, current.definition(),
                current.definition().revision(), new EncounterPersistenceError(
                EncounterPersistenceError.TARGET_EXISTS, "$.id",
                "a current document already exists for this ID"));
    }

    private static SaveResult notFound(Path path, String encounterId) {
        return new SaveResult(SaveStatus.NOT_FOUND, null, null, 0,
                new EncounterPersistenceError(EncounterPersistenceError.NOT_FOUND,
                        path == null ? "$.id" : path.toString(),
                        "current document was not found for " + encounterId));
    }

    private static SaveResult fromLoadFailure(LoadResult result) {
        EncounterPersistenceError error = result.error() == null
                ? new EncounterPersistenceError(EncounterPersistenceError.IO_FAILURE, "$.id",
                "current document cannot be read")
                : result.error();
        return new SaveResult(SaveStatus.REJECTED, null, result.definition(),
                result.definition() == null ? 0 : result.definition().revision(), error);
    }

    private static void writeAtomic(Path target, byte[] bytes, boolean replaceExisting)
            throws IOException {
        RevisionedFileStore.writeAtomic(target, bytes, replaceExisting);
    }

    private static final class EncounterCodec
            implements RevisionedFileStore.Codec<EncounterDefinition> {
        private final EncounterDefinitionJsonCodec codec;

        private EncounterCodec(EncounterDefinitionJsonCodec codec) {
            this.codec = codec;
        }

        @Override
        public RevisionedFileStore.Decoded<EncounterDefinition> decode(byte[] bytes) {
            EncounterDefinitionJsonCodec.DecodeResult result = codec.decode(bytes);
            return result.success()
                    ? new RevisionedFileStore.Decoded<>(result.definition(), null)
                    : new RevisionedFileStore.Decoded<>(null, toStorageError(result.error()));
        }

        @Override
        public String id(EncounterDefinition definition) {
            return definition.encounterId();
        }

        @Override
        public long revision(EncounterDefinition definition) {
            return definition.revision();
        }

        private static RevisionedFileStore.StorageError toStorageError(
                EncounterPersistenceError error) {
            return new RevisionedFileStore.StorageError(error.code(), error.path(), error.detail());
        }
    }

    private static final class EncounterLayout implements RevisionedFileStore.Layout {
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
        public Path currentPath(Path root, String encounterId) {
            String[] components = safeIdComponents(encounterId);
            Path current = currentDirectory(root).resolve(components[0]);
            for (int index = 1; index < components.length - 1; index++) {
                current = current.resolve(components[index]);
            }
            return current.resolve(components[components.length - 1]
                            + RevisionedFileStore.JSON_SUFFIX)
                    .toAbsolutePath().normalize();
        }

        @Override
        public Path historyPath(Path root, String encounterId, long revision) {
            String[] components = safeIdComponents(encounterId);
            Path current = historyDirectory(root).resolve(components[0]);
            for (int index = 1; index < components.length; index++) {
                current = current.resolve(components[index]);
            }
            return current.resolve(Long.toString(revision) + RevisionedFileStore.JSON_SUFFIX)
                    .toAbsolutePath().normalize();
        }

        @Override
        public String idFromCurrentPath(Path root, Path currentDirectory, Path candidate) {
            Path relative = currentDirectory.toAbsolutePath().normalize()
                    .relativize(candidate.toAbsolutePath().normalize());
            if (relative.getNameCount() < 2) {
                throw RevisionedFileStore.failure("UNSAFE_PATH", candidate,
                        "Encounter file must contain namespace and path directories");
            }
            String namespace = relative.getName(0).toString();
            String last = relative.getName(relative.getNameCount() - 1).toString();
            if (!last.endsWith(RevisionedFileStore.JSON_SUFFIX)
                    || last.length() == RevisionedFileStore.JSON_SUFFIX.length()) {
                throw RevisionedFileStore.failure("UNSAFE_PATH", candidate,
                        "Encounter file name is invalid");
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
            String encounterId = id.toString();
            Path expected = currentPath(root, encounterId);
            if (!expected.equals(candidate.toAbsolutePath().normalize())) {
                throw RevisionedFileStore.failure("UNSAFE_PATH", candidate,
                        "file path is not the canonical ID mapping");
            }
            return encounterId;
        }

        private static String[] safeIdComponents(String encounterId) {
            if (!DefinitionSupport.isNamespacedId(encounterId)) {
                throw RevisionedFileStore.failure("UNSAFE_PATH", "$.id",
                        "ID is not a canonical namespaced ID");
            }
            int separator = encounterId.indexOf(':');
            String namespace = encounterId.substring(0, separator);
            String path = encounterId.substring(separator + 1);
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
                    || namespace.contains(":") || namespace.contains("\u0000")) {
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
                             EncounterDefinition definition, byte[] bytes,
                             EncounterPersistenceError error) {
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
                                         EncounterDefinition definition) {
            return new LoadResult(LoadStatus.LOADED, id, path, definition, bytes, null);
        }

        private static LoadResult notFound(String id, Path path) {
            return new LoadResult(LoadStatus.NOT_FOUND, id, path, null, null,
                    new EncounterPersistenceError(EncounterPersistenceError.NOT_FOUND,
                            path == null ? "$.id" : path.toString(),
                            "document was not found"));
        }

        private static LoadResult invalid(String id, Path path, byte[] bytes,
                                          EncounterPersistenceError error) {
            return new LoadResult(LoadStatus.INVALID, id, path, null, bytes, error);
        }

        private static LoadResult failure(String id, Path path,
                                          EncounterPersistenceError error) {
            LoadStatus status = switch (error.code()) {
                case EncounterPersistenceError.UNSAFE_PATH -> LoadStatus.UNSAFE_PATH;
                case EncounterPersistenceError.CLOSED -> LoadStatus.CLOSED;
                case EncounterPersistenceError.DOCUMENT_TOO_LARGE,
                        EncounterPersistenceError.INVALID_UTF8,
                        EncounterPersistenceError.BOM_REJECTED,
                        EncounterPersistenceError.INVALID_JSON,
                        EncounterPersistenceError.TRAILING_DATA,
                        EncounterPersistenceError.DUPLICATE_KEY,
                        EncounterPersistenceError.UNKNOWN_KEY,
                        EncounterPersistenceError.MISSING_VALUE,
                        EncounterPersistenceError.NULL_REQUIRED_FIELD,
                        EncounterPersistenceError.INVALID_VALUE,
                        EncounterPersistenceError.WRONG_FORMAT,
                        EncounterPersistenceError.UNSUPPORTED_SCHEMA,
                        EncounterPersistenceError.WRONG_KIND,
                        EncounterPersistenceError.NON_INTEGRAL_NUMBER,
                        EncounterPersistenceError.REVISION_OVERFLOW,
                        EncounterPersistenceError.NEGATIVE_REVISION,
                        EncounterPersistenceError.NON_FINITE_NUMBER,
                        EncounterPersistenceError.UNSUPPORTED_ENUM,
                        EncounterPersistenceError.UNKNOWN_VARIANT,
                        EncounterPersistenceError.VARIANT_MISMATCH,
                        EncounterPersistenceError.NESTING_TOO_DEEP,
                        EncounterPersistenceError.COLLECTION_TOO_LARGE,
                        EncounterPersistenceError.STRING_TOO_LONG,
                        EncounterPersistenceError.INVALID_NAMESPACED_ID,
                        EncounterPersistenceError.DUPLICATE_REFERENCE,
                        EncounterPersistenceError.NUMBER_OUT_OF_RANGE,
                        EncounterPersistenceError.INVALID_DEFINITION,
                        EncounterPersistenceError.INVALID_LOCAL_ID,
                        EncounterPersistenceError.DUPLICATE_ID,
                        EncounterPersistenceError.DUPLICATE_LOCAL_ID,
                        EncounterPersistenceError.EMPTY_DEFINITION,
                        EncounterPersistenceError.CONTRADICTORY_DEFINITION,
                        EncounterPersistenceError.UNRESOLVED_ACTOR_REFERENCE,
                        EncounterPersistenceError.MISSING_PHASE_REFERENCE,
                        EncounterPersistenceError.NO_ENTRY_PHASE,
                        EncounterPersistenceError.MULTIPLE_ENTRY_PHASES,
                        EncounterPersistenceError.UNREACHABLE_PHASE,
                        EncounterPersistenceError.PHASE_CYCLE,
                        EncounterPersistenceError.MISSING_ACTOR_BEHAVIOR,
                        EncounterPersistenceError.DUPLICATE_ACTOR_BEHAVIOR,
                        EncounterPersistenceError.MISSING_ACTOR_ABILITY,
                        EncounterPersistenceError.DOWNED_ABILITY_POOL,
                        EncounterPersistenceError.DUPLICATE_STATE_TRANSITION,
                        EncounterPersistenceError.INVALID_STATE_TRANSITION,
                        EncounterPersistenceError.MISSING_STATE_TRANSITION,
                        EncounterPersistenceError.MISSING_DOWN_CONTROL_POLICY,
                        EncounterPersistenceError.INVALID_DOWN_CONTROL_POLICY,
                        EncounterPersistenceError.INVALID_WEIGHT,
                        EncounterPersistenceError.INVALID_SELECTION,
                        EncounterPersistenceError.INVALID_CONDITION -> LoadStatus.INVALID;
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

    public record SaveResult(SaveStatus status, EncounterDefinition definition,
                             EncounterDefinition current, long currentRevision,
                             EncounterPersistenceError error) {
        public SaveResult {
            status = Objects.requireNonNull(status, "status");
        }

        public boolean success() {
            return status == SaveStatus.SAVED;
        }

        public boolean conflict() {
            return status == SaveStatus.CONFLICT;
        }

        public EncounterDefinition saved() {
            return definition;
        }
    }
}
