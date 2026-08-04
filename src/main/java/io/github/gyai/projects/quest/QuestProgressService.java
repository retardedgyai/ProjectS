package io.github.gyai.projects.quest;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class QuestProgressService {
    private final QuestDefinitionRegistry definitions;

    public QuestProgressService(QuestDefinitionRegistry definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    public QuestProgressResult propose(
            Optional<QuestProgressSnapshot> current,
            QuestProgressCommand command
    ) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(command, "command");
        if (!definitions.contains(command.definition())) {
            return result(command, QuestProgressResult.Status.UNKNOWN_QUEST, null,
                    "unknown-quest");
        }
        if (current.isEmpty()) {
            if (command.type() != QuestProgressCommand.Type.START
                    || command.expectedProgressRevision() != 0) {
                return result(command, QuestProgressResult.Status.STALE, null,
                        "quest-not-started");
            }
            QuestProgressSnapshot started = new QuestProgressSnapshot(
                    command.playerId(), command.definition(),
                    QuestProgressSnapshot.State.ACTIVE, Map.of(), Set.of(),
                    false, false, 1);
            return result(command, QuestProgressResult.Status.STARTED, started, "");
        }
        QuestProgressSnapshot before = current.orElseThrow();
        if (!before.playerId().equals(command.playerId())
                || !before.definition().equals(command.definition())) {
            return result(command, QuestProgressResult.Status.REJECTED, null,
                    "progress-context-mismatch");
        }
        if (before.progressRevision() != command.expectedProgressRevision()) {
            return result(command, QuestProgressResult.Status.STALE, null, "stale-revision");
        }
        if (command.type() == QuestProgressCommand.Type.START) {
            return result(command, QuestProgressResult.Status.REJECTED, null,
                    "already-started");
        }
        if (before.claimedMarked()) {
            return result(command, QuestProgressResult.Status.REJECTED, null,
                    "quest-already-claimed");
        }
        return switch (command.type()) {
            case INCREMENT_COUNTER -> increment(before, command);
            case SET_MARKER -> marker(before, command);
            case COMPLETE -> complete(before, command);
            case MARK_CLAIMED -> claim(before, command);
            case START -> throw new IllegalStateException("handled above");
        };
    }

    private static QuestProgressResult increment(
            QuestProgressSnapshot before, QuestProgressCommand command
    ) {
        if (before.completionMarked()) return rejectedAfterCompletion(command);
        String id = command.targetId().orElseThrow();
        LinkedHashMap<String, Long> counters = new LinkedHashMap<>(before.counters());
        long previous = counters.getOrDefault(id, 0L);
        try {
            counters.put(id, Math.addExact(previous, command.amount()));
        } catch (ArithmeticException overflow) {
            return result(command, QuestProgressResult.Status.REJECTED, null,
                    "counter-overflow");
        }
        return updated(before, command, counters, before.markers(), false, false,
                QuestProgressSnapshot.State.ACTIVE, QuestProgressResult.Status.UPDATED);
    }

    private static QuestProgressResult marker(
            QuestProgressSnapshot before, QuestProgressCommand command
    ) {
        if (before.completionMarked()) return rejectedAfterCompletion(command);
        LinkedHashSet<String> markers = new LinkedHashSet<>(before.markers());
        markers.add(command.targetId().orElseThrow());
        return updated(before, command, before.counters(), markers, false, false,
                QuestProgressSnapshot.State.ACTIVE, QuestProgressResult.Status.UPDATED);
    }

    private static QuestProgressResult complete(
            QuestProgressSnapshot before, QuestProgressCommand command
    ) {
        if (before.completionMarked()) return rejectedAfterCompletion(command);
        return updated(before, command, before.counters(), before.markers(), true, false,
                QuestProgressSnapshot.State.COMPLETED, QuestProgressResult.Status.COMPLETED);
    }

    private static QuestProgressResult claim(
            QuestProgressSnapshot before, QuestProgressCommand command
    ) {
        if (!before.completionMarked()) {
            return result(command, QuestProgressResult.Status.REJECTED, null,
                    "quest-not-complete");
        }
        return updated(before, command, before.counters(), before.markers(), true, true,
                QuestProgressSnapshot.State.COMPLETED, QuestProgressResult.Status.CLAIM_MARKED);
    }

    private static QuestProgressResult updated(
            QuestProgressSnapshot before, QuestProgressCommand command,
            Map<String, Long> counters, Set<String> markers,
            boolean completed, boolean claimed, QuestProgressSnapshot.State state,
            QuestProgressResult.Status status
    ) {
        if (before.progressRevision() == Long.MAX_VALUE) {
            return result(command, QuestProgressResult.Status.REJECTED, null,
                    "progress-revision-exhausted");
        }
        QuestProgressSnapshot proposal = new QuestProgressSnapshot(
                before.playerId(), before.definition(), state, counters, markers,
                completed, claimed, before.progressRevision() + 1);
        return result(command, status, proposal, "");
    }

    private static QuestProgressResult rejectedAfterCompletion(QuestProgressCommand command) {
        return result(command, QuestProgressResult.Status.REJECTED, null,
                "quest-already-complete");
    }

    private static QuestProgressResult result(
            QuestProgressCommand command, QuestProgressResult.Status status,
            QuestProgressSnapshot proposal, String reason
    ) {
        return new QuestProgressResult(command.commandId(), status,
                Optional.ofNullable(proposal), reason);
    }
}
