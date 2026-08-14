package io.github.gyai.projects.content.persistence;

import io.github.gyai.projects.content.definition.AbilityDefinition;
import io.github.gyai.projects.content.definition.DefinitionSupport;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Atomic, revisioned repository for current Ability JSON documents. */
public final class AbilityDefinitionJsonRepository implements AutoCloseable {
    private static final String KIND_DIRECTORY = "abilities";
    private static final String HISTORY_DIRECTORY = ".history";
    static final String LOCK_FILE_NAME = ".projects-ability-content.lock";

    private final AbilityDefinitionJsonCodec codec;
    private final RevisionedFileStore<AbilityDefinition> store;

    public AbilityDefinitionJsonRepository(Path root) {
        this(root, new AbilityDefinitionJsonCodec());
    }

    public AbilityDefinitionJsonRepository(Path root, AbilityDefinitionJsonCodec codec) {
        this(root, codec, AbilityDefinitionJsonRepository::writeAtomic);
    }

    AbilityDefinitionJsonRepository(Path root, AbilityDefinitionJsonCodec codec,
                                    AtomicFileCommitter committer) {
        if (root == null) throw new IllegalArgumentException("root is required");
        this.codec = Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(committer, "committer");
        this.store = new RevisionedFileStore<>(root, new AbilityLayout(), new AbilityCodec(codec),
                AbilityDefinitionJsonCodec.MAX_DOCUMENT_BYTES, committer::commit);
    }

    /** Create a new document from base revision zero. */
    public synchronized SaveResult create(AbilityDefinition draft) {
        return create(draft, 0);
    }

    /** Create only when the caller's expected base revision is zero. */
    public synchronized SaveResult create(AbilityDefinition draft, long expectedBaseRevision) {
        try {
            try (RevisionedFileStore.MutationLock ignored = store.acquireMutationLock()) {
                return createLocked(draft, expectedBaseRevision);
            }
        } catch (RevisionedFileStore.Failure failure) {
            return rejected(toAbilityError(failure.error()));
        } catch (IOException | RuntimeException exception) {
            return failed(AbilityPersistenceError.IO_FAILURE, "$",
                    bounded(exception.getMessage(), "create failed"));
        }
    }

    /** Update only when both expected and draft base revisions equal disk revision. */
    public synchronized SaveResult update(AbilityDefinition draft, long expectedBaseRevision) {
        try {
            try (RevisionedFileStore.MutationLock ignored = store.acquireMutationLock()) {
                return updateLocked(draft, expectedBaseRevision);
            }
        } catch (RevisionedFileStore.Failure failure) {
            return rejected(toAbilityError(failure.error()));
        } catch (IOException | RuntimeException exception) {
            return failed(AbilityPersistenceError.IO_FAILURE, "$.revision",
                    bounded(exception.getMessage(), "update failed"));
        }
    }

    /** Choose create or update according to whether the target currently exists. */
    public synchronized SaveResult save(AbilityDefinition draft, long expectedBaseRevision) {
        if (draft == null) {
            return rejected(new AbilityPersistenceError(
                    AbilityPersistenceError.INVALID_DEFINITION, "$",
                    "AbilityDefinition is required"));
        }
        try {
            try (RevisionedFileStore.MutationLock ignored = store.acquireMutationLock()) {
                LoadResult current = loadInternal(draft.abilityId());
                if (current.status() == LoadStatus.NOT_FOUND) {
                    return createLocked(draft, expectedBaseRevision);
                }
                if (current.status() == LoadStatus.LOADED) {
                    return updateLocked(draft, expectedBaseRevision);
                }
                return fromLoadFailure(current);
            }
        } catch (RevisionedFileStore.Failure failure) {
            return rejected(toAbilityError(failure.error()));
        } catch (IOException | RuntimeException exception) {
            return failed(AbilityPersistenceError.IO_FAILURE, "$.revision",
                    bounded(exception.getMessage(), "save failed"));
        }
    }

    /** Load one current document by its canonical namespaced ID. */
    public synchronized LoadResult load(String abilityId) {
        try {
            return loadInternal(abilityId);
        } catch (RevisionedFileStore.Failure failure) {
            return LoadResult.failure(abilityId, null, toAbilityError(failure.error()));
        } catch (IOException | RuntimeException exception) {
            return LoadResult.failure(abilityId, null,
                    new AbilityPersistenceError(AbilityPersistenceError.IO_FAILURE, "$",
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
                    String abilityId = store.idFromCurrentPath(candidate);
                    results.add(load(abilityId));
                } catch (RevisionedFileStore.Failure failure) {
                    results.add(LoadResult.failure("", candidate, toAbilityError(failure.error())));
                } catch (RuntimeException exception) {
                    results.add(LoadResult.failure("", candidate,
                            new AbilityPersistenceError(AbilityPersistenceError.UNSAFE_PATH,
                                    candidate.toString(),
                                    bounded(exception.getMessage(), "unsafe content path"))));
                }
            }
            results.sort(Comparator.comparing(LoadResult::id)
                    .thenComparing(result -> result.path() == null ? "" : result.path().toString()));
            return List.copyOf(results);
        } catch (RevisionedFileStore.Failure failure) {
            return List.of(LoadResult.failure("", kind, toAbilityError(failure.error())));
        } catch (IOException | RuntimeException exception) {
            return List.of(LoadResult.failure("", kind,
                    new AbilityPersistenceError(AbilityPersistenceError.IO_FAILURE, "$",
                            bounded(exception.getMessage(), "list failed"))));
        }
    }

    /** Roll back a validated historical payload as the next current revision. */
    public synchronized SaveResult rollback(String abilityId, long targetRevision,
                                             long expectedCurrentRevision) {
        try {
            try (RevisionedFileStore.MutationLock ignored = store.acquireMutationLock()) {
                return rollbackLocked(abilityId, targetRevision, expectedCurrentRevision);
            }
        } catch (RevisionedFileStore.Failure failure) {
            return rejected(toAbilityError(failure.error()));
        } catch (IOException | RuntimeException exception) {
            return failed(AbilityPersistenceError.IO_FAILURE, "$.revision",
                    bounded(exception.getMessage(), "rollback failed"));
        }
    }

    /** Return available historical revision numbers in ascending order. */
    public synchronized List<Long> history(String abilityId) {
        return store.history(abilityId);
    }

    @Override
    public synchronized void close() {
        store.close();
    }

    private SaveResult createLocked(AbilityDefinition draft, long expectedBaseRevision)
            throws IOException {
        if (expectedBaseRevision != 0) {
            return rejected(new AbilityPersistenceError(
                    AbilityPersistenceError.INVALID_BASE_REVISION, "$.revision",
                    "create requires expected base revision 0"));
        }
        AbilityPersistenceError draftError = validateDraft(draft);
        if (draftError != null) return rejected(draftError);
        if (draft.revision() != 0) {
            return rejected(new AbilityPersistenceError(
                    AbilityPersistenceError.INVALID_BASE_REVISION, "$.revision",
                    "create draft revision must be 0"));
        }

        LoadResult current = loadInternal(draft.abilityId());
        if (current.status() == LoadStatus.LOADED) return targetExists(current);
        if (current.status() != LoadStatus.NOT_FOUND) return fromLoadFailure(current);

        AbilityDefinition saved = withRevision(draft, 1);
        AbilityDefinitionJsonCodec.EncodeResult encoded = codec.encode(saved);
        if (!encoded.success()) return rejected(encoded.error());
        Path target = store.currentPath(saved.abilityId());
        store.commit(target, encoded.bytes(), false);
        return saved(saved);
    }

    private SaveResult updateLocked(AbilityDefinition draft, long expectedBaseRevision)
            throws IOException {
        if (expectedBaseRevision < 1) {
            return rejected(new AbilityPersistenceError(
                    AbilityPersistenceError.INVALID_BASE_REVISION, "$.revision",
                    "update requires a positive expected base revision"));
        }
        AbilityPersistenceError draftError = validateDraft(draft);
        if (draftError != null) return rejected(draftError);
        if (draft.revision() != expectedBaseRevision) {
            return rejected(new AbilityPersistenceError(
                    AbilityPersistenceError.INVALID_BASE_REVISION, "$.revision",
                    "draft revision must equal expected base revision"));
        }

        LoadResult current = loadInternal(draft.abilityId());
        if (current.status() == LoadStatus.NOT_FOUND) {
            return notFound(current.path(), draft.abilityId());
        }
        if (current.status() != LoadStatus.LOADED) return fromLoadFailure(current);
        if (current.definition().revision() != expectedBaseRevision) return conflict(current);

        long nextRevision;
        try {
            nextRevision = Math.addExact(current.definition().revision(), 1);
        } catch (ArithmeticException exception) {
            return failed(AbilityPersistenceError.REVISION_OVERFLOW, "$.revision",
                    "next revision overflows signed 64-bit range");
        }
        AbilityDefinition saved = withRevision(draft, nextRevision);
        AbilityDefinitionJsonCodec.EncodeResult encoded = codec.encode(saved);
        if (!encoded.success()) return rejected(encoded.error());
        store.preserveHistory(draft.abilityId(), current.definition().revision(), current.bytes());
        store.commit(current.path(), encoded.bytes(), true);
        return saved(saved);
    }

    private SaveResult rollbackLocked(String abilityId, long targetRevision,
                                      long expectedCurrentRevision) throws IOException {
        if (targetRevision < 1) {
            return rejected(new AbilityPersistenceError(
                    AbilityPersistenceError.INVALID_VALUE, "$.targetRevision",
                    "history revision must be positive"));
        }
        if (expectedCurrentRevision < 1) {
            return rejected(new AbilityPersistenceError(
                    AbilityPersistenceError.INVALID_BASE_REVISION, "$.revision",
                    "expected current revision must be positive"));
        }
        LoadResult current = loadInternal(abilityId);
        if (current.status() == LoadStatus.NOT_FOUND) return notFound(current.path(), abilityId);
        if (current.status() != LoadStatus.LOADED) return fromLoadFailure(current);
        if (current.definition().revision() != expectedCurrentRevision) return conflict(current);
        if (targetRevision >= current.definition().revision()) {
            return rejected(new AbilityPersistenceError(
                    AbilityPersistenceError.INVALID_VALUE, "$.targetRevision",
                    "rollback target must be an older historical revision"));
        }

        RevisionedFileStore.Historical<AbilityDefinition> historical =
                store.readHistory(abilityId, targetRevision);
        long nextRevision;
        try {
            nextRevision = Math.addExact(current.definition().revision(), 1);
        } catch (ArithmeticException exception) {
            return failed(AbilityPersistenceError.REVISION_OVERFLOW, "$.revision",
                    "next revision overflows signed 64-bit range");
        }
        AbilityDefinition saved = withRevision(historical.definition(), nextRevision);
        AbilityDefinitionJsonCodec.EncodeResult encoded = codec.encode(saved);
        if (!encoded.success()) return rejected(encoded.error());
        store.preserveHistory(abilityId, current.definition().revision(), current.bytes());
        store.commit(current.path(), encoded.bytes(), true);
        return saved(saved);
    }

    private LoadResult loadInternal(String abilityId) throws IOException {
        RevisionedFileStore.ReadResult<AbilityDefinition> result = store.readCurrent(abilityId);
        return switch (result.status()) {
            case LOADED -> LoadResult.loaded(result.id(), result.path(), result.bytes(),
                    result.definition());
            case NOT_FOUND -> LoadResult.notFound(result.id(), result.path());
            case INVALID -> LoadResult.invalid(result.id(), result.path(), result.bytes(),
                    toAbilityError(result.error()));
        };
    }

    private AbilityPersistenceError validateDraft(AbilityDefinition draft) {
        AbilityDefinitionJsonCodec.EncodeResult validation = codec.encode(draft);
        return validation.success() ? null : validation.error();
    }

    private Path kindDirectory() {
        return store.currentDirectory();
    }

    private static AbilityPersistenceError toAbilityError(RevisionedFileStore.StorageError error) {
        return new AbilityPersistenceError(error.code(), error.path(), error.detail());
    }

    private static AbilityDefinition withRevision(AbilityDefinition source, long revision) {
        return new AbilityDefinition(
                AbilityDefinition.SCHEMA_VERSION,
                source.abilityId(),
                revision,
                source.displayName(),
                source.timing(),
                source.targeting(),
                source.timeline(),
                source.interruptPolicy(),
                source.visualReference());
    }

    private static String bounded(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value;
        return result.length() <= 256 ? result : result.substring(0, 255) + "…";
    }

    private static SaveResult saved(AbilityDefinition definition) {
        return new SaveResult(SaveStatus.SAVED, definition, null, definition.revision(), null);
    }

    private static SaveResult rejected(AbilityPersistenceError error) {
        return new SaveResult(SaveStatus.REJECTED, null, null, 0,
                Objects.requireNonNull(error, "error"));
    }

    private static SaveResult failed(String code, String path, String detail) {
        return rejected(new AbilityPersistenceError(code, path, bounded(detail, "save failed")));
    }

    private static SaveResult conflict(LoadResult current) {
        return new SaveResult(SaveStatus.CONFLICT, null, current.definition(),
                current.definition().revision(), new AbilityPersistenceError(
                AbilityPersistenceError.CONFLICT, "$.revision",
                "expected revision does not match current revision"));
    }

    private static SaveResult targetExists(LoadResult current) {
        return new SaveResult(SaveStatus.TARGET_EXISTS, null, current.definition(),
                current.definition().revision(), new AbilityPersistenceError(
                AbilityPersistenceError.TARGET_EXISTS, "$.id",
                "a current document already exists for this ID"));
    }

    private static SaveResult notFound(Path path, String abilityId) {
        return new SaveResult(SaveStatus.NOT_FOUND, null, null, 0,
                new AbilityPersistenceError(AbilityPersistenceError.NOT_FOUND,
                        path == null ? "$.id" : path.toString(),
                        "current document was not found for " + abilityId));
    }

    private static SaveResult fromLoadFailure(LoadResult result) {
        AbilityPersistenceError error = result.error() == null
                ? new AbilityPersistenceError(AbilityPersistenceError.IO_FAILURE, "$.id",
                "current document cannot be read")
                : result.error();
        return new SaveResult(SaveStatus.REJECTED, null, result.definition(),
                result.definition() == null ? 0 : result.definition().revision(), error);
    }

    private static void writeAtomic(Path target, byte[] bytes, boolean replaceExisting)
            throws IOException {
        RevisionedFileStore.writeAtomic(target, bytes, replaceExisting);
    }

    private static final class AbilityCodec implements RevisionedFileStore.Codec<AbilityDefinition> {
        private final AbilityDefinitionJsonCodec codec;

        private AbilityCodec(AbilityDefinitionJsonCodec codec) {
            this.codec = codec;
        }

        @Override
        public RevisionedFileStore.Decoded<AbilityDefinition> decode(byte[] bytes) {
            AbilityDefinitionJsonCodec.DecodeResult result = codec.decode(bytes);
            return result.success()
                    ? new RevisionedFileStore.Decoded<>(result.definition(), null)
                    : new RevisionedFileStore.Decoded<>(null, toStorageError(result.error()));
        }

        @Override
        public String id(AbilityDefinition definition) {
            return definition.abilityId();
        }

        @Override
        public long revision(AbilityDefinition definition) {
            return definition.revision();
        }

        private static RevisionedFileStore.StorageError toStorageError(
                AbilityPersistenceError error) {
            return new RevisionedFileStore.StorageError(error.code(), error.path(), error.detail());
        }
    }

    private static final class AbilityLayout implements RevisionedFileStore.Layout {
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
        public Path currentPath(Path root, String abilityId) {
            String[] components = safeIdComponents(abilityId);
            Path current = currentDirectory(root).resolve(components[0]);
            for (int index = 1; index < components.length - 1; index++) {
                current = current.resolve(components[index]);
            }
            String fileName = components[components.length - 1]
                    + RevisionedFileStore.JSON_SUFFIX;
            return current.resolve(fileName).toAbsolutePath().normalize();
        }

        @Override
        public Path historyPath(Path root, String abilityId, long revision) {
            String[] components = safeIdComponents(abilityId);
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
                        "Ability file must contain namespace and path directories");
            }
            String namespace = relative.getName(0).toString();
            String last = relative.getName(relative.getNameCount() - 1).toString();
            if (!last.endsWith(RevisionedFileStore.JSON_SUFFIX)
                    || last.length() == RevisionedFileStore.JSON_SUFFIX.length()) {
                throw RevisionedFileStore.failure("UNSAFE_PATH", candidate,
                        "Ability file name is invalid");
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
            String abilityId = id.toString();
            Path expected = currentPath(root, abilityId);
            if (!expected.equals(candidate.toAbsolutePath().normalize())) {
                throw RevisionedFileStore.failure("UNSAFE_PATH", candidate,
                        "file path is not the canonical ID mapping");
            }
            return abilityId;
        }

        private static String[] safeIdComponents(String abilityId) {
            if (!DefinitionSupport.isNamespacedId(abilityId)) {
                throw RevisionedFileStore.failure("UNSAFE_PATH", "$.id",
                        "ID is not a canonical namespaced ID");
            }
            int separator = abilityId.indexOf(':');
            String namespace = abilityId.substring(0, separator);
            String path = abilityId.substring(separator + 1);
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
                             AbilityDefinition definition, byte[] bytes,
                             AbilityPersistenceError error) {
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
                                         AbilityDefinition definition) {
            return new LoadResult(LoadStatus.LOADED, id, path, definition, bytes, null);
        }

        private static LoadResult notFound(String id, Path path) {
            return new LoadResult(LoadStatus.NOT_FOUND, id, path, null, null,
                    new AbilityPersistenceError(AbilityPersistenceError.NOT_FOUND,
                            path == null ? "$.id" : path.toString(), "document was not found"));
        }

        private static LoadResult invalid(String id, Path path, byte[] bytes,
                                          AbilityPersistenceError error) {
            return new LoadResult(LoadStatus.INVALID, id, path, null, bytes, error);
        }

        private static LoadResult failure(String id, Path path, AbilityPersistenceError error) {
            LoadStatus status = switch (error.code()) {
                case AbilityPersistenceError.UNSAFE_PATH -> LoadStatus.UNSAFE_PATH;
                case AbilityPersistenceError.CLOSED -> LoadStatus.CLOSED;
                case AbilityPersistenceError.DOCUMENT_TOO_LARGE,
                        AbilityPersistenceError.INVALID_UTF8,
                        AbilityPersistenceError.BOM_REJECTED,
                        AbilityPersistenceError.INVALID_JSON,
                        AbilityPersistenceError.TRAILING_DATA,
                        AbilityPersistenceError.DUPLICATE_KEY,
                        AbilityPersistenceError.UNKNOWN_KEY,
                        AbilityPersistenceError.MISSING_VALUE,
                        AbilityPersistenceError.NULL_REQUIRED_FIELD,
                        AbilityPersistenceError.INVALID_VALUE,
                        AbilityPersistenceError.WRONG_FORMAT,
                        AbilityPersistenceError.UNSUPPORTED_SCHEMA,
                        AbilityPersistenceError.WRONG_KIND,
                        AbilityPersistenceError.NON_INTEGRAL_NUMBER,
                        AbilityPersistenceError.REVISION_OVERFLOW,
                        AbilityPersistenceError.NEGATIVE_REVISION,
                        AbilityPersistenceError.NON_FINITE_NUMBER,
                        AbilityPersistenceError.UNSUPPORTED_ENUM,
                        AbilityPersistenceError.UNKNOWN_VARIANT,
                        AbilityPersistenceError.VARIANT_MISMATCH,
                        AbilityPersistenceError.NESTING_TOO_DEEP,
                        AbilityPersistenceError.COLLECTION_TOO_LARGE,
                        AbilityPersistenceError.STRING_TOO_LONG,
                        AbilityPersistenceError.INVALID_NAMESPACED_ID,
                        AbilityPersistenceError.INVALID_LOCAL_ID,
                        AbilityPersistenceError.DUPLICATE_REFERENCE,
                        AbilityPersistenceError.DUPLICATE_LOCAL_ID,
                        AbilityPersistenceError.DUPLICATE_TAG,
                        AbilityPersistenceError.NUMBER_OUT_OF_RANGE,
                        AbilityPersistenceError.EMPTY_DEFINITION,
                        AbilityPersistenceError.CONTRADICTORY_DEFINITION,
                        AbilityPersistenceError.DAMAGE_TYPE_TAG_MISMATCH,
                        AbilityPersistenceError.ELEMENT_TAG_MISMATCH
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

    public record SaveResult(SaveStatus status, AbilityDefinition definition,
                             AbilityDefinition current, long currentRevision,
                             AbilityPersistenceError error) {
        public SaveResult {
            status = Objects.requireNonNull(status, "status");
        }

        public boolean success() {
            return status == SaveStatus.SAVED;
        }

        public boolean conflict() {
            return status == SaveStatus.CONFLICT;
        }

        public AbilityDefinition saved() {
            return definition;
        }
    }
}
