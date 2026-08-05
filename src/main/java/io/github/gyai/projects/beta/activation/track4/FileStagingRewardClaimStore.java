package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.reward.RewardClaimKey;
import io.github.gyai.projects.reward.RewardClaimResult;
import io.github.gyai.projects.reward.RewardClaimStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Small durable exactly-once claim store confined to beta-staging/reward-claims. */
public final class FileStagingRewardClaimStore implements RewardClaimStore, AutoCloseable {
    private final Path root;
    private boolean closed;

    public FileStagingRewardClaimStore(Path root) {
        this.root = java.util.Objects.requireNonNull(root).toAbsolutePath().normalize();
        String normalized = this.root.toString().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
        if (!normalized.endsWith("/beta-staging/reward-claims")) {
            throw new IllegalArgumentException("claim store must be beta-staging/reward-claims");
        }
    }

    @Override public synchronized Optional<RewardClaimResult> findTerminal(RewardClaimKey key) {
        requireOpen();
        Path path = path(key);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        try {
            if (Files.isSymbolicLink(path) || Files.size(path) > 16_384) return Optional.empty();
            String[] lines = Files.readString(path, StandardCharsets.UTF_8).split("\\n", -1);
            if (lines.length < 5 || !lines[0].equals(key.stableIdentity())) return Optional.empty();
            return Optional.of(new RewardClaimResult(key,
                    RewardClaimResult.Status.valueOf(lines[1]), lines[2],
                    Boolean.parseBoolean(lines[3]), false, Instant.parse(lines[4])));
        } catch (RuntimeException | java.io.IOException failure) { return Optional.empty(); }
    }

    @Override public synchronized RewardClaimResult executeExclusive(
            RewardClaimKey key, UUID attemptId, Supplier<RewardClaimResult> operation
    ) {
        requireOpen();
        Optional<RewardClaimResult> existing = findTerminal(key);
        if (existing.isPresent()) return existing.orElseThrow().asReplay();
        RewardClaimResult result = java.util.Objects.requireNonNull(operation.get());
        if (result.terminal()) write(result);
        return result;
    }

    private void write(RewardClaimResult result) {
        try {
            rejectLinks(root);
            Files.createDirectories(root);
            Path target = path(result.key());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            String value = result.key().stableIdentity() + "\n" + result.status() + "\n"
                    + result.reason().replace('\n', ' ').replace('\r', ' ') + "\n"
                    + result.terminal() + "\n" + result.completedAt() + "\n";
            Files.writeString(temporary, value, StandardCharsets.UTF_8);
            try (var channel = java.nio.channels.FileChannel.open(temporary,
                    java.nio.file.StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("claim store write failed", failure);
        }
    }

    private Path path(RewardClaimKey key) {
        UUID stable = UUID.nameUUIDFromBytes(key.stableIdentity().getBytes(StandardCharsets.UTF_8));
        Path result = root.resolve(stable + ".claim").normalize();
        if (!result.getParent().equals(root)) throw new IllegalArgumentException("unsafe claim path");
        return result;
    }
    private static void rejectLinks(Path path) {
        for (Path current = path; current != null; current = current.getParent())
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current))
                throw new IllegalStateException("symlink rejected");
    }
    @Override public synchronized void close() { closed = true; }
    public Path root() { return root; }
    private void requireOpen() { if (closed) throw new IllegalStateException("claim store closed"); }
}
