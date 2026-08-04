package io.github.gyai.projects.network.beta;

import java.util.List;
import java.util.Map;

public record MobEditorDisplaySnapshot(
        int schemaVersion,
        long baseRevision,
        int page,
        List<Summary> entries,
        Detail detail,
        List<String> validation,
        OperationStatus operationStatus
) {
    public enum OperationStatus { IDLE, LOADING, VALID, INVALID, CONFLICT, SAVED, ROLLED_BACK, TEST_SPAWNED, REJECTED }

    public MobEditorDisplaySnapshot {
        if (schemaVersion <= 0 || baseRevision < 0 || page < 0 || operationStatus == null) {
            throw new IllegalArgumentException("Invalid Mob Editor snapshot");
        }
        entries = BetaDisplayValidation.list(
                entries, BetaProtocolLimits.DEFAULTS.mobEditorPageEntries(), "Mob Editor page");
        validation = BetaDisplayValidation.list(validation, 128, "validation details");
        validation.forEach(value -> BetaDisplayValidation.string(value, "validation detail"));
    }

    public record Summary(String mobId, long revision, String displayName) {
        public Summary {
            mobId = BetaDisplayValidation.id(mobId, "mobId");
            displayName = BetaDisplayValidation.string(displayName, "displayName");
            if (revision < 0) throw new IllegalArgumentException("Revision cannot be negative");
        }
    }

    public record Detail(String mobId, long revision, Map<String, String> fields) {
        public Detail {
            mobId = BetaDisplayValidation.id(mobId, "mobId");
            if (revision < 0) throw new IllegalArgumentException("Revision cannot be negative");
            fields = BetaDisplayValidation.map(fields, 64, "Mob Editor fields");
            fields.forEach((key, value) -> {
                BetaDisplayValidation.id(key, "fieldId");
                BetaDisplayValidation.string(value, "fieldValue");
            });
        }
    }
}
