package io.github.gyai.projects.combat.damage;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Immutable tags and elemental metadata that can be adapted into a damage request. */
public record AttackMetadata(
        Set<AttackTag> tags,
        ElementProfile elements
) {
    public static final AttackMetadata EMPTY =
            new AttackMetadata(Set.of(), ElementProfile.EMPTY);

    public AttackMetadata {
        tags = tags == null || tags.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(tags));
        elements = elements == null ? ElementProfile.EMPTY : elements;
    }

    public boolean hasTag(AttackTag tag) {
        return tag != null && tags.contains(tag);
    }
}
