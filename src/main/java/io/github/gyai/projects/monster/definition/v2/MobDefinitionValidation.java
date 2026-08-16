package io.github.gyai.projects.monster.definition.v2;

import java.util.List;

public record MobDefinitionValidation(Status status, List<String> details) {
    public static final int MAX_DETAILS = 32;

    public MobDefinitionValidation {
        status = status == null ? Status.INVALID : status;
        details = List.copyOf(details == null ? List.of() : details).stream()
                .limit(MAX_DETAILS).map(MobDefinitionValidation::bounded).toList();
    }

    public boolean valid() { return status == Status.VALID; }

    public enum Status {
        VALID, INVALID, UNRESOLVED_REFERENCE, UNKNOWN_VERSION, CONFLICT, OVERSIZED
    }

    private static String bounded(String value) {
        String text = value == null ? "" : value;
        return text.length() <= 256 ? text : text.substring(0, 255) + "…";
    }
}
