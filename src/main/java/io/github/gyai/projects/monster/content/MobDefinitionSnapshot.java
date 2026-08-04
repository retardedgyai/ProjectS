package io.github.gyai.projects.monster.content;

import io.github.gyai.projects.monster.definition.v2.MobDefinitionV2;

import java.time.Instant;

/** Immutable pin used by a spawned mob or in-flight attack. */
public record MobDefinitionSnapshot(MobDefinitionV2 definition, long revision,
                                    Instant pinnedAt) {
    public MobDefinitionSnapshot {
        if (definition == null || pinnedAt == null || revision != definition.revision()) {
            throw new IllegalArgumentException("invalid definition snapshot");
        }
    }
}
