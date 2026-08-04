package io.github.gyai.projects.monster.definition.v2;

public record MobDefinitionV2Policy(
        int maximumDefinitions,
        int maximumCollectionEntries,
        int maximumMapEntries,
        int maximumStringBytes,
        long maximumFileBytes,
        int maximumHistory
) {
    public static final MobDefinitionV2Policy SAFE_DEFAULTS =
            new MobDefinitionV2Policy(1_024, 128, 64, 256, 1_048_576L, 20);

    public MobDefinitionV2Policy {
        if (maximumDefinitions < 1 || maximumCollectionEntries < 1
                || maximumMapEntries < 1 || maximumStringBytes < 1
                || maximumFileBytes < 1 || maximumHistory < 1) {
            throw new IllegalArgumentException("Mob v2 policy bounds must be positive");
        }
    }
}
