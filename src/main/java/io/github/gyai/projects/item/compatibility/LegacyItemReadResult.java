package io.github.gyai.projects.item.compatibility;

import java.util.List;
import java.util.Optional;

public record LegacyItemReadResult(Optional<LegacyItemView> view, List<String> issues) {
    public LegacyItemReadResult {
        view = view == null ? Optional.empty() : view;
        issues = List.copyOf(issues);
    }
    public boolean valid() { return view.isPresent() && issues.isEmpty(); }
}
