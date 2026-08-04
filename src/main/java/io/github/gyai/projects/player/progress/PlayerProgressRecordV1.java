package io.github.gyai.projects.player.progress;

import io.github.gyai.projects.schema.SchemaVersions;

import java.util.Objects;

/** Versioned persistence envelope for player-data schema v1. */
public record PlayerProgressRecordV1(
        String schemaId,
        int schemaVersion,
        PlayerProgressSnapshot snapshot
) {
    public static final String SCHEMA_ID = "player-data";
    public static final int SCHEMA_VERSION = SchemaVersions.PLAYER_DATA;

    public PlayerProgressRecordV1 {
        if (!SCHEMA_ID.equals(schemaId)) {
            throw new IllegalArgumentException("schema ID must be player-data");
        }
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported player-data version");
        }
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public PlayerProgressRecordV1(PlayerProgressSnapshot snapshot) {
        this(SCHEMA_ID, SCHEMA_VERSION, snapshot);
    }
}
