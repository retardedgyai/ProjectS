package io.github.gyai.projects.schema;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

public final class SchemaVersions {
    public static final int PLAYER_DATA = 1;
    public static final int EQUIPMENT_ITEM = 1;
    public static final int MOD_DEFINITION = 1;
    public static final int RECIPE_DEFINITION = 1;
    public static final int MOB_DEFINITION = 1;

    private static final Map<SchemaId, Integer> KNOWN = knownVersionsInternal();
    private static final Set<SchemaId> REQUIRES_OWNER_DECISION = unresolvedInternal();

    private SchemaVersions() {
    }

    public static OptionalInt currentVersion(SchemaId schemaId) {
        if (schemaId == null) return OptionalInt.empty();
        Integer version = KNOWN.get(schemaId);
        return version == null ? OptionalInt.empty() : OptionalInt.of(version);
    }

    public static boolean isSupported(SchemaId schemaId, int version) {
        return version > 0 && currentVersion(schemaId).orElse(-1) == version;
    }

    public static Map<SchemaId, Integer> knownVersions() {
        return KNOWN;
    }

    public static Set<SchemaId> requiresOwnerDecision() {
        return REQUIRES_OWNER_DECISION;
    }

    public static int validateVersion(int version) {
        if (version <= 0) {
            throw new IllegalArgumentException("Schema version must be positive");
        }
        return version;
    }

    private static Map<SchemaId, Integer> knownVersionsInternal() {
        EnumMap<SchemaId, Integer> versions = new EnumMap<>(SchemaId.class);
        versions.put(SchemaId.PLAYER_DATA, validateVersion(PLAYER_DATA));
        versions.put(SchemaId.EQUIPMENT_ITEM, validateVersion(EQUIPMENT_ITEM));
        versions.put(SchemaId.MOD_DEFINITION, validateVersion(MOD_DEFINITION));
        versions.put(SchemaId.RECIPE_DEFINITION, validateVersion(RECIPE_DEFINITION));
        versions.put(SchemaId.MOB_DEFINITION, validateVersion(MOB_DEFINITION));
        return Collections.unmodifiableMap(versions);
    }

    private static Set<SchemaId> unresolvedInternal() {
        EnumSet<SchemaId> unresolved = EnumSet.allOf(SchemaId.class);
        unresolved.removeAll(KNOWN.keySet());
        return Collections.unmodifiableSet(unresolved);
    }
}

