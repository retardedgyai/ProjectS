package io.github.gyai.projects.content.definition;

import io.github.gyai.projects.combat.damage.DamageElement;

import java.util.List;
import java.util.Map;

/**
 * Bukkit-free schema-v1 document for one mob identity.
 *
 * <p>Encounter lifecycle belongs to {@link EncounterDefinition}; this document
 * intentionally contains no phases, spawn rules, arena, or reward data.</p>
 */
public record MobDefinition(
        int schemaVersion,
        String mobId,
        long revision,
        Presentation presentation,
        String entityType,
        Category category,
        Stats stats,
        Map<DamageElement, Double> elementValues,
        Map<DamageElement, Double> resistanceValues,
        List<String> equipmentReferences,
        List<String> abilityReferences
) {
    public static final int SCHEMA_VERSION = 1;

    public MobDefinition {
        elementValues = DefinitionSupport.immutableMap(elementValues);
        resistanceValues = DefinitionSupport.immutableMap(resistanceValues);
        equipmentReferences = DefinitionSupport.immutableList(equipmentReferences);
        abilityReferences = DefinitionSupport.immutableList(abilityReferences);
    }

    /** Alias useful to callers that use the common id vocabulary. */
    public String id() {
        return mobId;
    }

    /** Alias for callers that describe the two maps as elemental data. */
    public Map<DamageElement, Double> elements() {
        return elementValues;
    }

    public Map<DamageElement, Double> resistances() {
        return resistanceValues;
    }

    public List<String> abilities() {
        return abilityReferences;
    }

    public enum Category {
        NORMAL,
        ELITE,
        MINIBOSS,
        BOSS
    }

    /** Presentation is deliberately a bounded, text-only authoring value. */
    public record Presentation(String displayName, String nameplatePolicy) {
        public Presentation(String displayName) {
            this(displayName, "default");
        }
    }

    /** Numeric values remain data-only; authoring bounds are reported by the validator. */
    public record Stats(
            double maxHealth,
            double attackDamage,
            double movementSpeed,
            double knockbackResistance,
            double followRange,
            double scale
    ) {
    }
}
