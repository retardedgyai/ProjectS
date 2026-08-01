package io.github.gyai.projects.monster.editor;

import java.util.List;

public record HeadDefinition(
        int schemaVersion,
        long revision,
        String id,
        String displayName,
        SourceType sourceType,
        String playerName,
        String textureValue,
        String projectsItemId,
        List<String> tags,
        boolean favorite,
        String sourceNote
) {
    public static final int SCHEMA_VERSION = 1;

    public enum SourceType {
        VANILLA_HEAD,
        PLAYER_PROFILE,
        TEXTURE_VALUE,
        SAVED_HEAD,
        PROJECTS_ITEM
    }

    public HeadDefinition {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public HeadDefinition withRevision(long value) {
        return new HeadDefinition(
                schemaVersion, value, id, displayName, sourceType,
                playerName, textureValue, projectsItemId,
                tags, favorite, sourceNote);
    }
}
