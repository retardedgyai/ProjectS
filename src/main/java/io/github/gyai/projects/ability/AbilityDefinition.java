package io.github.gyai.projects.ability;

import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageType;
import io.github.gyai.projects.transaction.DomainId;

import java.util.List;
import java.util.Objects;

/** Immutable, Bukkit-free ability data for the first runtime schema. */
public record AbilityDefinition(int schemaVersion, String id, String displayName,
                                List<ActionSpec> steps) {
    public static final int SCHEMA_VERSION = 1;

    public AbilityDefinition {
        DomainId.requireNamespaced(id, "ability id");
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported ability schema");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Blank ability display name");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        if (steps.isEmpty()) throw new IllegalArgumentException("Ability requires actions");
    }

    public sealed interface ActionSpec permits Wait, CircleTelegraph, Damage {
        String actionId();
    }
    public record Wait(int ticks) implements ActionSpec {
        @Override public String actionId() { return "wait"; }
    }
    public record CircleTelegraph(TargetSelector target, TargetSelector origin, double radius, int durationTicks,
                                  boolean lockAtCreation) implements ActionSpec {
        @Override public String actionId() { return "telegraph.circle"; }
    }
    public record Damage(TargetSelector target, DamageType damageType, DamageKind damageKind,
                         double fixedDamage, double coefficient, boolean criticalAllowed,
                         AttackMetadata metadata) implements ActionSpec {
        @Override public String actionId() { return "damage"; }
        public Damage { metadata = metadata == null ? AttackMetadata.EMPTY : metadata; }
    }
}
