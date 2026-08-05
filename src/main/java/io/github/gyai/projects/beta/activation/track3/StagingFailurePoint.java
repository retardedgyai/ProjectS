package io.github.gyai.projects.beta.activation.track3;

public enum StagingFailurePoint {
    NONE,
    VALIDATE,
    RESERVE,
    CONSUME,
    PRODUCE,
    PERSIST,
    COMMIT
}
