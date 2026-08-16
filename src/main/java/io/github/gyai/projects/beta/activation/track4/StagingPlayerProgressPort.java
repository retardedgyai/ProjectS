package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.quest.QuestDefinitionRef;
import io.github.gyai.projects.quest.QuestProgressSnapshot;

import java.util.Optional;
import java.util.UUID;

/** Track 1 producer port consumed without owning its implementation. */
public interface StagingPlayerProgressPort {
    Optional<QuestProgressSnapshot> load(UUID playerId, QuestDefinitionRef quest);

    QuestProgressSnapshot save(QuestProgressSnapshot proposal, long expectedRevision);

    boolean available();
}
