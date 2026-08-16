package io.github.gyai.projects.monster.content;

public record MobDefinitionApplyResult(Status status, MobDefinitionSnapshot current,
                                       MobDefinitionSnapshot lastGood, String message) {
    public enum Status { APPLIED, INVALID_RETAINED, STALE_REJECTED, CAPACITY_REJECTED, CLOSED }
}
