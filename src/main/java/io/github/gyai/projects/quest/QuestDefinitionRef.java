package io.github.gyai.projects.quest;

import io.github.gyai.projects.transaction.DomainId;

public record QuestDefinitionRef(String questId, long questRevision) {
    public QuestDefinitionRef {
        questId = DomainId.requireNamespaced(questId, "quest ID");
        if (questRevision < 0) throw new IllegalArgumentException("Negative quest revision");
    }
}
