package io.github.gyai.projects.ability.editor;

import io.github.gyai.projects.ability.*;
import java.util.HashSet;
import java.util.Set;

/** Binding-level validation intentionally complements, never replaces, visual constructors. */
public final class AbilityVisualCrossValidator {
    private AbilityVisualCrossValidator() { }
    public static void validate(AbilityDefinition ability, AbilityVisualDefinition visual) {
        Set<String> primitiveIds=new HashSet<>();
        for (AbilityVisualDefinition.HookBinding hook : visual.bindings()) for (AbilityVisualDefinition.Emission emission : hook.emissions()) {
            if (emission.actionIndex() >= ability.steps().size()) throw new IllegalArgumentException("Unreachable visual action index");
            AbilityDefinition.ActionSpec action=emission.actionIndex()<0?null:ability.steps().get(emission.actionIndex());
            validateReachable(ability,hook.hook(),emission.actionIndex(),action);
            for (AbilityVisualDefinition.PrimitiveSpec primitive : emission.primitives()) {
                if (!primitiveIds.add(primitive.id())) throw new IllegalArgumentException("Duplicate primitive id");
                fields(primitive, action);
            }
        }
    }
    private static void validateReachable(AbilityDefinition ability, AbilityLifecycleEvent.Hook hook, int actionIndex, AbilityDefinition.ActionSpec action) {
        boolean reachable=switch(hook) {
            case CAST, EXPIRE, CANCEL -> actionIndex==-1;
            case TELEGRAPH -> actionIndex==-1
                    ? ability.steps().stream().anyMatch(AbilityDefinition.CircleTelegraph.class::isInstance)
                    : action instanceof AbilityDefinition.CircleTelegraph;
            case HIT -> actionIndex==-1
                    ? ability.steps().stream().anyMatch(AbilityDefinition.Damage.class::isInstance)
                    : action instanceof AbilityDefinition.Damage;
            case TRAVEL -> false;
        };
        if(!reachable) throw new IllegalArgumentException("Unreachable visual hook/action binding");
    }
    private static void fields(AbilityVisualDefinition.PrimitiveSpec p, AbilityDefinition.ActionSpec action) {
        check(p.size(),action); check(p.radius(),action); check(p.length(),action); check(p.height(),action); check(p.angle(),action); check(p.startAngle(),action); check(p.sweepAngle(),action); check(p.turns(),action);
    }
    private static void check(AbilityVisualDefinition.Scalar scalar, AbilityDefinition.ActionSpec action) {
        if (!(scalar instanceof AbilityVisualDefinition.ActionField field)) return;
        if (field != AbilityVisualDefinition.ActionField.RADIUS || !(action instanceof AbilityDefinition.CircleTelegraph)) throw new IllegalArgumentException("Action field unavailable for bound action");
    }
}
