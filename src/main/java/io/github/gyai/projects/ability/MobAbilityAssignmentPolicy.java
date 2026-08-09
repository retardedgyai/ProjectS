package io.github.gyai.projects.ability;

import io.github.gyai.projects.monster.editor.MobDefinition;
import io.github.gyai.projects.transaction.DomainId;

import java.util.List;
import java.util.Objects;

/**
 * Bukkit-free assignment boundary for Editor Mob ability IDs. Definitions may
 * survive registry changes on disk; only authoring and cast resolution consult
 * the current registry.
 */
public final class MobAbilityAssignmentPolicy {
    private final AbilityRegistry registry;

    public MobAbilityAssignmentPolicy(AbilityRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Validates the structural list and requires every authored ID to be known
     * before returning a copied definition.
     */
    public MobDefinition assign(MobDefinition definition, List<String> abilityIds) {
        Objects.requireNonNull(definition, "definition");
        MobDefinition assigned = definition.withAbilityIds(abilityIds);
        for (String abilityId : assigned.abilityIds()) {
            if (registry.find(abilityId).isEmpty()) {
                throw new IllegalArgumentException("Unknown ability id: " + abilityId);
            }
        }
        return assigned;
    }

    /** Resolves an explicitly requested assignment without choosing a fallback. */
    public Resolution resolve(MobDefinition definition, String requestedId) {
        try {
            DomainId.requireNamespaced(requestedId, "ability id");
        } catch (IllegalArgumentException | NullPointerException exception) {
            return new Resolution(Status.MALFORMED, null);
        }
        if (definition == null || !definition.abilityIds().contains(requestedId)) {
            return new Resolution(Status.UNASSIGNED, null);
        }
        AbilityDefinition ability = registry.find(requestedId).orElse(null);
        return ability == null
                ? new Resolution(Status.ASSIGNED_BUT_UNKNOWN, null)
                : new Resolution(Status.RESOLVED, ability);
    }

    public enum Status {
        MALFORMED,
        UNASSIGNED,
        ASSIGNED_BUT_UNKNOWN,
        RESOLVED
    }

    public record Resolution(Status status, AbilityDefinition definition) {
        public Resolution {
            Objects.requireNonNull(status, "status");
            if ((status == Status.RESOLVED) != (definition != null)) {
                throw new IllegalArgumentException("Resolution status/definition mismatch");
            }
        }

        public boolean resolved() {
            return status == Status.RESOLVED;
        }
    }
}
