package io.github.gyai.projects.schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public enum SchemaId {
    PLAYER_DATA("player-data"),
    EQUIPMENT_ITEM("equipment-item"),
    MOD_DEFINITION("mod-definition"),
    RECIPE_DEFINITION("recipe-definition"),
    MOB_DEFINITION("mob-definition"),
    CLIENT_PROTOCOL("client-protocol");

    private static final Map<String, SchemaId> BY_ID = index();

    private final String id;

    SchemaId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<SchemaId> fromId(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Map<String, SchemaId> byId() {
        return BY_ID;
    }

    private static Map<String, SchemaId> index() {
        LinkedHashMap<String, SchemaId> result = new LinkedHashMap<>();
        for (SchemaId schema : values()) {
            if (result.put(schema.id, schema) != null) {
                throw new IllegalStateException("Duplicate schema ID: " + schema.id);
            }
        }
        return Collections.unmodifiableMap(result);
    }
}

