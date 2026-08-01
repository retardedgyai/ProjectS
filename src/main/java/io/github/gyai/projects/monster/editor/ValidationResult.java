package io.github.gyai.projects.monster.editor;

import java.util.List;

public record ValidationResult(List<String> errors) {
    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean valid() {
        return errors.isEmpty();
    }

    public String message() {
        return valid() ? "検証に成功しました" : String.join(" / ", errors);
    }
}
