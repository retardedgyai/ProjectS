package io.github.gyai.projects.quest;

import io.github.gyai.projects.transaction.DomainId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record QuestProgressCommand(
        UUID commandId,
        UUID playerId,
        QuestDefinitionRef definition,
        Type type,
        long expectedProgressRevision,
        Optional<String> targetId,
        long amount
) {
    public QuestProgressCommand {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(type, "type");
        if (expectedProgressRevision < 0) {
            throw new IllegalArgumentException("Negative expected progress revision");
        }
        targetId = targetId == null ? Optional.empty() : targetId;
        targetId = targetId.map(id -> DomainId.requireNamespaced(id, "quest target ID"));
        if (type == Type.INCREMENT_COUNTER && (targetId.isEmpty() || amount <= 0)) {
            throw new IllegalArgumentException("Counter increment requires target and positive amount");
        }
        if (type == Type.SET_MARKER && targetId.isEmpty()) {
            throw new IllegalArgumentException("Marker command requires target");
        }
        if (type != Type.INCREMENT_COUNTER && amount != 0) {
            throw new IllegalArgumentException("Only counter increment accepts amount");
        }
        if (type != Type.INCREMENT_COUNTER && type != Type.SET_MARKER && targetId.isPresent()) {
            throw new IllegalArgumentException("Command does not accept target");
        }
    }

    public enum Type { START, INCREMENT_COUNTER, SET_MARKER, COMPLETE, MARK_CLAIMED }
}
