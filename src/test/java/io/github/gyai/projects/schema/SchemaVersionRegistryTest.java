package io.github.gyai.projects.schema;

import io.github.gyai.projects.monster.editor.MobDefinition;

import java.util.EnumSet;
import java.util.HashSet;

public final class SchemaVersionRegistryTest {
    public static void main(String[] args) {
        schemaIdsAreUnique();
        knownVersionsArePositiveAndImmutable();
        mobSchemaV1IsPreserved();
        unresolvedSchemasAreExplicit();
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
        assert !SchemaVersions.knownVersions().isEmpty();
        SchemaVersions.knownVersions().forEach((schema, version) -> {
            assert schema != null;
            assert version != null && version > 0;
        });
        expectUnsupported(() -> SchemaVersions.knownVersions().clear());
    }

    private static void mobSchemaV1IsPreserved() {
        assert MobDefinition.SCHEMA_VERSION == 1;
        assert SchemaVersions.MOB_DEFINITION == MobDefinition.SCHEMA_VERSION;
        assert SchemaVersions.currentVersion(SchemaId.MOB_DEFINITION)
                .orElseThrow() == 1;
        assert SchemaVersions.isSupported(SchemaId.MOB_DEFINITION, 1);
        assert !SchemaVersions.isSupported(SchemaId.MOB_DEFINITION, 2);
    }

    private static void unresolvedSchemasAreExplicit() {
        EnumSet<SchemaId> expected = EnumSet.allOf(SchemaId.class);
        expected.remove(SchemaId.MOB_DEFINITION);
        assert SchemaVersions.requiresOwnerDecision().equals(expected);
        for (SchemaId unresolved : expected) {
            assert SchemaVersions.currentVersion(unresolved).isEmpty();
            assert !SchemaVersions.isSupported(unresolved, 1);
        }
        assert SchemaVersions.currentVersion(null).isEmpty();
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

