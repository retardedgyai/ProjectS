package io.github.gyai.projects.content.definition;

import io.github.gyai.projects.ability.AbilityVisualDefinition;
import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.combat.damage.DamageElement;
import io.github.gyai.projects.combat.damage.DamageType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic authoring validator for the schema-v1 content documents. */
public final class ContentDefinitionValidator {
    public static final int SCHEMA_VERSION = 1;

    /** Explicit authoring bounds; these are not gameplay balance defaults. */
    public static final Bounds DEFAULT_BOUNDS = new Bounds(
            96,
            1_000_000_000L,
            1_000_000_000L,
            100.0,
            1_000_000L,
            512.0,
            1200,
            1_000_000,
            128.0,
            32.0,
            100.0,
            1_000_000_000L,
            1_000_000_000L,
            128.0,
            1_000_000L,
            1_000_000L,
            16
    );

    public static final Policy DEFAULT_POLICY = new Policy(DEFAULT_BOUNDS);

    private final Policy policy;

    public ContentDefinitionValidator() {
        this(DEFAULT_POLICY);
    }

    public ContentDefinitionValidator(Policy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public Policy policy() {
        return policy;
    }

    /** Validate a complete supplied catalog. All collections are treated as authoring input. */
    public ValidationResult validate(Catalog catalog) {
        List<Issue> issues = new ArrayList<>();
        if (catalog == null) {
            issue(issues, Codes.MISSING_VALUE, "catalog", "catalog is required");
            return new ValidationResult(issues);
        }

        Map<String, MobDefinition> mobs = indexMobs(catalog.mobs(), issues);
        Map<String, AbilityDefinition> abilities = indexAbilities(catalog.abilities(), issues);
        Map<String, EncounterDefinition> encounters = indexEncounters(
                catalog.encounters(), issues);
        Map<String, AbilityVisualDefinition> visuals = indexVisuals(
                catalog.visuals(), issues);
        Set<String> rewards = indexRewards(catalog.rewardReferences(), issues);
        Set<String> equipmentIds = indexEquipmentIds(catalog.equipmentIds(), issues);
        Set<String> entityTypeIds = indexEntityTypeIds(catalog.validEntityTypeIds(), issues);

        for (int i = 0; i < catalog.mobs().size(); i++) {
            MobDefinition definition = catalog.mobs().get(i);
            String path = "mobs[" + i + "]";
            if (definition == null) {
                issue(issues, Codes.MISSING_VALUE, path, "mob definition is required");
            } else {
                validateMob(definition, path, abilities, equipmentIds, entityTypeIds, issues);
            }
        }
        for (int i = 0; i < catalog.abilities().size(); i++) {
            AbilityDefinition definition = catalog.abilities().get(i);
            String path = "abilities[" + i + "]";
            if (definition == null) {
                issue(issues, Codes.MISSING_VALUE, path, "ability definition is required");
            } else {
                validateAbility(definition, path, visuals, issues);
            }
        }
        for (int i = 0; i < catalog.encounters().size(); i++) {
            EncounterDefinition definition = catalog.encounters().get(i);
            String path = "encounters[" + i + "]";
            if (definition == null) {
                issue(issues, Codes.MISSING_VALUE, path,
                        "encounter definition is required");
            } else {
                validateEncounter(definition, path, mobs, abilities, rewards, issues);
            }
        }
        return new ValidationResult(issues);
    }

    /** Convenience overload for callers that do not need to author an encounter catalog. */
    public ValidationResult validate(Collection<MobDefinition> mobs,
                                     Collection<AbilityDefinition> abilities,
                                     Collection<EncounterDefinition> encounters,
                                     Collection<AbilityVisualDefinition> visuals,
                                     Collection<String> rewardReferences) {
        return validate(new Catalog(toList(mobs), toList(abilities), toList(encounters),
                toList(visuals), toList(rewardReferences), List.of(), List.of()));
    }

    /** Convenience overload for a single encounter catalog. */
    public ValidationResult validate(Collection<MobDefinition> mobs,
                                     Collection<AbilityDefinition> abilities,
                                     Collection<EncounterDefinition> encounters) {
        return validate(mobs, abilities, encounters, List.of(), List.of());
    }

    private Map<String, MobDefinition> indexMobs(List<MobDefinition> definitions,
                                                  List<Issue> issues) {
        Map<String, MobDefinition> result = new LinkedHashMap<>();
        for (int i = 0; i < definitions.size(); i++) {
            MobDefinition definition = definitions.get(i);
            if (definition == null || !isNamespacedId(definition.mobId())) {
                continue;
            }
            if (result.putIfAbsent(definition.mobId(), definition) != null) {
                issue(issues, Codes.DUPLICATE_ID, "mobs[" + i + "].mobId",
                        "duplicate mob id " + definition.mobId());
            }
        }
        return result;
    }

    private Map<String, AbilityDefinition> indexAbilities(List<AbilityDefinition> definitions,
                                                            List<Issue> issues) {
        Map<String, AbilityDefinition> result = new LinkedHashMap<>();
        for (int i = 0; i < definitions.size(); i++) {
            AbilityDefinition definition = definitions.get(i);
            if (definition == null || !isNamespacedId(definition.abilityId())) {
                continue;
            }
            if (result.putIfAbsent(definition.abilityId(), definition) != null) {
                issue(issues, Codes.DUPLICATE_ID, "abilities[" + i + "].abilityId",
                        "duplicate ability id " + definition.abilityId());
            }
        }
        return result;
    }

    private Map<String, EncounterDefinition> indexEncounters(
            List<EncounterDefinition> definitions, List<Issue> issues) {
        Map<String, EncounterDefinition> result = new LinkedHashMap<>();
        for (int i = 0; i < definitions.size(); i++) {
            EncounterDefinition definition = definitions.get(i);
            if (definition == null || !isNamespacedId(definition.encounterId())) {
                continue;
            }
            if (result.putIfAbsent(definition.encounterId(), definition) != null) {
                issue(issues, Codes.DUPLICATE_ID, "encounters[" + i + "].encounterId",
                        "duplicate encounter id " + definition.encounterId());
            }
        }
        return result;
    }

    private Map<String, AbilityVisualDefinition> indexVisuals(
            List<AbilityVisualDefinition> definitions, List<Issue> issues) {
        Map<String, AbilityVisualDefinition> result = new LinkedHashMap<>();
        for (int i = 0; i < definitions.size(); i++) {
            AbilityVisualDefinition definition = definitions.get(i);
            String path = "visuals[" + i + "]";
            if (definition == null) {
                issue(issues, Codes.MISSING_VALUE, path, "visual definition is required");
            } else if (!isNamespacedId(definition.id())) {
                issue(issues, Codes.INVALID_NAMESPACED_ID, path + ".id",
                        "visual id must be lower-case and namespaced");
            } else if (result.putIfAbsent(definition.id(), definition) != null) {
                issue(issues, Codes.DUPLICATE_ID, path + ".id",
                        "duplicate visual id " + definition.id());
            }
        }
        return result;
    }

    private Set<String> indexRewards(List<String> references, List<Issue> issues) {
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < references.size(); i++) {
            String reference = references.get(i);
            String path = "rewards[" + i + "]";
            if (!isNamespacedId(reference)) {
                issue(issues, Codes.INVALID_NAMESPACED_ID, path,
                        "reward reference must be lower-case and namespaced");
            } else if (!result.add(reference)) {
                issue(issues, Codes.DUPLICATE_ID, path,
                        "duplicate reward reference " + reference);
            }
        }
        return result;
    }

    private Set<String> indexEquipmentIds(List<String> references, List<Issue> issues) {
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < references.size(); i++) {
            String reference = references.get(i);
            String path = "equipmentIds[" + i + "]";
            if (!isNamespacedId(reference)) {
                issue(issues, Codes.INVALID_NAMESPACED_ID, path,
                        "equipment id must be lower-case and namespaced");
            } else if (!result.add(reference)) {
                issue(issues, Codes.DUPLICATE_ID, path,
                        "duplicate equipment id " + reference);
            }
        }
        return result;
    }

    private Set<String> indexEntityTypeIds(List<String> references, List<Issue> issues) {
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < references.size(); i++) {
            String reference = references.get(i);
            String path = "validEntityTypeIds[" + i + "]";
            if (!isNamespacedId(reference)) {
                issue(issues, Codes.INVALID_NAMESPACED_ID, path,
                        "entity type id must be lower-case and namespaced");
            } else if (!result.add(reference)) {
                issue(issues, Codes.DUPLICATE_ID, path,
                        "duplicate entity type id " + reference);
            }
        }
        return result;
    }

    private void validateMob(MobDefinition definition, String path,
                             Map<String, AbilityDefinition> abilities,
                             Set<String> equipmentIds,
                             Set<String> entityTypeIds,
                             List<Issue> issues) {
        validateSchema(definition.schemaVersion(), path + ".schemaVersion", issues);
        validateNamespacedId(definition.mobId(), path + ".mobId", issues);
        validateRevision(definition.revision(), path + ".revision", issues);
        if (definition.presentation() == null) {
            issue(issues, Codes.MISSING_VALUE, path + ".presentation",
                    "presentation is required");
        } else {
            validateText(definition.presentation().displayName(),
                    path + ".presentation.displayName", 128, issues);
            validateText(definition.presentation().nameplatePolicy(),
                    path + ".presentation.nameplatePolicy", 64, issues);
        }
        validateNamespacedId(definition.entityType(), path + ".entityType", issues);
        if (isNamespacedId(definition.entityType())
                && !entityTypeIds.contains(definition.entityType())) {
            issue(issues, Codes.UNRESOLVED_ENTITY_TYPE, path + ".entityType",
                    "entity type is not present in the supplied catalog");
        }
        if (definition.category() == null) {
            issue(issues, Codes.MISSING_VALUE, path + ".category", "category is required");
        }
        validateStats(definition.stats(), path + ".stats", issues);
        validateElementMap(definition.elementValues(), path + ".elementValues", issues);
        validateElementMap(definition.resistanceValues(), path + ".resistanceValues", issues);

        validateNamespacedReferences(definition.equipmentReferences(),
                path + ".equipmentReferences", Codes.INVALID_NAMESPACED_ID, issues);
        for (int i = 0; i < definition.equipmentReferences().size(); i++) {
            String reference = definition.equipmentReferences().get(i);
            if (isNamespacedId(reference) && !equipmentIds.contains(reference)) {
                issue(issues, Codes.UNRESOLVED_EQUIPMENT_REFERENCE,
                        path + ".equipmentReferences[" + i + "]",
                        "equipment reference is not present in the supplied catalog");
            }
        }
        validateAbilityReferences(definition.abilityReferences(), path + ".abilityReferences",
                abilities, issues);
    }

    private void validateStats(MobDefinition.Stats stats, String path, List<Issue> issues) {
        if (stats == null) {
            issue(issues, Codes.MISSING_VALUE, path, "stats are required");
            return;
        }
        finiteAndRange(stats.maxHealth(), path + ".maxHealth", 0.0,
                policy.bounds().maxHealth(), false, issues);
        finiteAndRange(stats.attackDamage(), path + ".attackDamage", 0.0,
                policy.bounds().maxAttackDamage(), true, issues);
        finiteAndRange(stats.movementSpeed(), path + ".movementSpeed", 0.0,
                policy.bounds().maxMovementSpeed(), false, issues);
        finiteAndRange(stats.knockbackResistance(), path + ".knockbackResistance", 0.0,
                policy.bounds().maxKnockbackResistance(), true, issues);
        finiteAndRange(stats.followRange(), path + ".followRange", 0.0,
                policy.bounds().maxFollowRange(), false, issues);
        finiteAndRange(stats.scale(), path + ".scale", 0.0,
                policy.bounds().maxScale(), false, issues);
    }

    private void validateElementMap(Map<?, ?> values, String path, List<Issue> issues) {
        if (values == null) {
            issue(issues, Codes.MISSING_VALUE, path, "element map is required");
            return;
        }
        List<Map.Entry<?, ?>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
        for (Map.Entry<?, ?> entry : entries) {
            String entryPath = path + "[" + String.valueOf(entry.getKey()) + "]";
            if (entry.getKey() == null) {
                issue(issues, Codes.MISSING_VALUE, entryPath, "element key is required");
            } else if (!(entry.getKey() instanceof DamageElement)) {
                issue(issues, Codes.INVALID_VALUE, entryPath, "element key is unsupported");
            }
            if (!(entry.getValue() instanceof Number number)) {
                issue(issues, Codes.MISSING_VALUE, entryPath, "element value is required");
            } else {
                finiteAndRange(number.doubleValue(), entryPath, 0.0,
                        policy.bounds().maxElementValue(), true, issues);
            }
        }
    }

    private void validateAbility(AbilityDefinition definition, String path,
                                 Map<String, AbilityVisualDefinition> visuals,
                                 List<Issue> issues) {
        validateSchema(definition.schemaVersion(), path + ".schemaVersion", issues);
        validateNamespacedId(definition.abilityId(), path + ".abilityId", issues);
        validateRevision(definition.revision(), path + ".revision", issues);
        validateText(definition.displayName(), path + ".displayName", 128, issues);

        AbilityDefinition.Timing timing = definition.timing();
        if (timing == null) {
            issue(issues, Codes.MISSING_VALUE, path + ".timing", "timing is required");
        } else {
            validateTicks(timing.castTicks(), path + ".timing.castTicks", true, issues);
            validateTicks(timing.recoveryTicks(), path + ".timing.recoveryTicks", true, issues);
            validateTicks(timing.cooldownTicks(), path + ".timing.cooldownTicks", true, issues);
        }
        AbilityDefinition.Targeting targeting = definition.targeting();
        if (targeting == null) {
            issue(issues, Codes.MISSING_VALUE, path + ".targeting", "targeting is required");
        } else {
            if (targeting.selector() == null) {
                issue(issues, Codes.MISSING_VALUE, path + ".targeting.selector",
                        "target selector is required");
            }
            finiteAndRange(targeting.maxRange(), path + ".targeting.maxRange", 0.0,
                    policy.bounds().maxTargetRange(), false, issues);
        }
        if (definition.timeline() == null || definition.timeline().isEmpty()) {
            issue(issues, Codes.EMPTY_DEFINITION, path + ".timeline",
                    "ability timeline must not be empty");
        }
        if (definition.interruptPolicy() == null) {
            issue(issues, Codes.MISSING_VALUE, path + ".interruptPolicy",
                    "interrupt policy is required");
        }
            if (definition.visualReference() != null) {
                validateNamespacedId(definition.visualReference(), path + ".visualReference", issues);
            if (isNamespacedId(definition.visualReference())
                    && !visuals.containsKey(definition.visualReference())) {
                issue(issues, Codes.UNRESOLVED_VISUAL_REFERENCE,
                        path + ".visualReference",
                        "visual reference is not present in the supplied catalog");
            }
        }

        Set<String> stepIds = new HashSet<>();
        List<AbilityDefinition.TimelineAction> actions = definition.timeline();
        if (actions == null) {
            return;
        }
        for (int i = 0; i < actions.size(); i++) {
            AbilityDefinition.TimelineAction action = actions.get(i);
            String actionPath = path + ".timeline[" + i + "]";
            if (action == null) {
                issue(issues, Codes.MISSING_VALUE, actionPath, "timeline action is required");
                continue;
            }
            validateLocalId(action.stepId(), actionPath + ".stepId", issues);
            if (isLocalId(action.stepId()) && !stepIds.add(action.stepId())) {
                issue(issues, Codes.DUPLICATE_LOCAL_ID, actionPath + ".stepId",
                        "duplicate timeline step id " + action.stepId());
            }
            if (action instanceof AbilityDefinition.Wait wait) {
                validateTicks(wait.ticks(), actionPath + ".ticks", true, issues);
            } else if (action instanceof AbilityDefinition.Telegraph telegraph) {
                if (telegraph.origin() == null) {
                    issue(issues, Codes.MISSING_VALUE, actionPath + ".origin",
                            "telegraph origin is required");
                }
                validateTicks(telegraph.durationTicks(), actionPath + ".durationTicks", false,
                        issues);
                validateShape(telegraph.shape(), actionPath + ".shape", issues);
            } else if (action instanceof AbilityDefinition.Damage damage) {
                if (damage.target() == null) {
                    issue(issues, Codes.MISSING_VALUE, actionPath + ".target",
                            "damage target is required");
                }
                validateShape(damage.shape(), actionPath + ".shape", issues);
                if (damage.damageType() == null) {
                    issue(issues, Codes.MISSING_VALUE, actionPath + ".damageType",
                            "damage type is required");
                }
                if (damage.damageKind() == null) {
                    issue(issues, Codes.MISSING_VALUE, actionPath + ".damageKind",
                            "damage kind is required");
                } else if (damage.criticalAllowed()
                        && !damage.damageKind().criticalAllowed()) {
                    issue(issues, Codes.CONTRADICTORY_DEFINITION,
                            actionPath + ".criticalAllowed",
                            "damage kind does not allow critical damage");
                }
                if (damage.metadata() == null) {
                    issue(issues, Codes.MISSING_VALUE, actionPath + ".metadata",
                            "damage metadata is required");
                } else {
                    validateDamageMetadata(damage.damageType(), damage.metadata(),
                            actionPath + ".metadata", issues);
                }
                finiteAndRange(damage.fixedDamage(), actionPath + ".fixedDamage", 0.0,
                        policy.bounds().maxDamage(), true, issues);
                finiteAndRange(damage.coefficient(), actionPath + ".coefficient", 0.0,
                        policy.bounds().maxCoefficient(), true, issues);
                if (Double.isFinite(damage.fixedDamage()) && Double.isFinite(damage.coefficient())
                        && damage.fixedDamage() == 0.0 && damage.coefficient() == 0.0) {
                    issue(issues, Codes.CONTRADICTORY_DEFINITION, actionPath,
                            "damage action has no damage component");
                }
            } else if (action instanceof AbilityDefinition.Charge charge) {
                if (charge.target() == null) {
                    issue(issues, Codes.MISSING_VALUE, actionPath + ".target",
                            "charge target is required");
                }
                validateTicks(charge.durationTicks(), actionPath + ".durationTicks", false,
                        issues);
                validateShape(charge.path(), actionPath + ".path", issues);
                finiteAndRange(charge.speed(), actionPath + ".speed", 0.0,
                        policy.bounds().maxSpeed(), false, issues);
            } else if (action instanceof AbilityDefinition.Knockback knockback) {
                if (knockback.target() == null) {
                    issue(issues, Codes.MISSING_VALUE, actionPath + ".target",
                            "knockback target is required");
                }
                validateShape(knockback.shape(), actionPath + ".shape", issues);
                finiteAndRange(knockback.horizontalStrength(),
                        actionPath + ".horizontalStrength", 0.0,
                        policy.bounds().maxKnockbackStrength(), true, issues);
                finiteAndRange(knockback.verticalStrength(), actionPath + ".verticalStrength",
                        -policy.bounds().maxKnockbackStrength(),
                        policy.bounds().maxKnockbackStrength(), true, issues);
                if (Double.isFinite(knockback.horizontalStrength())
                        && Double.isFinite(knockback.verticalStrength())
                        && knockback.horizontalStrength() == 0.0
                        && knockback.verticalStrength() == 0.0) {
                    issue(issues, Codes.CONTRADICTORY_DEFINITION, actionPath,
                            "knockback action has no impulse");
                }
            }
        }
    }

    private void validateDamageMetadata(DamageType damageType, AttackMetadata metadata,
                                        String path, List<Issue> issues) {
        if (damageType == DamageType.PHYSICAL) {
            if (!metadata.hasTag(AttackTag.PHYSICAL)) {
                issue(issues, Codes.DAMAGE_TYPE_TAG_MISMATCH, path + ".tags",
                        "PHYSICAL damage requires PHYSICAL attack tag");
            }
            if (metadata.hasTag(AttackTag.MAGIC)) {
                issue(issues, Codes.CONTRADICTORY_DEFINITION, path + ".tags",
                        "PHYSICAL damage forbids MAGIC attack tag");
            }
        } else if (damageType == DamageType.MAGICAL) {
            if (!metadata.hasTag(AttackTag.MAGIC)) {
                issue(issues, Codes.DAMAGE_TYPE_TAG_MISMATCH, path + ".tags",
                        "MAGICAL damage requires MAGIC attack tag");
            }
            if (metadata.hasTag(AttackTag.PHYSICAL)) {
                issue(issues, Codes.CONTRADICTORY_DEFINITION, path + ".tags",
                        "MAGICAL damage forbids PHYSICAL attack tag");
            }
        }

        for (DamageElement element : DamageElement.values()) {
            AttackTag elementTag = tagFor(element);
            double value = metadata.elements().value(element);
            double scalingRate = metadata.elements().scalingRate(element);
            finiteAndRange(value, path + ".elements.values[" + element + "]", 0.0,
                    policy.bounds().maxElementValue(), true, issues);
            finiteAndRange(scalingRate, path + ".elements.scalingRates[" + element + "]", 0.0,
                    policy.bounds().maxElementMultiplier(), true, issues);
            if (value > 0.0 && !metadata.hasTag(elementTag)) {
                issue(issues, Codes.ELEMENT_TAG_MISMATCH,
                        path + ".elements.values[" + element + "]",
                        "positive " + element + " value requires " + elementTag + " attack tag");
            }
            if (scalingRate > 0.0 && !metadata.hasTag(elementTag)) {
                issue(issues, Codes.ELEMENT_TAG_MISMATCH,
                        path + ".elements.scalingRates[" + element + "]",
                        "positive " + element + " scaling rate requires " + elementTag
                                + " attack tag");
            }
        }
    }

    private static AttackTag tagFor(DamageElement element) {
        return switch (element) {
            case FIRE -> AttackTag.FIRE;
            case ICE -> AttackTag.ICE;
            case LIGHTNING -> AttackTag.LIGHTNING;
        };
    }

    private void validateShape(AbilityDefinition.RelativeShape shape, String path,
                               List<Issue> issues) {
        if (shape == null) {
            issue(issues, Codes.MISSING_VALUE, path, "relative shape is required");
            return;
        }
        if (shape instanceof AbilityDefinition.Circle circle) {
            finiteAndRange(circle.radius(), path + ".radius", 0.0,
                    policy.bounds().maxShapeRadius(), false, issues);
        } else if (shape instanceof AbilityDefinition.Donut donut) {
            finiteAndRange(donut.innerRadius(), path + ".innerRadius", 0.0,
                    policy.bounds().maxShapeRadius(), true, issues);
            finiteAndRange(donut.outerRadius(), path + ".outerRadius", 0.0,
                    policy.bounds().maxShapeRadius(), false, issues);
            if (Double.isFinite(donut.innerRadius()) && Double.isFinite(donut.outerRadius())
                    && donut.innerRadius() >= donut.outerRadius()) {
                issue(issues, Codes.CONTRADICTORY_DEFINITION, path,
                        "donut inner radius must be less than outer radius");
            }
        } else if (shape instanceof AbilityDefinition.Line line) {
            finiteAndRange(line.length(), path + ".length", 0.0,
                    policy.bounds().maxShapeLength(), false, issues);
            finiteAndRange(line.width(), path + ".width", 0.0,
                    policy.bounds().maxShapeWidth(), false, issues);
        }
    }

    private void validateEncounter(EncounterDefinition definition, String path,
                                   Map<String, MobDefinition> mobs,
                                   Map<String, AbilityDefinition> abilities,
                                   Set<String> rewards,
                                   List<Issue> issues) {
        validateSchema(definition.schemaVersion(), path + ".schemaVersion", issues);
        validateNamespacedId(definition.encounterId(), path + ".encounterId", issues);
        validateRevision(definition.revision(), path + ".revision", issues);
        if (definition.actors() == null || definition.actors().isEmpty()) {
            issue(issues, Codes.EMPTY_DEFINITION, path + ".actors",
                    "encounter must contain at least one actor");
        }
        if (definition.phases() == null || definition.phases().isEmpty()) {
            issue(issues, Codes.EMPTY_DEFINITION, path + ".phases",
                    "encounter must contain at least one phase");
        }
        if (definition.resetPolicy() == null) {
            issue(issues, Codes.MISSING_VALUE, path + ".resetPolicy",
                    "reset policy is required");
        } else {
            validateResetPolicy(definition.resetPolicy(), path + ".resetPolicy", issues);
        }
        Set<String> actorIds = new HashSet<>();
        Map<String, MobDefinition> actorMobs = new LinkedHashMap<>();
        for (int i = 0; i < definition.actors().size(); i++) {
            EncounterDefinition.Actor actor = definition.actors().get(i);
            String actorPath = path + ".actors[" + i + "]";
            if (actor == null) {
                issue(issues, Codes.MISSING_VALUE, actorPath, "actor is required");
                continue;
            }
            validateLocalId(actor.actorId(), actorPath + ".actorId", issues);
            if (isLocalId(actor.actorId()) && !actorIds.add(actor.actorId())) {
                issue(issues, Codes.DUPLICATE_LOCAL_ID, actorPath + ".actorId",
                        "duplicate actor id " + actor.actorId());
            }
            validateNamespacedId(actor.mobReference(), actorPath + ".mobReference", issues);
            if (isNamespacedId(actor.mobReference())
                    && !mobs.containsKey(actor.mobReference())) {
                issue(issues, Codes.UNRESOLVED_MOB_REFERENCE,
                        actorPath + ".mobReference",
                        "mob reference is not present in the supplied catalog");
            }
            if (isLocalId(actor.actorId()) && isNamespacedId(actor.mobReference())) {
                MobDefinition mob = mobs.get(actor.mobReference());
                if (mob != null) {
                    actorMobs.putIfAbsent(actor.actorId(), mob);
                }
            }
        }

        if (definition.victoryPolicy() == null) {
            issue(issues, Codes.MISSING_VALUE, path + ".victoryPolicy",
                    "victory policy is required");
        } else {
            validateCondition(definition.victoryPolicy().condition(), path + ".victoryPolicy.condition",
                    actorIds, issues);
        }
        if (definition.failurePolicy() == null) {
            issue(issues, Codes.MISSING_VALUE, path + ".failurePolicy",
                    "failure policy is required");
        } else {
            validateCondition(definition.failurePolicy().condition(), path + ".failurePolicy.condition",
                    actorIds, issues);
            if (definition.failurePolicy().mode() == null) {
                issue(issues, Codes.MISSING_VALUE, path + ".failurePolicy.mode",
                        "failure mode is required");
            }
        }
        validateNamespacedReferences(definition.rewardReferences(),
                path + ".rewardReferences", Codes.INVALID_NAMESPACED_ID, issues);
        for (int i = 0; i < definition.rewardReferences().size(); i++) {
            String reference = definition.rewardReferences().get(i);
            if (isNamespacedId(reference) && !rewards.contains(reference)) {
                issue(issues, Codes.UNRESOLVED_REWARD_REFERENCE,
                        path + ".rewardReferences[" + i + "]",
                        "reward reference is not present in the supplied catalog");
            }
        }

        List<EncounterDefinition.Phase> phases = definition.phases();
        Map<String, Integer> phaseIndexes = new LinkedHashMap<>();
        Set<String> phaseIds = new HashSet<>();
        int entryCount = 0;
        for (int i = 0; i < phases.size(); i++) {
            EncounterDefinition.Phase phase = phases.get(i);
            String phasePath = path + ".phases[" + i + "]";
            if (phase == null) {
                issue(issues, Codes.MISSING_VALUE, phasePath, "phase is required");
                continue;
            }
            validateLocalId(phase.phaseId(), phasePath + ".phaseId", issues);
            if (isLocalId(phase.phaseId())) {
                if (!phaseIds.add(phase.phaseId())) {
                    issue(issues, Codes.DUPLICATE_LOCAL_ID, phasePath + ".phaseId",
                            "duplicate phase id " + phase.phaseId());
                } else {
                    phaseIndexes.put(phase.phaseId(), i);
                }
            }
            if (phase.entry()) {
                entryCount++;
            }
            validatePhase(phase, phasePath, abilities, actorIds, actorMobs, issues);
        }
        if (entryCount == 0) {
            issue(issues, Codes.NO_ENTRY_PHASE, path + ".phases",
                    "encounter must have exactly one entry phase");
        } else if (entryCount > 1) {
            issue(issues, Codes.MULTIPLE_ENTRY_PHASES, path + ".phases",
                    "encounter must have exactly one entry phase");
        }
        validatePhaseGraph(phases, phaseIndexes, entryCount, path, issues);
        validatePhaseStateTransitions(phases, phaseIndexes, path, issues);
    }

    private void validatePhase(EncounterDefinition.Phase phase, String path,
                               Map<String, AbilityDefinition> abilities,
                               Set<String> actorIds,
                               Map<String, MobDefinition> actorMobs,
                               List<Issue> issues) {
        if (phase.actorBehaviors() == null || phase.actorBehaviors().isEmpty()) {
            issue(issues, Codes.MISSING_ACTOR_BEHAVIOR, path + ".actorBehaviors",
                    "phase must define at least one actor behavior");
        }
        Set<String> behaviorActors = new HashSet<>();
        for (int i = 0; i < phase.actorBehaviors().size(); i++) {
            EncounterDefinition.ActorBehavior behavior = phase.actorBehaviors().get(i);
            String behaviorPath = path + ".actorBehaviors[" + i + "]";
            if (behavior == null) {
                issue(issues, Codes.MISSING_VALUE, behaviorPath, "actor behavior is required");
                continue;
            }
            validateLocalId(behavior.actorId(), behaviorPath + ".actorId", issues);
            if (isLocalId(behavior.actorId())
                    && !actorIds.contains(behavior.actorId())) {
                issue(issues, Codes.UNRESOLVED_ACTOR_REFERENCE, behaviorPath + ".actorId",
                        "actor reference is not present in the encounter");
            }
            if (isLocalId(behavior.actorId())
                    && !behaviorActors.add(behavior.actorId())) {
                issue(issues, Codes.DUPLICATE_ACTOR_BEHAVIOR, behaviorPath + ".actorId",
                        "phase contains more than one behavior for the actor");
            }
            MobDefinition actorMob = actorMobs.get(behavior.actorId());
            Set<String> allowed = behavior.allowedAbilityReferences();
            if (behavior.state() == null) {
                issue(issues, Codes.MISSING_VALUE, behaviorPath + ".state",
                        "actor state is required");
            }
            if (behavior.state() == EncounterDefinition.ActorState.DOWNED) {
                if (allowed != null && !allowed.isEmpty()) {
                    issue(issues, Codes.DOWNED_ABILITY_POOL,
                            behaviorPath + ".allowedAbilityReferences",
                            "downed actor behavior must not select abilities");
                }
                if (behavior.abilitySelectionPolicy() != null) {
                    issue(issues, Codes.DOWNED_ABILITY_POOL,
                            behaviorPath + ".abilitySelectionPolicy",
                            "downed actor behavior must not define an ability selection policy");
                }
            } else {
                if (allowed == null || allowed.isEmpty()) {
                    issue(issues, Codes.MISSING_ACTOR_ABILITY,
                            behaviorPath + ".allowedAbilityReferences",
                            "active actor behavior must allow at least one ability");
                } else {
                    int index = 0;
                    for (String reference : sortedStrings(allowed)) {
                        String referencePath = behaviorPath + ".allowedAbilityReferences[" + index++ + "]";
                        validateActorAbilityReference(reference, referencePath, actorMob,
                                abilities, issues);
                    }
                }
                validateSelectionPolicy(behavior.abilitySelectionPolicy(),
                        behaviorPath + ".abilitySelectionPolicy",
                        allowed == null ? Set.of() : allowed, actorMob, abilities, issues);
            }
        }
        for (String actorId : sortedStrings(actorIds)) {
            if (!behaviorActors.contains(actorId)) {
                issue(issues, Codes.MISSING_ACTOR_BEHAVIOR, path + ".actorBehaviors",
                        "phase is missing behavior for actor " + actorId);
            }
        }
        Set<String> transitionIds = new HashSet<>();
        for (int i = 0; i < phase.transitions().size(); i++) {
            EncounterDefinition.Transition transition = phase.transitions().get(i);
            String transitionPath = path + ".transitions[" + i + "]";
            if (transition == null) {
                issue(issues, Codes.MISSING_VALUE, transitionPath, "transition is required");
                continue;
            }
            validateLocalId(transition.transitionId(), transitionPath + ".transitionId", issues);
            if (isLocalId(transition.transitionId())
                    && !transitionIds.add(transition.transitionId())) {
                issue(issues, Codes.DUPLICATE_LOCAL_ID,
                        transitionPath + ".transitionId",
                        "duplicate transition id " + transition.transitionId());
            }
            validateLocalId(transition.targetPhaseId(), transitionPath + ".targetPhaseId", issues);
            validateCondition(transition.condition(), transitionPath + ".condition", actorIds, issues);
            validateActorStateTransitions(transition.actorStateTransitions(),
                    transitionPath + ".actorStateTransitions", actorIds, issues);
        }
    }

    private void validateSelectionPolicy(EncounterDefinition.AbilitySelectionPolicy selection,
                                         String path, Set<String> allowed,
                                         MobDefinition actorMob,
                                         Map<String, AbilityDefinition> abilities,
                                         List<Issue> issues) {
        if (selection == null) {
            issue(issues, Codes.MISSING_VALUE, path, "ability selection policy is required");
            return;
        }
        Set<String> selected = new LinkedHashSet<>();
        if (selection instanceof EncounterDefinition.OrderedSelection ordered) {
            if (ordered.abilityReferences() == null || ordered.abilityReferences().isEmpty()) {
                issue(issues, Codes.INVALID_SELECTION, path,
                        "ordered selection must contain at least one ability");
            }
            for (int i = 0; i < ordered.abilityReferences().size(); i++) {
                String reference = ordered.abilityReferences().get(i);
                String entryPath = path + ".abilityReferences[" + i + "]";
                validateSelectionReference(reference, entryPath, allowed, actorMob, abilities,
                        selected, issues);
            }
        } else if (selection instanceof EncounterDefinition.WeightedSelection weighted) {
            if (weighted.entries() == null || weighted.entries().isEmpty()) {
                issue(issues, Codes.INVALID_SELECTION, path,
                        "weighted selection must contain at least one ability");
            }
            double total = 0.0;
            for (int i = 0; i < weighted.entries().size(); i++) {
                EncounterDefinition.WeightedAbility entry = weighted.entries().get(i);
                String entryPath = path + ".entries[" + i + "]";
                if (entry == null) {
                    issue(issues, Codes.MISSING_VALUE, entryPath, "weighted ability is required");
                    continue;
                }
                finiteAndRange(entry.weight(), entryPath + ".weight", 0.0,
                        policy.bounds().maxWeight(), false, issues);
                if (Double.isFinite(entry.weight()) && entry.weight() <= 0.0) {
                    issue(issues, Codes.INVALID_WEIGHT, entryPath + ".weight",
                            "weight must be greater than zero");
                }
                validateSelectionReference(entry.abilityReference(),
                        entryPath + ".abilityReference", allowed, actorMob, abilities, selected,
                        issues);
                if (Double.isFinite(entry.weight())) {
                    total += entry.weight();
                }
            }
            if (!Double.isFinite(total) || total <= 0.0) {
                issue(issues, Codes.INVALID_WEIGHT, path,
                        "weighted selection must have a finite positive total");
            }
        }
        if (!selected.equals(allowed)) {
            Set<String> missing = new LinkedHashSet<>(allowed);
            missing.removeAll(selected);
            if (!missing.isEmpty()) {
                issue(issues, Codes.CONTRADICTORY_DEFINITION, path,
                        "allowed abilities are not selectable: " + sortedStrings(missing));
            }
        }
    }

    private void validateSelectionReference(String reference, String path, Set<String> allowed,
                                            MobDefinition actorMob,
                                            Map<String, AbilityDefinition> abilities,
                                            Set<String> selected, List<Issue> issues) {
        validateActorAbilityReference(reference, path, actorMob, abilities, issues);
        if (!isNamespacedId(reference)) {
            return;
        }
        if (!allowed.contains(reference)) {
            issue(issues, Codes.INVALID_SELECTION, path,
                    "selected ability is not in the phase allow-list");
        }
        if (!selected.add(reference)) {
            issue(issues, Codes.INVALID_SELECTION, path,
                    "ability appears more than once in the selection policy");
        }
    }

    private void validateActorAbilityReference(String reference, String path,
                                                MobDefinition actorMob,
                                                Map<String, AbilityDefinition> abilities,
                                                List<Issue> issues) {
        validateNamespacedId(reference, path, issues);
        if (!isNamespacedId(reference)) {
            return;
        }
        if (!abilities.containsKey(reference)) {
            issue(issues, Codes.UNRESOLVED_ABILITY_REFERENCE, path,
                    "ability reference is not present in the supplied catalog");
        } else if (actorMob != null && !actorMob.abilityReferences().contains(reference)) {
            issue(issues, Codes.MISSING_PHASE_ABILITY, path,
                    "ability reference is not present in the actor mob definition");
        }
    }

    private void validatePhaseGraph(List<EncounterDefinition.Phase> phases,
                                    Map<String, Integer> phaseIndexes,
                                    int entryCount, String path, List<Issue> issues) {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        Map<String, List<GraphEdge>> edges = new LinkedHashMap<>();
        for (String phaseId : phaseIndexes.keySet()) {
            graph.put(phaseId, new LinkedHashSet<>());
            edges.put(phaseId, new ArrayList<>());
        }
        for (int i = 0; i < phases.size(); i++) {
            EncounterDefinition.Phase phase = phases.get(i);
            if (phase == null || !phaseIndexes.containsKey(phase.phaseId())) {
                continue;
            }
            Set<String> targets = graph.get(phase.phaseId());
            for (int j = 0; j < phase.transitions().size(); j++) {
                EncounterDefinition.Transition transition = phase.transitions().get(j);
                if (transition == null) {
                    continue;
                }
                if (!phaseIndexes.containsKey(transition.targetPhaseId())) {
                    issue(issues, Codes.MISSING_PHASE_REFERENCE,
                            path + ".phases[" + i + "].transitions[" + j + "].targetPhaseId",
                            "transition target phase is not present in the encounter");
                } else {
                    targets.add(transition.targetPhaseId());
                    edges.get(phase.phaseId()).add(new GraphEdge(
                            transition.targetPhaseId(),
                            guaranteesPhaseProgress(transition.condition())));
                }
            }
        }

        Set<String> entries = new LinkedHashSet<>();
        for (EncounterDefinition.Phase phase : phases) {
            if (phase != null && phase.entry() && phaseIndexes.containsKey(phase.phaseId())) {
                entries.add(phase.phaseId());
            }
        }
        Set<String> reachable = new HashSet<>();
        if (!entries.isEmpty()) {
            Deque<String> pending = new ArrayDeque<>(entries);
            while (!pending.isEmpty()) {
                String phaseId = pending.removeFirst();
                if (!reachable.add(phaseId)) {
                    continue;
                }
                for (String target : graph.getOrDefault(phaseId, Set.of())) {
                    pending.addLast(target);
                }
            }
        }
        for (Map.Entry<String, Integer> entry : phaseIndexes.entrySet()) {
            if (!reachable.contains(entry.getKey())) {
                issue(issues, Codes.UNREACHABLE_PHASE,
                        path + ".phases[" + entry.getValue() + "].phaseId",
                        "phase is not reachable from an entry phase");
            }
        }
        if (entryCount > 0) {
            Set<String> cycleNodes = new HashSet<>();
            detectUnprogressingCycles(edges, reachable, cycleNodes);
            for (String phaseId : sortedStrings(cycleNodes)) {
                issue(issues, Codes.PHASE_CYCLE,
                        path + ".phases[" + phaseIndexes.get(phaseId) + "].phaseId",
                        "reachable phase cycle has no guaranteed positive phase-relative delay");
            }
        }
    }

    /**
     * Cycles are valid phase behavior. This intentionally conservative guard
     * rejects only cycles made entirely from transitions that do not require a
     * positive PHASE-relative delay on every loop.
     */
    private void detectUnprogressingCycles(Map<String, List<GraphEdge>> graph,
                                            Set<String> reachable, Set<String> cycleNodes) {
        Set<String> visited = new HashSet<>();
        Map<String, Integer> stackPositions = new HashMap<>();
        List<String> path = new ArrayList<>();
        Deque<GraphTraversalFrame> stack = new ArrayDeque<>();

        // Iterate in graph insertion order rather than the hash-set order of reachable so the
        // traversal itself is deterministic. The result is sorted before it becomes issues.
        for (String node : graph.keySet()) {
            if (!reachable.contains(node) || visited.contains(node)) {
                continue;
            }
            stack.push(new GraphTraversalFrame(node));
            stackPositions.put(node, path.size());
            path.add(node);
            while (!stack.isEmpty()) {
                GraphTraversalFrame frame = stack.peek();
                List<GraphEdge> outgoing = graph.getOrDefault(frame.node(), List.of());
                if (frame.nextEdgeIndex() >= outgoing.size()) {
                    stack.pop();
                    stackPositions.remove(frame.node());
                    path.removeLast();
                    visited.add(frame.node());
                    continue;
                }

                GraphEdge edge = outgoing.get(frame.nextEdgeIndex());
                frame.advance();
                if (edge.guaranteedProgress() || !graph.containsKey(edge.target())) {
                    continue;
                }

                Integer targetPosition = stackPositions.get(edge.target());
                if (targetPosition != null) {
                    for (int i = targetPosition; i < path.size(); i++) {
                        cycleNodes.add(path.get(i));
                    }
                } else if (!visited.contains(edge.target())) {
                    stack.push(new GraphTraversalFrame(edge.target()));
                    stackPositions.put(edge.target(), path.size());
                    path.add(edge.target());
                }
            }
        }
    }

    private static final class GraphTraversalFrame {
        private final String node;
        private int nextEdgeIndex;

        private GraphTraversalFrame(String node) {
            this.node = node;
        }

        private String node() {
            return node;
        }

        private int nextEdgeIndex() {
            return nextEdgeIndex;
        }

        private void advance() {
            nextEdgeIndex++;
        }
    }

    private boolean guaranteesPhaseProgress(EncounterDefinition.Condition condition) {
        return guaranteesPhaseProgress(condition, 0);
    }

    private boolean guaranteesPhaseProgress(EncounterDefinition.Condition condition, int depth) {
        if (condition == null || depth > policy.bounds().maxConditionDepth()) {
            return false;
        }
        if (condition instanceof EncounterDefinition.ElapsedTicksAtLeast elapsed) {
            return elapsed.clock() == EncounterDefinition.Clock.PHASE
                    && elapsed.ticks() > 0
                    && elapsed.ticks() <= policy.bounds().maxLongTicks();
        }
        if (condition instanceof EncounterDefinition.All all) {
            return all.conditions() != null
                    && all.conditions().stream()
                    .anyMatch(child -> guaranteesPhaseProgress(child, depth + 1));
        }
        if (condition instanceof EncounterDefinition.Any any) {
            return any.conditions() != null
                    && !any.conditions().isEmpty()
                    && any.conditions().stream()
                    .allMatch(child -> guaranteesPhaseProgress(child, depth + 1));
        }
        return false;
    }

    private record GraphEdge(String target, boolean guaranteedProgress) {
    }

    private void validateResetPolicy(EncounterDefinition.ResetPolicy reset, String path,
                                     List<Issue> issues) {
        finiteAndRange(reset.leashRadius(), path + ".leashRadius", 0.0,
                policy.bounds().maxLeashRadius(), false, issues);
        validateTicks(reset.resetAfterNoTargetTicks(), path + ".resetAfterNoTargetTicks", true,
                issues);
        if (reset.leashRadius() == 0.0 && reset.resetOnLeash()) {
            issue(issues, Codes.CONTRADICTORY_DEFINITION, path + ".leashRadius",
                    "reset-on-leash requires a positive leash radius");
        }
    }

    private void validateCondition(EncounterDefinition.Condition condition, String path,
                                   Set<String> actorIds, List<Issue> issues) {
        validateCondition(condition, path, actorIds, issues, 0);
    }

    private void validateCondition(EncounterDefinition.Condition condition, String path,
                                   Set<String> actorIds, List<Issue> issues, int depth) {
        if (condition == null) {
            issue(issues, Codes.INVALID_CONDITION, path, "condition is required");
            return;
        }
        if (depth > policy.bounds().maxConditionDepth()) {
            issue(issues, Codes.NUMBER_OUT_OF_RANGE, path,
                    "condition nesting exceeds the authoring bound");
            return;
        }
        if (condition instanceof EncounterDefinition.ActorHealthRatioAtMost health) {
            validateLocalId(health.actorId(), path + ".actorId", issues);
            if (isLocalId(health.actorId()) && !actorIds.contains(health.actorId())) {
                issue(issues, Codes.UNRESOLVED_ACTOR_REFERENCE, path + ".actorId",
                        "actor reference is not present in the encounter");
            }
            finiteAndRange(health.ratio(), path + ".ratio", 0.0, 1.0, true, issues);
        } else if (condition instanceof EncounterDefinition.ElapsedTicksAtLeast elapsed) {
            if (elapsed.clock() == null) {
                issue(issues, Codes.MISSING_VALUE, path + ".clock", "elapsed clock is required");
            }
            if (elapsed.ticks() < 0) {
                issue(issues, Codes.NUMBER_OUT_OF_RANGE, path + ".ticks",
                        "elapsed ticks must be non-negative");
            } else if (elapsed.ticks() > policy.bounds().maxLongTicks()) {
                issue(issues, Codes.NUMBER_OUT_OF_RANGE, path + ".ticks",
                        "elapsed ticks exceeds the authoring bound");
            }
        } else if (condition instanceof EncounterDefinition.All all) {
            validateCompoundConditions(all.conditions(), path, actorIds, issues, depth);
        } else if (condition instanceof EncounterDefinition.Any any) {
            validateCompoundConditions(any.conditions(), path, actorIds, issues, depth);
        }
    }

    private void validateActorStateTransitions(
            List<EncounterDefinition.ActorStateTransition> transitions,
            String path, Set<String> actorIds, List<Issue> issues) {
        Set<String> seenActors = new HashSet<>();
        for (int i = 0; i < transitions.size(); i++) {
            EncounterDefinition.ActorStateTransition transition = transitions.get(i);
            String transitionPath = path + "[" + i + "]";
            if (transition == null) {
                issue(issues, Codes.MISSING_VALUE, transitionPath,
                        "actor state transition is required");
                continue;
            }
            validateLocalId(transition.actorId(), transitionPath + ".actorId", issues);
            if (isLocalId(transition.actorId())
                    && !actorIds.contains(transition.actorId())) {
                issue(issues, Codes.UNRESOLVED_ACTOR_REFERENCE,
                        transitionPath + ".actorId",
                        "actor reference is not present in the encounter");
            }
            if (isLocalId(transition.actorId())
                    && !seenActors.add(transition.actorId())) {
                issue(issues, Codes.DUPLICATE_STATE_TRANSITION,
                        transitionPath + ".actorId",
                        "transition contains more than one state effect for the actor");
            }
            if (transition.from() == null) {
                issue(issues, Codes.MISSING_VALUE, transitionPath + ".from",
                        "source actor state is required");
            }
            if (transition.to() == null) {
                issue(issues, Codes.MISSING_VALUE, transitionPath + ".to",
                        "target actor state is required");
            }
            if (transition.from() == transition.to() && transition.from() != null) {
                issue(issues, Codes.INVALID_STATE_TRANSITION, transitionPath,
                        "actor state transition must change state");
            }
            boolean enteringDown = transition.from() == EncounterDefinition.ActorState.ACTIVE
                    && transition.to() == EncounterDefinition.ActorState.DOWNED;
            boolean leavingDown = transition.from() == EncounterDefinition.ActorState.DOWNED
                    && transition.to() == EncounterDefinition.ActorState.ACTIVE;
            if ((enteringDown || leavingDown) && transition.downControlPolicy() == null) {
                issue(issues, Codes.MISSING_DOWN_CONTROL_POLICY,
                        transitionPath + ".downControlPolicy",
                        "down boundary transition requires its canonical control policy");
            } else if (enteringDown && transition.downControlPolicy()
                    != EncounterDefinition.DownControlPolicy.ENTER_DOWN) {
                issue(issues, Codes.INVALID_DOWN_CONTROL_POLICY,
                        transitionPath + ".downControlPolicy",
                        "entering down must cancel ability, clear current CC, and suppress without buffering");
            } else if (leavingDown && transition.downControlPolicy()
                    != EncounterDefinition.DownControlPolicy.EXIT_DOWN) {
                issue(issues, Codes.INVALID_DOWN_CONTROL_POLICY,
                        transitionPath + ".downControlPolicy",
                        "leaving down must unsuppress CC without restoring or buffering old CC");
            } else if (!enteringDown && !leavingDown
                    && transition.downControlPolicy() != null) {
                issue(issues, Codes.INVALID_DOWN_CONTROL_POLICY,
                        transitionPath + ".downControlPolicy",
                        "down control policy is only valid on an ACTIVE/DOWNED boundary");
            }
        }
    }

    private void validatePhaseStateTransitions(List<EncounterDefinition.Phase> phases,
                                               Map<String, Integer> phaseIndexes,
                                               String path, List<Issue> issues) {
        for (int i = 0; i < phases.size(); i++) {
            EncounterDefinition.Phase source = phases.get(i);
            if (source == null || !phaseIndexes.containsKey(source.phaseId())) {
                continue;
            }
            Map<String, EncounterDefinition.ActorState> sourceStates = actorStates(source);
            for (int j = 0; j < source.transitions().size(); j++) {
                EncounterDefinition.Transition transition = source.transitions().get(j);
                if (transition == null || !phaseIndexes.containsKey(transition.targetPhaseId())) {
                    continue;
                }
                EncounterDefinition.Phase target = phases.get(phaseIndexes.get(transition.targetPhaseId()));
                Map<String, EncounterDefinition.ActorState> targetStates = actorStates(target);
                Map<String, EncounterDefinition.ActorStateTransition> effects = new LinkedHashMap<>();
                for (EncounterDefinition.ActorStateTransition effect
                        : transition.actorStateTransitions()) {
                    if (effect != null) {
                        effects.put(effect.actorId(), effect);
                    }
                }
                String transitionPath = path + ".phases[" + i + "].transitions[" + j + "]";
                for (Map.Entry<String, EncounterDefinition.ActorState> entry : sourceStates.entrySet()) {
                    String actorId = entry.getKey();
                    EncounterDefinition.ActorState targetState = targetStates.get(actorId);
                    if (targetState == null || entry.getValue() == targetState) {
                        continue;
                    }
                    EncounterDefinition.ActorStateTransition effect = effects.get(actorId);
                    if (effect == null) {
                        issue(issues, Codes.MISSING_STATE_TRANSITION,
                                transitionPath + ".actorStateTransitions",
                                "phase actor state change requires an explicit typed state transition");
                    } else if (effect.from() != entry.getValue() || effect.to() != targetState) {
                        issue(issues, Codes.INVALID_STATE_TRANSITION,
                                transitionPath + ".actorStateTransitions",
                                "typed actor state transition does not match source and target phases");
                    }
                }
                for (EncounterDefinition.ActorStateTransition effect : transition.actorStateTransitions()) {
                    if (effect == null) {
                        continue;
                    }
                    EncounterDefinition.ActorState sourceState = sourceStates.get(effect.actorId());
                    EncounterDefinition.ActorState targetState = targetStates.get(effect.actorId());
                    if (sourceState == null || targetState == null
                            || effect.from() != sourceState || effect.to() != targetState) {
                        issue(issues, Codes.INVALID_STATE_TRANSITION,
                                transitionPath + ".actorStateTransitions",
                                "typed actor state transition does not match source and target phases");
                    }
                }
            }
        }
    }

    private Map<String, EncounterDefinition.ActorState> actorStates(EncounterDefinition.Phase phase) {
        Map<String, EncounterDefinition.ActorState> states = new LinkedHashMap<>();
        for (EncounterDefinition.ActorBehavior behavior : phase.actorBehaviors()) {
            if (behavior != null && behavior.actorId() != null && behavior.state() != null) {
                states.putIfAbsent(behavior.actorId(), behavior.state());
            }
        }
        return states;
    }

    private void validateCompoundConditions(List<EncounterDefinition.Condition> conditions,
                                            String path, Set<String> actorIds,
                                            List<Issue> issues, int depth) {
        if (conditions == null || conditions.isEmpty()) {
            issue(issues, Codes.INVALID_CONDITION, path,
                    "compound condition must contain at least one condition");
            return;
        }
        for (int i = 0; i < conditions.size(); i++) {
            validateCondition(conditions.get(i), path + ".conditions[" + i + "]",
                    actorIds, issues, depth + 1);
        }
    }

    private void validateAbilityReferences(List<String> references, String path,
                                           Map<String, AbilityDefinition> abilities,
                                           List<Issue> issues) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < references.size(); i++) {
            String reference = references.get(i);
            String referencePath = path + "[" + i + "]";
            validateNamespacedId(reference, referencePath, issues);
            if (isNamespacedId(reference) && !seen.add(reference)) {
                issue(issues, Codes.DUPLICATE_REFERENCE, referencePath,
                        "duplicate ability reference " + reference);
            }
            if (isNamespacedId(reference) && !abilities.containsKey(reference)) {
                issue(issues, Codes.UNRESOLVED_ABILITY_REFERENCE, referencePath,
                        "ability reference is not present in the supplied catalog");
            }
        }
    }

    private void validateNamespacedReferences(List<String> references, String path,
                                              String invalidCode, List<Issue> issues) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < references.size(); i++) {
            String reference = references.get(i);
            if (!isNamespacedId(reference)) {
                issue(issues, invalidCode, path + "[" + i + "]",
                        "reference must be lower-case and namespaced");
            } else if (!seen.add(reference)) {
                issue(issues, Codes.DUPLICATE_REFERENCE, path + "[" + i + "]",
                        "duplicate reference " + reference);
            }
        }
    }

    private void validateSchema(int schemaVersion, String path, List<Issue> issues) {
        if (schemaVersion != SCHEMA_VERSION) {
            issue(issues, Codes.UNSUPPORTED_SCHEMA_VERSION, path,
                    "only schema version 1 is supported");
        }
    }

    private void validateRevision(long revision, String path, List<Issue> issues) {
        if (revision < 0) {
            issue(issues, Codes.NEGATIVE_REVISION, path,
                    "revision must be non-negative");
        } else if (revision > policy.bounds().maxLongRevision()) {
            issue(issues, Codes.NUMBER_OUT_OF_RANGE, path,
                    "revision exceeds the authoring bound");
        }
    }

    private void validateNamespacedId(String value, String path, List<Issue> issues) {
        if (!isNamespacedId(value)) {
            issue(issues, Codes.INVALID_NAMESPACED_ID, path,
                    "id must be lower-case, namespaced, and bounded");
        }
    }

    private void validateLocalId(String value, String path, List<Issue> issues) {
        if (!isLocalId(value)) {
            issue(issues, Codes.INVALID_LOCAL_ID, path,
                    "local id must match [a-z][a-z0-9_-]{0,31} and the configured id bound");
        }
    }

    private boolean isNamespacedId(String value) {
        return value != null
                && value.length() <= policy.bounds().maxIdLength()
                && value.equals(value.toLowerCase(Locale.ROOT))
                && DefinitionSupport.NAMESPACED_ID.matcher(value).matches()
                && !value.contains("..")
                && !value.contains("//")
                && !value.endsWith("/");
    }

    private boolean isLocalId(String value) {
        return value != null
                && value.length() <= policy.bounds().maxIdLength()
                && DefinitionSupport.LOCAL_ID.matcher(value).matches();
    }

    private void validateText(String value, String path, int maxLength, List<Issue> issues) {
        if (value == null || value.isBlank()) {
            issue(issues, Codes.MISSING_VALUE, path, "text value is required");
        } else if (value.length() > maxLength) {
            issue(issues, Codes.NUMBER_OUT_OF_RANGE, path,
                    "text value exceeds the authoring bound");
        }
    }

    private void validateTicks(long ticks, String path, boolean allowZero, List<Issue> issues) {
        if (ticks < 0 || !allowZero && ticks == 0) {
            issue(issues, Codes.NUMBER_OUT_OF_RANGE, path,
                    allowZero ? "ticks must be non-negative" : "ticks must be positive");
        } else if (ticks > policy.bounds().maxTicks()) {
            issue(issues, Codes.NUMBER_OUT_OF_RANGE, path,
                    "ticks exceed the authoring bound");
        }
    }

    private void finiteAndRange(double value, String path, double minimum, double maximum,
                                boolean inclusiveMinimum, List<Issue> issues) {
        if (!Double.isFinite(value)) {
            issue(issues, Codes.NON_FINITE_NUMBER, path, "number must be finite");
        } else if (value < minimum || (!inclusiveMinimum && value == minimum) || value > maximum) {
            issue(issues, Codes.NUMBER_OUT_OF_RANGE, path,
                    "number is outside the authoring bound");
        }
    }

    private static void issue(List<Issue> issues, String code, String path, String detail) {
        issues.add(new Issue(code, path, detail));
    }

    private static List<String> sortedStrings(Collection<String> values) {
        List<String> result = new ArrayList<>(values);
        result.sort(Comparator.nullsFirst(String::compareTo));
        return result;
    }

    private static <T> List<T> toList(Collection<? extends T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record Catalog(
            List<MobDefinition> mobs,
            List<AbilityDefinition> abilities,
            List<EncounterDefinition> encounters,
            List<AbilityVisualDefinition> visuals,
            List<String> rewardReferences,
            List<String> equipmentIds,
            List<String> validEntityTypeIds
    ) {
        public Catalog {
            mobs = DefinitionSupport.immutableList(mobs);
            abilities = DefinitionSupport.immutableList(abilities);
            encounters = DefinitionSupport.immutableList(encounters);
            visuals = DefinitionSupport.immutableList(visuals);
            rewardReferences = DefinitionSupport.immutableList(rewardReferences);
            equipmentIds = DefinitionSupport.immutableList(equipmentIds);
            validEntityTypeIds = deterministicEntityTypeIds(validEntityTypeIds);
        }

        public Catalog(Collection<MobDefinition> mobs, Collection<AbilityDefinition> abilities,
                       Collection<EncounterDefinition> encounters,
                       Collection<AbilityVisualDefinition> visuals,
                       Collection<String> rewardReferences,
                       Collection<String> equipmentIds,
                       Collection<String> validEntityTypeIds) {
            this(mobs == null ? List.of() : List.copyOf(mobs),
                    abilities == null ? List.of() : List.copyOf(abilities),
                    encounters == null ? List.of() : List.copyOf(encounters),
                    visuals == null ? List.of() : List.copyOf(visuals),
                    rewardReferences == null ? List.of() : List.copyOf(rewardReferences),
                    equipmentIds == null ? List.of() : List.copyOf(equipmentIds),
                    validEntityTypeIds == null ? List.of() : new ArrayList<>(validEntityTypeIds));
        }

        public Catalog(Collection<MobDefinition> mobs, Collection<AbilityDefinition> abilities,
                       Collection<EncounterDefinition> encounters,
                       Collection<AbilityVisualDefinition> visuals,
                       Collection<String> rewardReferences,
                       Collection<String> equipmentIds) {
            this(mobs, abilities, encounters, visuals, rewardReferences, equipmentIds, List.of());
        }

        public Catalog(Collection<MobDefinition> mobs, Collection<AbilityDefinition> abilities,
                       Collection<EncounterDefinition> encounters,
                       Collection<AbilityVisualDefinition> visuals,
                       Collection<String> rewardReferences) {
            this(mobs, abilities, encounters, visuals, rewardReferences, List.of());
        }

        public static Catalog empty() {
            return new Catalog(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of());
        }

        private static List<String> deterministicEntityTypeIds(List<String> values) {
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            List<String> sorted = new ArrayList<>(values);
            sorted.sort(Comparator.nullsFirst(String::compareTo));
            return DefinitionSupport.immutableList(sorted);
        }
    }

    public record Policy(Bounds bounds) {
        public Policy {
            bounds = Objects.requireNonNull(bounds, "bounds");
        }
    }

    public record Bounds(
            int maxIdLength,
            long maxLongRevision,
            double maxHealth,
            double maxElementValue,
            double maxAttackDamage,
            double maxMovementSpeed,
            int maxTicks,
            long maxLongTicks,
            double maxTargetRange,
            double maxShapeWidth,
            double maxElementMultiplier,
            double maxDamage,
            double maxCoefficient,
            double maxShapeRadius,
            double maxShapeLength,
            double maxLeashRadius,
            int maxConditionDepth
    ) {
        public double maxKnockbackResistance() {
            return 1.0;
        }

        public double maxFollowRange() {
            return maxTargetRange;
        }

        public double maxScale() {
            return 16.0;
        }

        public double maxSpeed() {
            return maxMovementSpeed;
        }

        public double maxKnockbackStrength() {
            return maxShapeLength;
        }

        public double maxWeight() {
            return maxElementMultiplier;
        }
    }

    public record ValidationResult(List<Issue> issues) {
        public ValidationResult {
            List<Issue> copy = new ArrayList<>(issues == null ? List.of() : issues);
            copy.sort(Comparator.comparing(Issue::path)
                    .thenComparing(Issue::code)
                    .thenComparing(Issue::detail));
            issues = List.copyOf(copy);
        }

        public boolean valid() {
            return issues.isEmpty();
        }

        public boolean isValid() {
            return valid();
        }

        public List<String> codes() {
            return issues.stream().map(Issue::code).toList();
        }

        public List<String> paths() {
            return issues.stream().map(Issue::path).toList();
        }
    }

    public record Issue(String code, String path, String detail) {
        public Issue {
            code = Objects.requireNonNull(code, "code");
            path = Objects.requireNonNull(path, "path");
            detail = Objects.requireNonNull(detail, "detail");
        }

        public String message() {
            return detail;
        }
    }

    public static final class Codes {
        public static final String UNSUPPORTED_SCHEMA_VERSION = "UNSUPPORTED_SCHEMA_VERSION";
        public static final String INVALID_NAMESPACED_ID = "INVALID_NAMESPACED_ID";
        public static final String INVALID_LOCAL_ID = "INVALID_LOCAL_ID";
        public static final String NEGATIVE_REVISION = "NEGATIVE_REVISION";
        public static final String NON_FINITE_NUMBER = "NON_FINITE_NUMBER";
        public static final String NUMBER_OUT_OF_RANGE = "NUMBER_OUT_OF_RANGE";
        public static final String INVALID_VALUE = "INVALID_VALUE";
        public static final String DUPLICATE_ID = "DUPLICATE_ID";
        public static final String DUPLICATE_LOCAL_ID = "DUPLICATE_LOCAL_ID";
        public static final String DUPLICATE_REFERENCE = "DUPLICATE_REFERENCE";
        public static final String MISSING_VALUE = "MISSING_VALUE";
        public static final String EMPTY_DEFINITION = "EMPTY_DEFINITION";
        public static final String CONTRADICTORY_DEFINITION = "CONTRADICTORY_DEFINITION";
        public static final String UNRESOLVED_MOB_REFERENCE = "UNRESOLVED_MOB_REFERENCE";
        public static final String UNRESOLVED_ENTITY_TYPE = "UNRESOLVED_ENTITY_TYPE";
        public static final String UNRESOLVED_ACTOR_REFERENCE = "UNRESOLVED_ACTOR_REFERENCE";
        public static final String UNRESOLVED_ABILITY_REFERENCE = "UNRESOLVED_ABILITY_REFERENCE";
        public static final String UNRESOLVED_EQUIPMENT_REFERENCE = "UNRESOLVED_EQUIPMENT_REFERENCE";
        public static final String UNRESOLVED_VISUAL_REFERENCE = "UNRESOLVED_VISUAL_REFERENCE";
        public static final String UNRESOLVED_REWARD_REFERENCE = "UNRESOLVED_REWARD_REFERENCE";
        public static final String MISSING_PHASE_REFERENCE = "MISSING_PHASE_REFERENCE";
        public static final String NO_ENTRY_PHASE = "NO_ENTRY_PHASE";
        public static final String MULTIPLE_ENTRY_PHASES = "MULTIPLE_ENTRY_PHASES";
        public static final String UNREACHABLE_PHASE = "UNREACHABLE_PHASE";
        public static final String PHASE_CYCLE = "PHASE_CYCLE";
        public static final String MISSING_PHASE_ABILITY = "MISSING_PHASE_ABILITY";
        public static final String MISSING_ACTOR_BEHAVIOR = "MISSING_ACTOR_BEHAVIOR";
        public static final String DUPLICATE_ACTOR_BEHAVIOR = "DUPLICATE_ACTOR_BEHAVIOR";
        public static final String MISSING_ACTOR_ABILITY = "MISSING_ACTOR_ABILITY";
        public static final String DOWNED_ABILITY_POOL = "DOWNED_ABILITY_POOL";
        public static final String DUPLICATE_STATE_TRANSITION = "DUPLICATE_STATE_TRANSITION";
        public static final String INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION";
        public static final String MISSING_STATE_TRANSITION = "MISSING_STATE_TRANSITION";
        public static final String MISSING_DOWN_CONTROL_POLICY = "MISSING_DOWN_CONTROL_POLICY";
        public static final String INVALID_DOWN_CONTROL_POLICY = "INVALID_DOWN_CONTROL_POLICY";
        public static final String INVALID_WEIGHT = "INVALID_WEIGHT";
        public static final String INVALID_SELECTION = "INVALID_SELECTION";
        public static final String INVALID_CONDITION = "INVALID_CONDITION";
        public static final String DAMAGE_TYPE_TAG_MISMATCH = "DAMAGE_TYPE_TAG_MISMATCH";
        public static final String ELEMENT_TAG_MISMATCH = "ELEMENT_TAG_MISMATCH";

        private Codes() {
        }
    }
}
