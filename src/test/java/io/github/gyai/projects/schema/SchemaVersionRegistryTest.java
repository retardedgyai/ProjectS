package io.github.gyai.projects.schema;

import io.github.gyai.projects.monster.editor.MobDefinition;

import java.util.EnumSet;
import java.util.HashSet;

public final class SchemaVersionRegistryTest {
    public static void main(String[] args) {
        schemaIdsAreUnique();
        knownVersionsArePositiveAndImmutable();
        mobSchemaV1IsPreservedAsSupportedReadVersion();
        clientProtocolV1IsResolved();
        validationRejectsNonPositiveVersions();
    }

    private static void schemaIdsAreUnique() {
        assert SchemaId.values().length == 6;
        assert SchemaId.byId().size() == SchemaId.values().length;
        assert new HashSet<>(SchemaId.byId().keySet()).size()
                == SchemaId.values().length;
        assert SchemaId.fromId("mob-definition").orElseThrow()
                == SchemaId.MOB_DEFINITION;
        assert SchemaId.fromId("unknown").isEmpty();
        assert SchemaId.fromId(null).isEmpty();
        expectUnsupported(() -> SchemaId.byId().clear());
    }

    private static void knownVersionsArePositiveAndImmutable() {
        assert SchemaVersions.knownVersions().size() == 6;
        SchemaVersions.knownVersions().forEach((schema, version) -> {
            assert schema != null;
            assert version != null && version > 0;
        });
        expectUnsupported(() -> SchemaVersions.knownVersions().clear());
        expectUnsupported(() -> SchemaVersions.supportedReadVersions().clear());
        expectUnsupported(() -> SchemaVersions.supportedReadVersions(
                SchemaId.MOB_DEFINITION).clear());
    }

    private static void mobSchemaV1IsPreservedAsSupportedReadVersion() {
        assert MobDefinition.SCHEMA_VERSION == 1;
        assert SchemaVersions.MOB_DEFINITION == 2;
        assert SchemaVersions.currentVersion(SchemaId.MOB_DEFINITION)
                .orElseThrow() == 2;
        assert SchemaVersions.supportedReadVersions(SchemaId.MOB_DEFINITION)
                .equals(java.util.Set.of(1, 2));
        assert SchemaVersions.isSupported(SchemaId.MOB_DEFINITION, 1);
        assert SchemaVersions.isSupported(SchemaId.MOB_DEFINITION, 2);
        assert !SchemaVersions.isSupported(SchemaId.MOB_DEFINITION, 3);
        assert SchemaVersions.currentVersion(SchemaId.PLAYER_DATA).orElseThrow() == 1;
        assert SchemaVersions.currentVersion(SchemaId.EQUIPMENT_ITEM).orElseThrow() == 1;
        assert SchemaVersions.currentVersion(SchemaId.MOD_DEFINITION).orElseThrow() == 1;
        assert SchemaVersions.currentVersion(SchemaId.RECIPE_DEFINITION).orElseThrow() == 1;
    }

    private static void clientProtocolV1IsResolved() {
        assert SchemaVersions.CLIENT_PROTOCOL == 1;
        assert SchemaVersions.currentVersion(SchemaId.CLIENT_PROTOCOL)
                .orElseThrow() == 1;
        assert SchemaVersions.supportedReadVersions(SchemaId.CLIENT_PROTOCOL)
                .equals(java.util.Set.of(1));
        assert SchemaVersions.isSupported(SchemaId.CLIENT_PROTOCOL, 1);
        assert !SchemaVersions.isSupported(SchemaId.CLIENT_PROTOCOL, 2);
        assert SchemaVersions.requiresOwnerDecision().equals(EnumSet.noneOf(SchemaId.class));
        assert SchemaVersions.currentVersion(null).isEmpty();
        assert SchemaVersions.supportedReadVersions(null).isEmpty();
        expectUnsupported(() -> SchemaVersions.requiresOwnerDecision().clear());
    }

    private static void validationRejectsNonPositiveVersions() {
        assert SchemaVersions.validateVersion(1) == 1;
        expectIllegalArgument(() -> SchemaVersions.validateVersion(0));
        expectIllegalArgument(() -> SchemaVersions.validateVersion(-1));
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void expectIllegalArgument(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}

