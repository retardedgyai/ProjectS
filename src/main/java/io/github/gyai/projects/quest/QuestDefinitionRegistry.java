package io.github.gyai.projects.quest;

@FunctionalInterface
public interface QuestDefinitionRegistry {
    boolean contains(QuestDefinitionRef definition);
}
