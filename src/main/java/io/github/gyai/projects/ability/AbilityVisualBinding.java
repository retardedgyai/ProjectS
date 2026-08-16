package io.github.gyai.projects.ability;

import io.github.gyai.projects.transaction.DomainId;
/** Stable, separate ability-to-presentation association. */
public record AbilityVisualBinding(String abilityId, String visualId) {
    public AbilityVisualBinding { DomainId.requireNamespaced(abilityId, "ability id"); DomainId.requireNamespaced(visualId, "visual id"); }
}
