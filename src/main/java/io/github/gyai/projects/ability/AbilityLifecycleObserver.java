package io.github.gyai.projects.ability;

/** Non-authoritative lifecycle boundary. Implementations must never be trusted by gameplay. */
@FunctionalInterface
public interface AbilityLifecycleObserver {
    AbilityLifecycleObserver NOOP = event -> { };
    void onLifecycle(AbilityLifecycleEvent event);
}
