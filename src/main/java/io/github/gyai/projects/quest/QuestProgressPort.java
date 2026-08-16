package io.github.gyai.projects.quest;

import java.util.Optional;
import java.util.UUID;

/** Durable adapter boundary. Foundation code publishes proposals; it does not write PlayerData. */
public interface QuestProgressPort {
    Optional<QuestProgressSnapshot> load(UUID playerId, QuestDefinitionRef definition);

    QuestProgressSnapshot persist(
            QuestProgressSnapshot proposal,
            long expectedProgressRevision
    );
}
