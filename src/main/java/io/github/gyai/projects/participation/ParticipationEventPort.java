package io.github.gyai.projects.participation;

/** Future combat/gathering producers publish immutable events through this port. */
@FunctionalInterface
public interface ParticipationEventPort {
    ParticipationResult record(ParticipationEvent event);
}
