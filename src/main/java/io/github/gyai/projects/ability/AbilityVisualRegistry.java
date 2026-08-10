package io.github.gyai.projects.ability;

import java.util.*;
/** Small immutable-definition registry; duplicate IDs are a startup error. */
public final class AbilityVisualRegistry {
    private final Map<String, AbilityVisualDefinition> definitions = new HashMap<>();
    private final AbilityVisualBindingRegistry bindings = new AbilityVisualBindingRegistry();
    public void register(AbilityVisualDefinition value) { if (definitions.putIfAbsent(value.id(), value) != null) throw new IllegalArgumentException("Duplicate visual id"); }
    public void bind(AbilityVisualBinding value) { bindings.register(value); }
    public Optional<AbilityVisualDefinition> find(String id) { return Optional.ofNullable(definitions.get(id)); }
    public Optional<AbilityVisualDefinition> resolve(String abilityId) { return bindings.findVisualId(abilityId).flatMap(this::find); }
    public Optional<String> boundVisualId(String abilityId) { return bindings.findVisualId(abilityId); }
    public Map<String, AbilityVisualDefinition> definitions() { return Collections.unmodifiableMap(new TreeMap<>(definitions)); }
}
