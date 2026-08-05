package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.quest.QuestDefinitionRef;
import io.github.gyai.projects.quest.QuestProgressSnapshot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Durable Track-1 staging progress adapter used by Track 4; never touches production PlayerData. */
public final class FileStagingQuestProgressPort implements StagingPlayerProgressPort, AutoCloseable {
    private static final long MAXIMUM_BYTES = 64 * 1024L;
    private final Path root;
    private boolean closed;

    public FileStagingQuestProgressPort(Path root) {
        this.root = java.util.Objects.requireNonNull(root).toAbsolutePath().normalize();
        String normalized = this.root.toString().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
        if (!normalized.endsWith("/beta-staging/players/quests")) {
            throw new IllegalArgumentException("quest progress must be under beta-staging/players/quests");
        }
    }

    @Override public synchronized Optional<QuestProgressSnapshot> load(
            UUID playerId, QuestDefinitionRef quest
    ) {
        requireOpen();
        Path path = path(playerId, quest);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        try {
            rejectLink(path);
            if (Files.size(path) > MAXIMUM_BYTES) throw new IllegalStateException("quest progress oversized");
            QuestProgressSnapshot value = decode(Files.readString(path, StandardCharsets.UTF_8));
            return value.playerId().equals(playerId) && value.definition().equals(quest)
                    ? Optional.of(value) : Optional.empty();
        } catch (IOException | RuntimeException failure) {
            return Optional.empty();
        }
    }

    @Override public synchronized QuestProgressSnapshot save(
            QuestProgressSnapshot proposal, long expectedRevision
    ) {
        requireOpen();
        if (proposal == null || expectedRevision < 0
                || proposal.progressRevision() != expectedRevision + 1) {
            throw new IllegalArgumentException("invalid quest progress proposal");
        }
        Optional<QuestProgressSnapshot> current = load(proposal.playerId(), proposal.definition());
        long actual = current.map(QuestProgressSnapshot::progressRevision).orElse(0L);
        if (actual != expectedRevision) throw new IllegalStateException("quest progress conflict");
        write(path(proposal.playerId(), proposal.definition()), encode(proposal));
        return proposal;
    }

    @Override public synchronized boolean available() { return !closed; }
    @Override public synchronized void close() { closed = true; }
    public Path root() { return root; }

    private Path path(UUID playerId, QuestDefinitionRef quest) {
        if (playerId == null || quest == null) throw new IllegalArgumentException("identity required");
        Path playerRoot = root.resolve(playerId.toString()).normalize();
        String safe = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (quest.questId() + "@" + quest.questRevision()).getBytes(StandardCharsets.UTF_8));
        Path result = playerRoot.resolve(safe + ".progress").normalize();
        if (!result.startsWith(root)) throw new IllegalArgumentException("unsafe progress path");
        return result;
    }

    private void write(Path target, String source) {
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAXIMUM_BYTES) throw new IllegalArgumentException("quest progress oversized");
        try {
            rejectExistingLinks(target.getParent());
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            rejectLink(temporary); rejectLink(target);
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                channel.write(ByteBuffer.wrap(bytes)); channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new IllegalStateException("quest progress write failed", failure);
        }
    }

    private static String encode(QuestProgressSnapshot value) {
        StringBuilder out = new StringBuilder();
        out.append("player=").append(value.playerId()).append('\n');
        out.append("quest=").append(text(value.definition().questId())).append('\n');
        out.append("questRevision=").append(value.definition().questRevision()).append('\n');
        out.append("state=").append(value.state()).append('\n');
        out.append("completion=").append(value.completionMarked()).append('\n');
        out.append("claimed=").append(value.claimedMarked()).append('\n');
        out.append("revision=").append(value.progressRevision()).append('\n');
        value.counters().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> out.append("counter.").append(text(entry.getKey()))
                        .append('=').append(entry.getValue()).append('\n'));
        value.markers().stream().sorted().forEach(marker ->
                out.append("marker.").append(text(marker)).append("=1\n"));
        return out.toString();
    }

    private static QuestProgressSnapshot decode(String source) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        LinkedHashMap<String, Long> counters = new LinkedHashMap<>();
        LinkedHashSet<String> markers = new LinkedHashSet<>();
        for (String line : source.split("\\n")) {
            if (line.isBlank()) continue;
            int at = line.indexOf('='); if (at <= 0) throw new IllegalArgumentException("malformed");
            String key = line.substring(0, at), value = line.substring(at + 1);
            if (key.startsWith("counter.")) counters.put(plain(key.substring(8)), Long.parseLong(value));
            else if (key.startsWith("marker.")) markers.add(plain(key.substring(7)));
            else if (values.put(key, value) != null) throw new IllegalArgumentException("duplicate");
        }
        return new QuestProgressSnapshot(UUID.fromString(values.get("player")),
                new QuestDefinitionRef(plain(values.get("quest")), Long.parseLong(values.get("questRevision"))),
                QuestProgressSnapshot.State.valueOf(values.get("state")), counters, markers,
                Boolean.parseBoolean(values.get("completion")), Boolean.parseBoolean(values.get("claimed")),
                Long.parseLong(values.get("revision")));
    }

    private static String text(String value) { return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String plain(String value) { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
    private static void rejectExistingLinks(Path path) {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) rejectLink(current);
        }
    }
    private static void rejectLink(Path path) { if (Files.isSymbolicLink(path)) throw new IllegalStateException("symlink rejected"); }
    private void requireOpen() { if (closed) throw new IllegalStateException("quest progress closed"); }
}
