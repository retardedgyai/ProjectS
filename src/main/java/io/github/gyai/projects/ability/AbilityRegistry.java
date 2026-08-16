package io.github.gyai.projects.ability;

import java.util.*;

/** Registry with validation at its public boundary; unknown actions never enter runtime. */
public final class AbilityRegistry {
    private final ActionRegistry actions;
    private final Map<String, AbilityDefinition> definitions = new LinkedHashMap<>();
    public AbilityRegistry(ActionRegistry actions) { this.actions = Objects.requireNonNull(actions); }
    public synchronized void register(AbilityDefinition definition) {
        Objects.requireNonNull(definition); actions.validate(definition);
        if (definitions.putIfAbsent(definition.id(), definition) != null) throw new IllegalArgumentException("Duplicate ability id: " + definition.id());
    }
    public synchronized Optional<AbilityDefinition> find(String id) { return Optional.ofNullable(definitions.get(id)); }
    public synchronized List<AbilityDefinition> list() { return List.copyOf(definitions.values()); }
}
