package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.track1.player.PlayerProgressObservation;
import io.github.gyai.projects.quest.QuestDefinitionRef;
import io.github.gyai.projects.quest.QuestProgressSnapshot;

import java.util.Optional;
import java.util.UUID;

/** Explicit Track 1 -> Track 4 adapter retaining the actual Track 1 port identity. */
public final class Track1QuestProgressPort implements StagingPlayerProgressPort {
    private final io.github.gyai.projects.beta.activation.track1.player.StagingPlayerProgressPort track1;
    private final StagingPlayerProgressPort questStore;

    public Track1QuestProgressPort(
            io.github.gyai.projects.beta.activation.track1.player.StagingPlayerProgressPort track1,
            StagingPlayerProgressPort questStore
    ) {
        this.track1 = java.util.Objects.requireNonNull(track1);
        this.questStore = java.util.Objects.requireNonNull(questStore);
    }

    @Override public boolean available() { return questStore.available(); }
    @Override public Optional<QuestProgressSnapshot> load(UUID playerId,
            QuestDefinitionRef definition) {
        track1.observation(playerId); // establishes the real Track 1 boundary without mutation
        return questStore.load(playerId, definition);
    }
    @Override public QuestProgressSnapshot save(QuestProgressSnapshot proposal,
            long expectedRevision) {
        Optional<PlayerProgressObservation> observation = track1.observation(proposal.playerId());
        if (observation.isEmpty()) throw new IllegalStateException("Track 1 staging session required");
        return questStore.save(proposal, expectedRevision);
    }

    public boolean usesTrack1Port(
            io.github.gyai.projects.beta.activation.track1.player.StagingPlayerProgressPort value
    ) { return track1 == value; }
}
