package io.github.gyai.projects.transaction;

public enum TransactionStage {
    VALIDATE,
    RESERVE,
    CONSUME,
    PRODUCE,
    PERSIST,
    COMMIT
}
