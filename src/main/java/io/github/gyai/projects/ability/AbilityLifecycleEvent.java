package io.github.gyai.projects.ability;

/** Immutable snapshot supplied at the authoritative runtime transition.  Anchor may be absent. */
public record AbilityLifecycleEvent(AbilityCastContext context, Hook hook, int actionIndex,
        AbilityDefinition.ActionSpec action, AbilityCastContext.EntityRef target,
        AbilityRuntime.CancelReason cancelReason, AnchorFrame anchor) {
    public enum Hook { CAST, TELEGRAPH, TRAVEL, HIT, EXPIRE, CANCEL }
}
