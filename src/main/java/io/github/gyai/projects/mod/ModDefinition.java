package io.github.gyai.projects.mod;

import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.equipment.EquipmentSlot;
import io.github.gyai.projects.equipment.MetadataIds;
import io.github.gyai.projects.schema.SchemaId;
import io.github.gyai.projects.schema.SchemaVersions;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record ModDefinition(
        int schemaVersion, String modId, ModRank rank,
        Set<EquipmentSlot> allowedSlots,
        Set<AttackTag> requiredTags, Set<AttackTag> excludedTags,
        ModTagMatchPolicy tagMatchPolicy,
        String statId, double minimumValue, double maximumValue,
        ModStackingLayer stackingLayer, ModSource source,
        ModDisplayMetadata display, long definitionRevision
) {
    public ModDefinition {
        if (!SchemaVersions.isSupported(SchemaId.MOD_DEFINITION, schemaVersion)) {
            throw new IllegalArgumentException("unsupported MOD schema version");
        }
        modId = MetadataIds.requireCanonical("modId", modId);
        Objects.requireNonNull(rank, "rank");
        allowedSlots = immutableEnumSet(allowedSlots, EquipmentSlot.class);
        if (allowedSlots.isEmpty()) throw new IllegalArgumentException("allowedSlots must not be empty");
        requiredTags = immutableEnumSet(requiredTags, AttackTag.class);
        excludedTags = immutableEnumSet(excludedTags, AttackTag.class);
        if (!Collections.disjoint(requiredTags, excludedTags)) {
            throw new IllegalArgumentException("requiredTags and excludedTags overlap");
        }
        Objects.requireNonNull(tagMatchPolicy, "tagMatchPolicy");
        statId = MetadataIds.requireCanonical("statId", statId);
        if (!Double.isFinite(minimumValue) || !Double.isFinite(maximumValue)
                || minimumValue > maximumValue) {
            throw new IllegalArgumentException("MOD value range must be finite and ordered");
        }
        Objects.requireNonNull(stackingLayer, "stackingLayer");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(display, "display");
        if (definitionRevision < 0) throw new IllegalArgumentException("definitionRevision must be non-negative");
    }
    public boolean acceptsAttackTags(Set<AttackTag> actualTags) {
        if (actualTags == null || !Collections.disjoint(actualTags, excludedTags)) return false;
        return tagMatchPolicy == ModTagMatchPolicy.EXACT
                ? actualTags.equals(requiredTags)
                : actualTags.containsAll(requiredTags);
    }
    private static <E extends Enum<E>> Set<E> immutableEnumSet(Set<E> values, Class<E> type) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }
}
