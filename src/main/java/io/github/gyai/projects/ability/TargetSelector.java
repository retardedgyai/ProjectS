package io.github.gyai.projects.ability;
public enum TargetSelector {
    SELF, PRIMARY_TARGET;
    public AbilityCastContext.EntityRef select(AbilityCastContext context) {
        return this == SELF ? context.source() : context.primaryTarget();
    }
}
