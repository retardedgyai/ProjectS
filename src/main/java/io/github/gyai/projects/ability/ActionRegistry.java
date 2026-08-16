package io.github.gyai.projects.ability;

import java.util.*;

public final class ActionRegistry {
    private final Map<String, ActionExecutor<?>> executors = new HashMap<>();
    public <T extends AbilityDefinition.ActionSpec> void register(String id, Class<T> type, ActionExecutor<T> executor) {
        if (id == null || id.isBlank() || executors.putIfAbsent(id, new TypedExecutor<>(type, executor)) != null) throw new IllegalArgumentException("Duplicate or blank action id");
    }
    public void validate(AbilityDefinition definition) {
        for (var step : definition.steps()) executor(step).validate(step);
    }
    public ActionExecutor<AbilityDefinition.ActionSpec> executor(AbilityDefinition.ActionSpec step) {
        ActionExecutor<?> result = executors.get(step.actionId());
        if (result == null) throw new IllegalArgumentException("Unknown ability action: " + step.actionId());
        @SuppressWarnings("unchecked") var typed = (ActionExecutor<AbilityDefinition.ActionSpec>) result;
        return typed;
    }
    public interface ActionExecutor<T extends AbilityDefinition.ActionSpec> {
        void validate(T spec);
        void execute(T spec, AbilityRuntime.Cast cast);
    }
    private record TypedExecutor<T extends AbilityDefinition.ActionSpec>(Class<T> type, ActionExecutor<T> delegate)
            implements ActionExecutor<AbilityDefinition.ActionSpec> {
        @Override public void validate(AbilityDefinition.ActionSpec spec) { if (!type.isInstance(spec)) throw new IllegalArgumentException("Action type mismatch"); delegate.validate(type.cast(spec)); }
        @Override public void execute(AbilityDefinition.ActionSpec spec, AbilityRuntime.Cast cast) { delegate.execute(type.cast(spec), cast); }
    }
}
