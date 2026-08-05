package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.track2.TrainingDummyParticipationPort;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;

/** Pulls the bounded Track 2 event port into the Track 4 staging quest runtime. */
public final class TrainingDummyParticipationRelay implements AutoCloseable {
    private final JavaPlugin plugin;
    private final TrainingDummyParticipationPort source;
    private final StagingTrainingDummyQuestRuntime target;
    private final Predicate<UUID> compatibleClient;
    private long sequence;
    private BukkitTask task;
    private boolean closed;

    public TrainingDummyParticipationRelay(JavaPlugin plugin,
            TrainingDummyParticipationPort source,
            StagingTrainingDummyQuestRuntime target,
            Predicate<UUID> compatibleClient) {
        this.plugin = java.util.Objects.requireNonNull(plugin);
        this.source = java.util.Objects.requireNonNull(source);
        this.target = java.util.Objects.requireNonNull(target);
        this.compatibleClient = java.util.Objects.requireNonNull(compatibleClient);
    }

    public synchronized void start() {
        if (closed) throw new IllegalStateException("relay closed");
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::poll, 1L, 2L);
    }

    public void poll() {
        for (TrainingDummyParticipationPort.ParticipationEvent event
                : source.after(sequence, 64)) {
            sequence = Math.max(sequence, event.sequence());
            Player player = Bukkit.getPlayer(event.playerId());
            if (player == null || !player.isOnline()) continue;
            target.record(new StagingTrainingDummyQuestRuntime.DirectHit(
                    event.targetId(), stable(event.hitId()), event.playerId(), event.targetId(),
                    player.getWorld().getName(), event.sequence(),
                    Instant.ofEpochMilli(event.occurredAtMillis()), true, true,
                    player.hasPermission("projects.dev"), compatibleClient.test(event.playerId())));
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (task != null) task.cancel();
        task = null;
        sequence = 0;
    }

    public boolean usesSource(TrainingDummyParticipationPort value) { return source == value; }
    private static UUID stable(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
