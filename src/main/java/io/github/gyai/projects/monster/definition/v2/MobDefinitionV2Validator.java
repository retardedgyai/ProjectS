package io.github.gyai.projects.monster.definition.v2;

import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.combat.damage.DamageElement;
import io.github.gyai.projects.combat.damage.DamageType;
import io.github.gyai.projects.monster.definition.v2.MobDefinitionValidation.Status;
import io.github.gyai.projects.monster.definition.v2.reference.MobReferenceResolvers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class MobDefinitionV2Validator {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._:/-]{0,127}");
    private final MobReferenceResolvers resolvers;
    private final MobDefinitionV2Policy policy;

    public MobDefinitionV2Validator(
            MobReferenceResolvers resolvers,
            MobDefinitionV2Policy policy
    ) {
        this.resolvers = java.util.Objects.requireNonNull(resolvers, "resolvers");
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
    }

    public MobDefinitionValidation validate(MobDefinitionV2 definition) {
        List<String> invalid = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        if (definition == null) return result(Status.INVALID, "definition is null");
        if (definition.schemaVersion() != MobDefinitionV2.SCHEMA_VERSION) {
            return result(Status.UNKNOWN_VERSION, "unsupported schema version");
        }
        id(definition.mobId(), "mobId", invalid);
        string(definition.display().name(), "display name", invalid);
        id(definition.entityType().toLowerCase(java.util.Locale.ROOT), "entity type", invalid);
        map(definition.attributes(), "attributes", invalid);
        map(definition.display().values(), "display metadata", invalid);
        map(definition.extensions(), "extensions", invalid);
        collection(definition.attacks(), "attacks", invalid);
        collection(definition.skills(), "skills", invalid);
        collection(definition.phases(), "phases", invalid);
        collection(definition.dropReferences(), "drops", invalid);
        collection(definition.spawnRules(), "spawn rules", invalid);
        collection(definition.weaknesses(), "weaknesses", invalid);
        collection(definition.rewardReferences(), "rewards", invalid);
        definition.attributes().forEach((key, value) -> {
            id(key, "attribute", invalid);
            finite(value, "attribute " + key, invalid);
            if (value != null && value < 0) invalid.add("negative attribute: " + key);
        });
        validateAttacks(definition, invalid);
        validateSkills(definition, invalid, unresolved);
        validatePhases(definition.phases(), definition.skills().stream()
                .map(MobDefinitionV2.SkillReference::skillId).collect(java.util.stream.Collectors.toSet()), invalid);
        validateSpawns(definition, invalid, unresolved);
        validateWeaknesses(definition, invalid);
        validateCategory(definition.fireCategory(), "fire", invalid);
        validateCategory(definition.iceCategory(), "ice", invalid);
        references(definition.dropReferences(), value -> resolvers.items().resolves(value)
                        || resolvers.resources().resolves(value)
                        || resolvers.rewards().resolves(value),
                "drop", invalid, unresolved);
        references(definition.rewardReferences(), resolvers.rewards()::resolves,
                "reward", invalid, unresolved);
        id(definition.participationPolicyReference(), "participation policy", invalid);
        if (!resolvers.participationPolicies().resolves(
                definition.participationPolicyReference())) {
            unresolved.add("participation policy: "
                    + definition.participationPolicyReference());
        }
        if (!invalid.isEmpty()) return new MobDefinitionValidation(Status.INVALID, invalid);
        if (!unresolved.isEmpty()) {
            return new MobDefinitionValidation(Status.UNRESOLVED_REFERENCE, unresolved);
        }
        return new MobDefinitionValidation(Status.VALID, List.of());
    }

    private void validateAttacks(MobDefinitionV2 definition, List<String> invalid) {
        Set<String> ids = new HashSet<>();
        for (var attack : definition.attacks()) {
            id(attack.attackId(), "attack", invalid);
            if (!ids.add(attack.attackId())) invalid.add("duplicate attack: " + attack.attackId());
            finite(attack.coefficient(), "attack coefficient", invalid);
            Set<AttackTag> tags = attack.metadata().tags();
            if (attack.damageFamily() == DamageType.PHYSICAL
                    && !tags.contains(AttackTag.PHYSICAL)) {
                invalid.add("physical attack missing PHYSICAL tag: " + attack.attackId());
            }
            if (attack.damageFamily() == DamageType.MAGICAL
                    && !tags.contains(AttackTag.MAGIC)) {
                invalid.add("magical attack missing MAGIC tag: " + attack.attackId());
            }
            for (DamageElement element : DamageElement.values()) {
                double value = attack.metadata().elements().value(element);
                double scaling = attack.metadata().elements().scalingRate(element);
                finite(value, "element value", invalid);
                finite(scaling, "element scaling", invalid);
                if ((value > 0 || scaling > 0) && !tags.contains(tag(element))) {
                    invalid.add("element metadata/tag mismatch: " + attack.attackId());
                }
            }
        }
    }

    private void validateSkills(
            MobDefinitionV2 definition,
            List<String> invalid,
            List<String> unresolved
    ) {
        Set<String> ids = new HashSet<>();
        Set<String> attackIds = definition.attacks().stream()
                .map(MobDefinitionV2.AttackDefinition::attackId)
                .collect(java.util.stream.Collectors.toSet());
        for (var skill : definition.skills()) {
            id(skill.skillId(), "skill", invalid);
            if (!ids.add(skill.skillId())) invalid.add("duplicate skill: " + skill.skillId());
            finite(skill.coefficient(), "skill coefficient", invalid);
            id(skill.cooldownReference(), "cooldown reference", invalid);
            id(skill.targetSelectionReference(), "target selection reference", invalid);
            id(skill.attackMetadataReference(), "attack metadata reference", invalid);
            if (!attackIds.contains(skill.attackMetadataReference())) {
                invalid.add("missing attack metadata reference: "
                        + skill.attackMetadataReference());
            }
            if (!resolvers.skills().resolves(skill.skillId(), skill.revision())) {
                unresolved.add("skill: " + skill.skillId() + "@" + skill.revision());
            }
        }
    }

    private void validatePhases(
            List<MobDefinitionV2.PhaseDefinition> phases,
            Set<String> declaredSkills,
            List<String> invalid
    ) {
        if (phases.isEmpty()) return;
        Map<String, MobDefinitionV2.PhaseDefinition> byId = new HashMap<>();
        List<String> entries = new ArrayList<>();
        for (var phase : phases) {
            id(phase.phaseId(), "phase", invalid);
            if (byId.putIfAbsent(phase.phaseId(), phase) != null) {
                invalid.add("duplicate phase: " + phase.phaseId());
            }
            if (phase.entry()) entries.add(phase.phaseId());
            for (String skill : phase.allowedSkills()) {
                if (!declaredSkills.contains(skill)) invalid.add("missing phase skill: " + skill);
            }
        }
        if (entries.isEmpty()) {
            invalid.add("phase graph has no entry");
            return;
        }
        for (var phase : phases) {
            for (String target : phase.transitionTargets()) {
                if (!byId.containsKey(target)) invalid.add("missing phase target: " + target);
            }
        }
        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(entries);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (!reachable.add(id)) continue;
            var phase = byId.get(id);
            if (phase != null) queue.addAll(phase.transitionTargets());
        }
        for (String id : byId.keySet()) {
            if (!reachable.contains(id)) invalid.add("unreachable phase: " + id);
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String entry : entries) {
            if (cycle(entry, byId, visiting, visited)) {
                invalid.add("phase graph contains a cycle");
                break;
            }
        }
    }

    private boolean cycle(String id, Map<String, MobDefinitionV2.PhaseDefinition> byId,
                          Set<String> visiting, Set<String> visited) {
        if (visiting.contains(id)) return true;
        if (!visited.add(id)) return false;
        visiting.add(id);
        var phase = byId.get(id);
        if (phase != null) {
            for (String next : phase.transitionTargets()) {
                if (cycle(next, byId, visiting, visited)) return true;
            }
        }
        visiting.remove(id);
        return false;
    }

    private void validateSpawns(MobDefinitionV2 definition, List<String> invalid,
                                List<String> unresolved) {
        Set<String> ids = new HashSet<>();
        for (var spawn : definition.spawnRules()) {
            id(spawn.spawnId(), "spawn", invalid);
            if (!ids.add(spawn.spawnId())) invalid.add("duplicate spawn: " + spawn.spawnId());
            id(spawn.regionLocationKey(), "region", invalid);
            id(spawn.maximumActiveReference(), "maximum active reference", invalid);
            id(spawn.respawnPolicyReference(), "respawn policy reference", invalid);
            id(spawn.schedulerLifecycleReference(), "scheduler lifecycle reference", invalid);
            map(spawn.conditions(), "spawn conditions", invalid);
            if (!resolvers.regions().resolves(spawn.regionLocationKey())) {
                unresolved.add("region: " + spawn.regionLocationKey());
            }
            if (!resolvers.resources().resolves(spawn.maximumActiveReference())) {
                unresolved.add("maximum active resource: " + spawn.maximumActiveReference());
            }
            if (!resolvers.resources().resolves(spawn.respawnPolicyReference())) {
                unresolved.add("respawn policy: " + spawn.respawnPolicyReference());
            }
        }
    }

    private void validateWeaknesses(MobDefinitionV2 definition, List<String> invalid) {
        Set<DamageElement> seen = new HashSet<>();
        for (var weakness : definition.weaknesses()) {
            if (!seen.add(weakness.element())) {
                invalid.add("duplicate weakness: " + weakness.element());
            }
            finite(weakness.multiplier(), "weakness multiplier", invalid);
            if (weakness.multiplier() < 0 || weakness.multiplier() > 100) {
                invalid.add("weakness multiplier outside safety range");
            }
        }
    }

    private void validateCategory(MobDefinitionV2.ElementCategorySettings settings,
                                  String label, List<String> invalid) {
        map(settings.explicitOverrides(), label + " overrides", invalid);
        settings.explicitOverrides().forEach((key, value) -> {
            id(key, label + " override", invalid);
            finite(value, label + " override " + key, invalid);
            if (value != null && (value < 0 || value > 1_000_000)) {
                invalid.add(label + " override outside safety range: " + key);
            }
        });
    }

    private void references(List<String> values, java.util.function.Predicate<String> resolver,
                            String label, List<String> invalid, List<String> unresolved) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            id(value, label, invalid);
            if (!seen.add(value)) invalid.add("duplicate " + label + ": " + value);
            else if (!resolver.test(value)) unresolved.add(label + ": " + value);
        }
    }

    private void collection(java.util.Collection<?> values, String name, List<String> invalid) {
        if (values.size() > policy.maximumCollectionEntries()) invalid.add(name + " oversized");
    }

    private void map(Map<?, ?> values, String name, List<String> invalid) {
        if (values.size() > policy.maximumMapEntries()) invalid.add(name + " oversized");
        for (var entry : values.entrySet()) {
            string(String.valueOf(entry.getKey()), name + " key", invalid);
            string(String.valueOf(entry.getValue()), name + " value", invalid);
        }
    }

    private void id(String value, String name, List<String> invalid) {
        if (value == null || !ID.matcher(value).matches() || value.contains("..")) {
            invalid.add("invalid " + name + ": " + value);
        }
    }

    private void string(String value, String name, List<String> invalid) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length
                > policy.maximumStringBytes()) invalid.add(name + " oversized");
    }

    private static void finite(Double value, String name, List<String> invalid) {
        if (value == null || !Double.isFinite(value)) invalid.add(name + " must be finite");
    }

    private static AttackTag tag(DamageElement element) {
        return switch (element) {
            case FIRE -> AttackTag.FIRE;
            case ICE -> AttackTag.ICE;
            case LIGHTNING -> AttackTag.LIGHTNING;
        };
    }

    private static MobDefinitionValidation result(Status status, String detail) {
        return new MobDefinitionValidation(status, List.of(detail));
    }
}
