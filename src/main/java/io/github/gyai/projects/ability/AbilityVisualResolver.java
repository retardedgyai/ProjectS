package io.github.gyai.projects.ability;

import java.util.Optional;

/** The sole presentation lookup seam shared by player and mob casts. */
@FunctionalInterface
public interface AbilityVisualResolver {
    Optional<AbilityVisualDefinition> resolve(String abilityId);
}
