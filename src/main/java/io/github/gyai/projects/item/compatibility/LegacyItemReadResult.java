package io.github.gyai.projects.item.compatibility;

import java.util.List;
import java.util.Optional;

public record LegacyItemReadResult(
        Optional<LegacyItemView> view,
        List<String> issues,
        Status status
) {
    public LegacyItemReadResult {
        view = view == null ? Optional.empty() : view;
        issues = List.copyOf(issues);
        if (status == null) throw new IllegalArgumentException("status is required");
        if ((status == Status.READABLE) != view.isPresent()) {
            throw new IllegalArgumentException("readable status must match view presence");
        }
    }
    public boolean valid() { return status == Status.READABLE && issues.isEmpty(); }
    public enum Status { READABLE, MISSING, MALFORMED }
}
