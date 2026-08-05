package io.github.gyai.projects.beta.activation.track4;

/** Track 2 producer boundary; Track 4 accepts only immutable hit facts. */
public interface TrainingDummyParticipationPort {
    StagingTrainingDummyQuestRuntime.HitResult record(
            StagingTrainingDummyQuestRuntime.DirectHit hit);
}
