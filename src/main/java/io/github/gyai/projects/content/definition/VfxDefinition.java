package io.github.gyai.projects.content.definition;

import io.github.gyai.projects.ability.AbilityVisualDefinition;

import java.util.Objects;

/**
 * Bukkit-free schema-v1 authoring wrapper for one ability visual document.
 *
 * <p>The runtime visual model remains the single source of truth for visual
 * fields; this type only adds the content-document identity and revision
 * boundary.</p>
 */
public record VfxDefinition(
        int schemaVersion,
        String vfxId,
        long revision,
        AbilityVisualDefinition visual
) {
    public static final int SCHEMA_VERSION = 1;

    public VfxDefinition {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported VFX schema");
        }
        if (!DefinitionSupport.isNamespacedId(vfxId)) {
            throw new IllegalArgumentException("Invalid VFX ID");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("VFX revision must be non-negative");
        }
        visual = Objects.requireNonNull(visual, "visual");
        if (!vfxId.equals(visual.id())) {
            throw new IllegalArgumentException("VFX ID must match embedded visual ID");
        }
    }

    /** Alias for callers using the common definition vocabulary. */
    public String id() {
        return vfxId;
    }
}
