package io.github.gyai.projects.reward;

@FunctionalInterface
public interface EndgameUnlockPort {
    UnlockRecordResult record(UnlockProposal proposal);
}
