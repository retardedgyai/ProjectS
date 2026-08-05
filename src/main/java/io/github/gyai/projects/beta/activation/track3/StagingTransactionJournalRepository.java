package io.github.gyai.projects.beta.activation.track3;

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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Durable, staging-only recovery journal. It records state; it never executes an operation. */
public final class StagingTransactionJournalRepository implements AutoCloseable {
    public static final int MAXIMUM_FILES = 2_048;
    public static final long MAXIMUM_FILE_BYTES = 64 * 1024L;
    private static final String VERSION = "2";

    private final Path root;
    private final Path quarantine;
    private boolean closed;

    public StagingTransactionJournalRepository(Path root) {
        if (root == null) throw new IllegalArgumentException("journal root is required");
        this.root = root.toAbsolutePath().normalize();
        String normalized = this.root.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!normalized.endsWith("/beta-staging/transactions")) {
            throw new IllegalArgumentException("journal must be under beta-staging/transactions");
        }
        quarantine = this.root.resolve("quarantine");
    }

    public synchronized void save(Entry entry) {
        requireOpen();
        if (entry == null) throw new IllegalArgumentException("entry is required");
        prepareRoot();
        Path destination = entryPath(entry.requestId());
        byte[] bytes = encode(entry).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAXIMUM_FILE_BYTES) throw new IllegalArgumentException("journal entry too large");
        if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                && journalFiles().size() >= MAXIMUM_FILES) {
            throw new IllegalStateException("journal file limit reached");
        }
        Path temporary = root.resolve(entry.requestId() + ".tmp");
        rejectLink(temporary);
        rejectLink(destination);
        try (FileChannel channel = FileChannel.open(temporary,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        } catch (IOException failure) {
            throw new IllegalStateException("journal write failed", failure);
        }
        try {
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            forceDirectory(root);
        } catch (IOException failure) {
            throw new IllegalStateException("journal replace failed", failure);
        }
    }

    public synchronized Optional<Entry> load(UUID requestId) {
        requireOpen();
        if (requestId == null) throw new IllegalArgumentException("requestId is required");
        Path path = entryPath(requestId);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        try {
            rejectLink(path);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(path) > MAXIMUM_FILE_BYTES) {
                quarantine(path, "invalid-file");
                return Optional.empty();
            }
            Entry decoded = decode(Files.readString(path, StandardCharsets.UTF_8));
            if (!decoded.requestId().equals(requestId)) throw new IllegalArgumentException("request mismatch");
            return Optional.of(decoded);
        } catch (IOException | RuntimeException failure) {
            quarantine(path, "corrupt");
            return Optional.empty();
        }
    }

    public synchronized List<Entry> loadAll() {
        requireOpen();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return List.of();
        ArrayList<Entry> result = new ArrayList<>();
        for (Path path : journalFiles()) {
            String name = path.getFileName().toString();
            try {
                UUID requestId = UUID.fromString(name.substring(0, name.length() - 8));
                load(requestId).ifPresent(result::add);
            } catch (RuntimeException failure) {
                quarantine(path, "unknown-name");
            }
        }
        result.sort(Comparator.comparing(Entry::requestId));
        return List.copyOf(result);
    }

    public synchronized void quarantine(UUID requestId, String reason) {
        requireOpen();
        quarantine(entryPath(requestId), reason);
    }

    public synchronized void drain() {
        if (!closed && Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) forceDirectory(root);
    }

    /** Persistent quarantine is a startup gate until an operator resolves it. */
    public synchronized int quarantinedFileCount() {
        requireOpen();
        if (!Files.isDirectory(quarantine, LinkOption.NOFOLLOW_LINKS)) return 0;
        try (var stream = Files.list(quarantine)) {
            return (int) stream.filter(path -> Files.isRegularFile(
                            path, LinkOption.NOFOLLOW_LINKS))
                    .limit(MAXIMUM_FILES + 1L).count();
        } catch (IOException failure) {
            throw new IllegalStateException("journal quarantine listing failed", failure);
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        drain();
        closed = true;
    }

    public Path root() { return root; }

    private void prepareRoot() {
        try {
            Files.createDirectories(root);
            Files.createDirectories(quarantine);
            rejectLink(root);
            rejectLink(quarantine);
        } catch (IOException failure) {
            throw new IllegalStateException("journal directory unavailable", failure);
        }
    }

    private List<Path> journalFiles() {
        try (var stream = Files.list(root)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".journal"))
                    .limit(MAXIMUM_FILES + 1L).toList();
        } catch (IOException failure) {
            throw new IllegalStateException("journal listing failed", failure);
        }
    }

    private Path entryPath(UUID requestId) {
        Path path = root.resolve(requestId + ".journal").normalize();
        if (!path.getParent().equals(root)) throw new IllegalArgumentException("unsafe request path");
        return path;
    }

    private void quarantine(Path source, String reason) {
        if (source == null || !Files.exists(source, LinkOption.NOFOLLOW_LINKS)) return;
        prepareRoot();
        String safe = reason == null ? "unknown" : reason.replaceAll("[^a-zA-Z0-9_-]", "-");
        if (safe.length() > 32) safe = safe.substring(0, 32);
        Path destination = quarantine.resolve(source.getFileName() + "." + safe);
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            forceDirectory(quarantine);
        } catch (IOException failure) {
            throw new IllegalStateException("journal quarantine failed", failure);
        }
    }

    private static void rejectLink(Path path) {
        if (Files.isSymbolicLink(path)) throw new IllegalStateException("symbolic links are not allowed");
    }

    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ignored) {
            // Some platforms cannot open directories; the entry file itself was already forced.
        }
    }

    private static String encode(Entry value) {
        return "version=" + VERSION + "\n"
                + "requestId=" + value.requestId() + "\n"
                + "stage=" + value.stage() + "\n"
                + "playerId=" + value.playerId() + "\n"
                + "operation=" + text(value.operationType()) + "\n"
                + "inputs=" + text(String.join(",", value.inputIdentities())) + "\n"
                + "reservation=" + value.reservationState() + "\n"
                + "output=" + text(value.proposedOutputIdentity()) + "\n"
                + "outcome=" + value.terminalOutcome() + "\n"
                + "recipe=" + text(value.recipeId()) + "\n"
                + "expectedRevision=" + value.expectedRevision() + "\n"
                + "expectedOutputUnits=" + value.expectedOutputUnits() + "\n"
                + "completedStages=" + text(String.join(",", value.completedStages())) + "\n"
                + "outputEquipmentBase=" + value.outputEquipmentBase() + "\n"
                + "reason=" + text(value.reason()) + "\n"
                + "updatedAt=" + value.updatedAtMillis() + "\n";
    }

    private static Entry decode(String source) {
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        for (String line : source.split("\\n")) {
            if (line.isBlank()) continue;
            int separator = line.indexOf('=');
            if (separator <= 0 || values.put(line.substring(0, separator), line.substring(separator + 1)) != null) {
                throw new IllegalArgumentException("malformed journal");
            }
        }
        if (!VERSION.equals(values.get("version")) || values.size() != 16) {
            throw new IllegalArgumentException("unsupported journal");
        }
        String inputs = plain(values.get("inputs"));
        return new Entry(UUID.fromString(values.get("requestId")),
                Stage.valueOf(values.get("stage")), UUID.fromString(values.get("playerId")),
                plain(values.get("operation")), inputs.isBlank() ? List.of() : List.of(inputs.split(",")),
                ReservationState.valueOf(values.get("reservation")), plain(values.get("output")),
                TerminalOutcome.valueOf(values.get("outcome")), plain(values.get("recipe")),
                Long.parseLong(values.get("expectedRevision")),
                Long.parseLong(values.get("expectedOutputUnits")),
                plain(values.get("completedStages")).isBlank() ? List.of()
                        : List.of(plain(values.get("completedStages")).split(",")),
                Boolean.parseBoolean(values.get("outputEquipmentBase")),
                plain(values.get("reason")), Long.parseLong(values.get("updatedAt")));
    }

    private static String text(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String plain(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("journal repository is closed");
    }

    public enum Stage { VALIDATE, RESERVE, RESERVED, CONSUMED, PRODUCED, PERSISTED, COMMITTED, ROLLED_BACK, COMMIT_UNCERTAIN }
    public enum ReservationState { NONE, REQUESTED, HELD, RELEASED, CONSUMED, UNKNOWN }
    public enum TerminalOutcome { NONE, COMMITTED, ROLLED_BACK, COMMIT_UNCERTAIN }

    public record Entry(UUID requestId, Stage stage, UUID playerId, String operationType,
                        List<String> inputIdentities, ReservationState reservationState,
                        String proposedOutputIdentity, TerminalOutcome terminalOutcome,
                        String recipeId, long expectedRevision, long expectedOutputUnits,
                        List<String> completedStages, boolean outputEquipmentBase, String reason,
                        long updatedAtMillis) {
        public Entry(UUID requestId, Stage stage, UUID playerId, String operationType,
                     List<String> inputIdentities, ReservationState reservationState,
                     String proposedOutputIdentity, TerminalOutcome terminalOutcome,
                     long updatedAtMillis) {
            this(requestId, stage, playerId, operationType, inputIdentities,
                    reservationState, proposedOutputIdentity, terminalOutcome,
                    "projects:staging/recovery", 0, 1, List.of(), false, "", updatedAtMillis);
        }
        public Entry {
            if (requestId == null || stage == null || playerId == null || operationType == null
                    || operationType.isBlank() || operationType.length() > 128 || inputIdentities == null
                    || inputIdentities.size() > 128 || reservationState == null
                    || proposedOutputIdentity == null || proposedOutputIdentity.length() > 512
                    || terminalOutcome == null || recipeId == null || recipeId.isBlank()
                    || expectedRevision < 0 || expectedOutputUnits < 1
                    || completedStages == null || completedStages.size() > 6
                    || reason == null || reason.length() > 512 || updatedAtMillis < 0) {
                throw new IllegalArgumentException("invalid journal entry");
            }
            inputIdentities = List.copyOf(inputIdentities);
            completedStages = List.copyOf(completedStages);
            for (String input : inputIdentities) if (input == null || input.isBlank() || input.length() > 256)
                throw new IllegalArgumentException("invalid journal input");
        }
    }
}
