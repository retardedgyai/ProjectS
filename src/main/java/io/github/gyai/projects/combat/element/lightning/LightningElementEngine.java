package io.github.gyai.projects.combat.element.lightning;

/** Contract placeholder only; Wave 1 has no approved lightning behavior. */
public interface LightningElementEngine {
    boolean enabled();

    Result evaluate(Input input);

    record Input(String subjectId) {
        public Input {
            if (subjectId == null || subjectId.isBlank() || subjectId.length() > 128) {
                throw new IllegalArgumentException(
                        "subjectId must be 1..128 non-blank characters");
            }
        }
    }

    record Result(boolean applied, String reason) {
        public Result {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
        }
    }
}
