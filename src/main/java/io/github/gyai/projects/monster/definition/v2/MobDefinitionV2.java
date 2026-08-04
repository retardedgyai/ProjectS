package io.github.gyai.projects.monster.definition.v2;

import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.DamageElement;
import io.github.gyai.projects.combat.damage.DamageType;
import io.github.gyai.projects.combat.element.ElementTargetCategory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable Mob schema v2 document. It deliberately contains no Bukkit types. */
public record MobDefinitionV2(
        int schemaVersion,
        String mobId,
        long revision,
        DisplayMetadata display,
        String entityType,
        MobCategory category,
        Map<String, Double> attributes,
        List<AttackDefinition> attacks,
        List<SkillReference> skills,
        List<PhaseDefinition> phases,
        List<String> dropReferences,
        List<SpawnRule> spawnRules,
        List<ElementWeakness> weaknesses,
        ElementCategorySettings fireCategory,
        ElementCategorySettings iceCategory,
        List<String> rewardReferences,
        String participationPolicyReference,
        Map<String, String> extensions
) {
    public static final int SCHEMA_VERSION = 2;

    public MobDefinitionV2 {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("MobDefinitionV2 schema version must be 2");
        }
        mobId = require(mobId, "mobId");
        if (revision < 0) throw new IllegalArgumentException("revision must be non-negative");
        display = Objects.requireNonNull(display, "display");
        entityType = require(entityType, "entityType");
        category = Objects.requireNonNull(category, "category");
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
        attacks = List.copyOf(attacks == null ? List.of() : attacks);
        skills = List.copyOf(skills == null ? List.of() : skills);
        phases = List.copyOf(phases == null ? List.of() : phases);
        dropReferences = List.copyOf(dropReferences == null ? List.of() : dropReferences);
        spawnRules = List.copyOf(spawnRules == null ? List.of() : spawnRules);
        weaknesses = List.copyOf(weaknesses == null ? List.of() : weaknesses);
        fireCategory = Objects.requireNonNull(fireCategory, "fireCategory");
        iceCategory = Objects.requireNonNull(iceCategory, "iceCategory");
        rewardReferences = List.copyOf(rewardReferences == null ? List.of() : rewardReferences);
        participationPolicyReference = require(
                participationPolicyReference, "participationPolicyReference");
        extensions = Map.copyOf(extensions == null ? Map.of() : extensions);
    }

    public MobDefinitionV2 withRevision(long newRevision) {
        return new MobDefinitionV2(schemaVersion, mobId, newRevision, display,
                entityType, category, attributes, attacks, skills, phases,
                dropReferences, spawnRules, weaknesses, fireCategory, iceCategory,
                rewardReferences, participationPolicyReference, extensions);
    }

    public enum MobCategory { NORMAL, ELITE, MINIBOSS, BOSS }

    public enum AttackClassification { DIRECT, PERIODIC, AUTOMATIC }

    public record DisplayMetadata(String name, String nameplatePolicy,
                                  Map<String, String> values) {
        public DisplayMetadata {
            name = require(name, "display.name");
            nameplatePolicy = require(nameplatePolicy, "display.nameplatePolicy");
            values = Map.copyOf(values == null ? Map.of() : values);
        }
    }

    /** Uses Track C metadata directly; tags are never inferred from IDs or weapons. */
    public record AttackDefinition(
            String attackId,
            DamageType damageFamily,
            AttackClassification classification,
            AttackMetadata metadata,
            double coefficient
    ) {
        public AttackDefinition {
            attackId = require(attackId, "attackId");
            damageFamily = Objects.requireNonNull(damageFamily, "damageFamily");
            classification = Objects.requireNonNull(classification, "classification");
            metadata = Objects.requireNonNull(metadata, "metadata");
            finite(coefficient, "attack coefficient");
        }
    }

    public record SkillReference(
            String skillId,
            long revision,
            String trigger,
            String cooldownReference,
            String targetSelectionReference,
            String attackMetadataReference,
            double coefficient,
            String enabledCondition
    ) {
        public SkillReference {
            skillId = require(skillId, "skillId");
            if (revision < 0) throw new IllegalArgumentException("skill revision");
            trigger = require(trigger, "trigger");
            cooldownReference = require(cooldownReference, "cooldownReference");
            targetSelectionReference = require(targetSelectionReference, "targetSelectionReference");
            attackMetadataReference = require(attackMetadataReference, "attackMetadataReference");
            finite(coefficient, "skill coefficient");
            enabledCondition = require(enabledCondition, "enabledCondition");
        }
    }

    public record PhaseDefinition(
            String phaseId,
            boolean entry,
            String entryCondition,
            String exitCondition,
            Set<String> allowedSkills,
            Set<String> transitionTargets,
            List<String> cleanupActions,
            String invulnerabilityPolicyReference
    ) {
        public PhaseDefinition {
            phaseId = require(phaseId, "phaseId");
            entryCondition = require(entryCondition, "entryCondition");
            exitCondition = require(exitCondition, "exitCondition");
            allowedSkills = Set.copyOf(allowedSkills == null ? Set.of() : allowedSkills);
            transitionTargets = Set.copyOf(
                    transitionTargets == null ? Set.of() : transitionTargets);
            cleanupActions = List.copyOf(cleanupActions == null ? List.of() : cleanupActions);
            invulnerabilityPolicyReference = require(
                    invulnerabilityPolicyReference, "invulnerabilityPolicyReference");
        }
    }

    public record SpawnRule(
            String spawnId,
            String regionLocationKey,
            String maximumActiveReference,
            String respawnPolicyReference,
            Map<String, String> conditions,
            String schedulerLifecycleReference
    ) {
        public SpawnRule {
            spawnId = require(spawnId, "spawnId");
            regionLocationKey = require(regionLocationKey, "regionLocationKey");
            maximumActiveReference = require(maximumActiveReference, "maximumActiveReference");
            respawnPolicyReference = require(respawnPolicyReference, "respawnPolicyReference");
            conditions = Map.copyOf(conditions == null ? Map.of() : conditions);
            schedulerLifecycleReference = require(
                    schedulerLifecycleReference, "schedulerLifecycleReference");
        }
    }

    public record ElementWeakness(DamageElement element, double multiplier) {
        public ElementWeakness {
            element = Objects.requireNonNull(element, "element");
            finite(multiplier, "weakness multiplier");
            if (multiplier < 0) throw new IllegalArgumentException("weakness multiplier");
        }
    }

    /** Explicit-only per-Mob tuning; absent keys inherit Track C category policy. */
    public record ElementCategorySettings(
            ElementTargetCategory category,
            Map<String, Double> explicitOverrides
    ) {
        public ElementCategorySettings {
            category = Objects.requireNonNull(category, "category");
            explicitOverrides = Map.copyOf(
                    explicitOverrides == null ? Map.of() : explicitOverrides);
        }
    }

    private static String require(String value, String name) {
        String checked = Objects.requireNonNull(value, name);
        if (checked.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return checked;
    }

    private static void finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
