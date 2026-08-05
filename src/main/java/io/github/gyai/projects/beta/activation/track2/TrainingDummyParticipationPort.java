package io.github.gyai.projects.beta.activation.track2;

import java.util.List;
import java.util.UUID;

/** Bounded pull port for deduplicated Training Dummy participation. */
public interface TrainingDummyParticipationPort {
    List<ParticipationEvent> after(long sequenceExclusive, int limit);

    record ParticipationEvent(
            long sequence,
            String hitId,
            UUID playerId,
            UUID targetId,
            String attackId,
            long occurredAtMillis
    ) {
        public ParticipationEvent {
            if (sequence < 1 || hitId == null || hitId.isBlank() || hitId.length() > 128
                    || playerId == null || targetId == null || attackId == null
                    || attackId.isBlank() || attackId.length() > 64 || occurredAtMillis < 0) {
                throw new IllegalArgumentException("Invalid participation event");
            }
        }
    }
}
