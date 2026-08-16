package io.github.gyai.projects.quest;

import io.github.gyai.projects.player.progress.PlayerProgressSnapshot;
import io.github.gyai.projects.player.progress.QuestProgressState;

import java.util.Objects;
import java.util.Optional;

/**
 * Read-only Track A bridge. V1 lacks definition/progress revisions and explicit
 * completion/claim fields, so this bridge deliberately publishes no write mapping.
 */
public final class PlayerProgressQuestView {
    public LegacyQuestView inspect(PlayerProgressSnapshot player, String questId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(questId, "questId");
        return new LegacyQuestView(player.playerId(), questId,
                Optional.ofNullable(player.questStates().get(questId)),
                MappingStatus.PERSISTENCE_MAPPING_REQUIRES_OWNER_DECISION);
    }

    public enum MappingStatus { PERSISTENCE_MAPPING_REQUIRES_OWNER_DECISION }

    public record LegacyQuestView(
            java.util.UUID playerId,
            String questId,
            Optional<QuestProgressState> legacyState,
            MappingStatus mappingStatus
    ) {
        public LegacyQuestView {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(questId, "questId");
            legacyState = legacyState == null ? Optional.empty() : legacyState;
            Objects.requireNonNull(mappingStatus, "mappingStatus");
        }
    }
}
