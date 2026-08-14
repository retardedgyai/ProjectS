package io.github.gyai.projects.content.persistence;

import io.github.gyai.projects.content.definition.ContentDefinitionValidator;
import io.github.gyai.projects.content.definition.DefinitionSupport;
import io.github.gyai.projects.content.definition.EncounterDefinition;
import io.github.gyai.projects.content.persistence.StrictJson.JsonArrayValue;
import io.github.gyai.projects.content.persistence.StrictJson.JsonBoolean;
import io.github.gyai.projects.content.persistence.StrictJson.JsonNull;
import io.github.gyai.projects.content.persistence.StrictJson.JsonNumber;
import io.github.gyai.projects.content.persistence.StrictJson.JsonObjectValue;
import io.github.gyai.projects.content.persistence.StrictJson.JsonString;
import io.github.gyai.projects.content.persistence.StrictJson.JsonValue;

import java.math.BigDecimal;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Strict, deterministic JSON codec for the schema-v1 encounter envelope.
 *
 * <p>References to mobs, abilities, and rewards are deliberately checked only
 * for their local ID grammar here. Resolution belongs to catalog validation,
 * so an encounter can be authored before the other content documents exist.</p>
 */
public final class EncounterDefinitionJsonCodec {
    public static final String FORMAT = "projects-content";
    public static final int SCHEMA_VERSION = 1;
    public static final String KIND = "encounter";
    public static final int MAX_DOCUMENT_BYTES = StrictJson.MAX_DOCUMENT_BYTES;
    public static final int MAX_NESTING_DEPTH = StrictJson.MAX_NESTING_DEPTH;
    public static final int MAX_COLLECTION_ENTRIES = StrictJson.MAX_COLLECTION_ENTRIES;
    public static final int MAX_STRING_LENGTH = StrictJson.MAX_STRING_LENGTH;
    public static final int MAX_CONDITION_DEPTH =
            ContentDefinitionValidator.DEFAULT_BOUNDS.maxConditionDepth();

    private static final Set<String> ENVELOPE_KEYS = Set.of(
            "format", "schemaVersion", "kind", "id", "revision", "definition");
    private static final Set<String> DEFINITION_KEYS = Set.of(
            "actors", "phases", "resetPolicy", "victoryPolicy", "failurePolicy",
            "rewardReferences");
    private static final Set<String> ACTOR_KEYS = Set.of("actorId", "mobReference");
    private static final Set<String> PHASE_KEYS = Set.of(
            "phaseId", "entry", "actorBehaviors", "transitions");
    private static final Set<String> ACTOR_BEHAVIOR_KEYS = Set.of(
            "actorId", "state", "allowedAbilityReferences", "abilitySelectionPolicy");
    private static final Set<String> TRANSITION_KEYS = Set.of(
            "transitionId", "condition", "targetPhaseId", "actorStateTransitions");
    private static final Set<String> ACTOR_STATE_TRANSITION_KEYS = Set.of(
            "actorId", "from", "to", "downControlPolicy");
    private static final Set<String> ORDERED_SELECTION_KEYS = Set.of(
            "type", "abilityReferences");
    private static final Set<String> WEIGHTED_SELECTION_KEYS = Set.of("type", "entries");
    private static final Set<String> WEIGHTED_ABILITY_KEYS = Set.of(
            "abilityReference", "weight");
    private static final Set<String> ALWAYS_KEYS = Set.of("type");
    private static final Set<String> HEALTH_CONDITION_KEYS = Set.of(
            "type", "actorId", "ratio");
    private static final Set<String> ELAPSED_CONDITION_KEYS = Set.of(
            "type", "ticks", "clock");
    private static final Set<String> COMPOUND_CONDITION_KEYS = Set.of(
            "type", "conditions");
    private static final Set<String> RESET_POLICY_KEYS = Set.of(
            "leashRadius", "resetAfterNoTargetTicks", "resetOnLeash", "resetHealth",
            "resetPhase");
    private static final Set<String> VICTORY_POLICY_KEYS = Set.of("condition");
    private static final Set<String> FAILURE_POLICY_KEYS = Set.of("condition", "mode");
    private static final ContentDefinitionValidator.Bounds CONTENT_BOUNDS =
            ContentDefinitionValidator.DEFAULT_BOUNDS;

    /** Encode an encounter into canonical UTF-8 JSON with a final LF. */
    public EncodeResult encode(EncounterDefinition definition) {
        EncounterPersistenceError validation = validateDefinition(definition, "$");
        if (validation != null) return EncodeResult.failure(validation);
        try {
            byte[] bytes = StrictJson.encodeUtf8(render(definition) + "\n");
            if (bytes.length > MAX_DOCUMENT_BYTES) {
                return EncodeResult.failure(error(EncounterPersistenceError.DOCUMENT_TOO_LARGE, "$",
                        "encoded document exceeds 1 MiB"));
            }
            return EncodeResult.success(bytes);
        } catch (CharacterCodingException exception) {
            return EncodeResult.failure(error(EncounterPersistenceError.INVALID_UTF8, "$",
                    "definition contains an invalid Unicode string"));
        } catch (RuntimeException exception) {
            return EncodeResult.failure(error(EncounterPersistenceError.INVALID_DEFINITION, "$",
                    bounded(exception.getMessage(), "definition cannot be encoded")));
        }
    }

    /** Decode one strict JSON document without throwing expected content errors. */
    public DecodeResult decode(byte[] bytes) {
        if (bytes == null) {
            return DecodeResult.failure(error(EncounterPersistenceError.INVALID_VALUE, "$",
                    "document bytes are required"));
        }
        if (bytes.length > MAX_DOCUMENT_BYTES) {
            return DecodeResult.failure(error(EncounterPersistenceError.DOCUMENT_TOO_LARGE, "$",
                    "document exceeds 1 MiB"));
        }
        if (StrictJson.hasUtf8Bom(bytes)) {
            return DecodeResult.failure(error(EncounterPersistenceError.BOM_REJECTED, "$",
                    "UTF-8 BOM is not permitted"));
        }
        final String json;
        try {
            json = StrictJson.decodeUtf8(bytes);
        } catch (CharacterCodingException exception) {
            return DecodeResult.failure(error(EncounterPersistenceError.INVALID_UTF8, "$",
                    "document is not valid UTF-8"));
        }
        try {
            JsonValue root = StrictJson.parse(json);
            EncounterDefinition definition = decodeDefinition(root);
            return DecodeResult.success(definition);
        } catch (StrictJson.Failure failure) {
            StrictJson.Error jsonError = failure.error();
            return DecodeResult.failure(error(jsonError.code(), jsonError.path(),
                    jsonError.detail()));
        } catch (CodecFailure failure) {
            return DecodeResult.failure(failure.error());
        } catch (RuntimeException exception) {
            return DecodeResult.failure(error(EncounterPersistenceError.INVALID_JSON, "$",
                    bounded(exception.getMessage(), "document is invalid")));
        }
    }

    private static EncounterDefinition decodeDefinition(JsonValue root) {
        JsonObjectValue envelope = object(root, "$");
        requireKeys(envelope, ENVELOPE_KEYS, "$");

        String format = string(required(envelope, "format", "$"), "$.format");
        if (!FORMAT.equals(format)) {
            fail(EncounterPersistenceError.WRONG_FORMAT, "$.format",
                    "format must be " + FORMAT);
        }
        int schemaVersion = integer(required(envelope, "schemaVersion", "$"),
                "$.schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            fail(EncounterPersistenceError.UNSUPPORTED_SCHEMA, "$.schemaVersion",
                    "only schema version 1 is supported");
        }
        String kind = string(required(envelope, "kind", "$"), "$.kind");
        if (!KIND.equals(kind)) {
            fail(EncounterPersistenceError.WRONG_KIND, "$.kind", "kind must be encounter");
        }
        String id = string(required(envelope, "id", "$"), "$.id");
        if (!DefinitionSupport.isNamespacedId(id)) {
            fail(EncounterPersistenceError.INVALID_NAMESPACED_ID, "$.id",
                    "id must use the canonical lower-case namespaced grammar");
        }
        long revision = revision(required(envelope, "revision", "$"), "$.revision");
        if (revision < 0) {
            fail(EncounterPersistenceError.NEGATIVE_REVISION, "$.revision",
                    "revision must be non-negative");
        }

        JsonObjectValue payload = object(required(envelope, "definition", "$"),
                "$.definition");
        requireKeys(payload, DEFINITION_KEYS, "$.definition");
        List<EncounterDefinition.Actor> actors = decodeActors(
                required(payload, "actors", "$.definition"), "$.definition.actors");
        List<EncounterDefinition.Phase> phases = decodePhases(
                required(payload, "phases", "$.definition"), "$.definition.phases");
        EncounterDefinition.ResetPolicy resetPolicy = decodeResetPolicy(
                required(payload, "resetPolicy", "$.definition"));
        EncounterDefinition.VictoryPolicy victoryPolicy = decodeVictoryPolicy(
                required(payload, "victoryPolicy", "$.definition"));
        EncounterDefinition.FailurePolicy failurePolicy = decodeFailurePolicy(
                required(payload, "failurePolicy", "$.definition"));
        List<String> rewards = decodeReferences(
                required(payload, "rewardReferences", "$.definition"),
                "$.definition.rewardReferences");

        EncounterDefinition definition = new EncounterDefinition(
                schemaVersion, id, revision, actors, phases, resetPolicy, victoryPolicy,
                failurePolicy, rewards);
        EncounterPersistenceError validation = validateDefinition(definition, "$");
        if (validation != null) throw new CodecFailure(validation);
        return definition;
    }

    private static List<EncounterDefinition.Actor> decodeActors(JsonValue value, String path) {
        JsonArrayValue array = array(value, path);
        List<EncounterDefinition.Actor> result = new ArrayList<>(array.values().size());
        for (int index = 0; index < array.values().size(); index++) {
            String entryPath = path + "[" + index + "]";
            JsonObjectValue object = object(requiredArrayValue(array, index, entryPath), entryPath);
            requireKeys(object, ACTOR_KEYS, entryPath);
            result.add(new EncounterDefinition.Actor(
                    string(required(object, "actorId", entryPath), entryPath + ".actorId"),
                    string(required(object, "mobReference", entryPath),
                            entryPath + ".mobReference")));
        }
        return List.copyOf(result);
    }

    private static List<EncounterDefinition.Phase> decodePhases(JsonValue value, String path) {
        JsonArrayValue array = array(value, path);
        List<EncounterDefinition.Phase> result = new ArrayList<>(array.values().size());
        for (int index = 0; index < array.values().size(); index++) {
            String phasePath = path + "[" + index + "]";
            JsonObjectValue object = object(requiredArrayValue(array, index, phasePath), phasePath);
            requireKeys(object, PHASE_KEYS, phasePath);
            List<EncounterDefinition.ActorBehavior> behaviors = decodeActorBehaviors(
                    required(object, "actorBehaviors", phasePath),
                    phasePath + ".actorBehaviors");
            List<EncounterDefinition.Transition> transitions = decodeTransitions(
                    required(object, "transitions", phasePath), phasePath + ".transitions");
            result.add(new EncounterDefinition.Phase(
                    string(required(object, "phaseId", phasePath), phasePath + ".phaseId"),
                    bool(required(object, "entry", phasePath), phasePath + ".entry"),
                    behaviors, transitions));
        }
        return List.copyOf(result);
    }

    private static List<EncounterDefinition.ActorBehavior> decodeActorBehaviors(
            JsonValue value, String path) {
        JsonArrayValue array = array(value, path);
        List<EncounterDefinition.ActorBehavior> result =
                new ArrayList<>(array.values().size());
        for (int index = 0; index < array.values().size(); index++) {
            String behaviorPath = path + "[" + index + "]";
            JsonObjectValue object = object(requiredArrayValue(array, index, behaviorPath),
                    behaviorPath);
            requireKeys(object, ACTOR_BEHAVIOR_KEYS, behaviorPath);
            Set<String> allowed = decodeReferenceSet(
                    required(object, "allowedAbilityReferences", behaviorPath),
                    behaviorPath + ".allowedAbilityReferences");
            JsonValue selectionValue = requiredAllowNull(object, "abilitySelectionPolicy",
                    behaviorPath);
            EncounterDefinition.AbilitySelectionPolicy selection = selectionValue instanceof JsonNull
                    ? null
                    : decodeSelection(selectionValue,
                    behaviorPath + ".abilitySelectionPolicy");
            result.add(new EncounterDefinition.ActorBehavior(
                    string(required(object, "actorId", behaviorPath), behaviorPath + ".actorId"),
                    actorState(string(required(object, "state", behaviorPath),
                            behaviorPath + ".state"), behaviorPath + ".state"),
                    allowed, selection));
        }
        return List.copyOf(result);
    }

    private static List<EncounterDefinition.Transition> decodeTransitions(JsonValue value,
                                                                            String path) {
        JsonArrayValue array = array(value, path);
        List<EncounterDefinition.Transition> result = new ArrayList<>(array.values().size());
        for (int index = 0; index < array.values().size(); index++) {
            String transitionPath = path + "[" + index + "]";
            JsonObjectValue object = object(requiredArrayValue(array, index, transitionPath),
                    transitionPath);
            requireKeys(object, TRANSITION_KEYS, transitionPath);
            List<EncounterDefinition.ActorStateTransition> effects = decodeStateTransitions(
                    required(object, "actorStateTransitions", transitionPath),
                    transitionPath + ".actorStateTransitions");
            result.add(new EncounterDefinition.Transition(
                    string(required(object, "transitionId", transitionPath),
                            transitionPath + ".transitionId"),
                    decodeCondition(required(object, "condition", transitionPath),
                            transitionPath + ".condition", 0),
                    string(required(object, "targetPhaseId", transitionPath),
                            transitionPath + ".targetPhaseId"),
                    effects));
        }
        return List.copyOf(result);
    }

    private static List<EncounterDefinition.ActorStateTransition> decodeStateTransitions(
            JsonValue value, String path) {
        JsonArrayValue array = array(value, path);
        List<EncounterDefinition.ActorStateTransition> result =
                new ArrayList<>(array.values().size());
        for (int index = 0; index < array.values().size(); index++) {
            String effectPath = path + "[" + index + "]";
            JsonObjectValue object = object(requiredArrayValue(array, index, effectPath),
                    effectPath);
            requireKeys(object, ACTOR_STATE_TRANSITION_KEYS, effectPath);
            result.add(new EncounterDefinition.ActorStateTransition(
                    string(required(object, "actorId", effectPath), effectPath + ".actorId"),
                    actorState(string(required(object, "from", effectPath),
                            effectPath + ".from"), effectPath + ".from"),
                    actorState(string(required(object, "to", effectPath),
                            effectPath + ".to"), effectPath + ".to"),
                    downControlPolicy(string(required(object, "downControlPolicy", effectPath),
                            effectPath + ".downControlPolicy"),
                            effectPath + ".downControlPolicy")));
        }
        return List.copyOf(result);
    }

    private static EncounterDefinition.AbilitySelectionPolicy decodeSelection(JsonValue value,
                                                                                String path) {
        JsonObjectValue object = object(value, path);
        String type = string(required(object, "type", path), path + ".type");
        if ("ordered".equals(type)) {
            requireKeys(object, ORDERED_SELECTION_KEYS, path);
            return new EncounterDefinition.OrderedSelection(decodeOrderedReferences(
                    required(object, "abilityReferences", path), path + ".abilityReferences"));
        }
        if ("weighted".equals(type)) {
            requireKeys(object, WEIGHTED_SELECTION_KEYS, path);
            JsonArrayValue entries = array(required(object, "entries", path), path + ".entries");
            List<EncounterDefinition.WeightedAbility> result =
                    new ArrayList<>(entries.values().size());
            for (int index = 0; index < entries.values().size(); index++) {
                String entryPath = path + ".entries[" + index + "]";
                JsonObjectValue entry = object(requiredArrayValue(entries, index, entryPath),
                        entryPath);
                requireKeys(entry, WEIGHTED_ABILITY_KEYS, entryPath);
                result.add(new EncounterDefinition.WeightedAbility(
                        string(required(entry, "abilityReference", entryPath),
                                entryPath + ".abilityReference"),
                        number(required(entry, "weight", entryPath), entryPath + ".weight")));
            }
            return new EncounterDefinition.WeightedSelection(result);
        }
        fail(EncounterPersistenceError.UNKNOWN_VARIANT, path + ".type",
                "unsupported ability selection type");
        throw new AssertionError("unreachable");
    }

    private static EncounterDefinition.Condition decodeCondition(JsonValue value, String path,
                                                                 int depth) {
        if (depth > MAX_CONDITION_DEPTH) {
            fail(EncounterPersistenceError.NUMBER_OUT_OF_RANGE, path,
                    "condition nesting exceeds the authoring bound");
        }
        JsonObjectValue object = object(value, path);
        String type = string(required(object, "type", path), path + ".type");
        switch (type) {
            case "always" -> {
                requireKeys(object, ALWAYS_KEYS, path);
                return EncounterDefinition.Always.INSTANCE;
            }
            case "actor_health_ratio_at_most" -> {
                requireKeys(object, HEALTH_CONDITION_KEYS, path);
                return new EncounterDefinition.ActorHealthRatioAtMost(
                        string(required(object, "actorId", path), path + ".actorId"),
                        number(required(object, "ratio", path), path + ".ratio"));
            }
            case "elapsed_ticks_at_least" -> {
                requireKeys(object, ELAPSED_CONDITION_KEYS, path);
                return new EncounterDefinition.ElapsedTicksAtLeast(
                        longNumber(required(object, "ticks", path), path + ".ticks",
                                EncounterPersistenceError.NUMBER_OUT_OF_RANGE),
                        clock(string(required(object, "clock", path), path + ".clock"),
                                path + ".clock"));
            }
            case "all", "any" -> {
                requireKeys(object, COMPOUND_CONDITION_KEYS, path);
                JsonArrayValue conditions = array(required(object, "conditions", path),
                        path + ".conditions");
                List<EncounterDefinition.Condition> result =
                        new ArrayList<>(conditions.values().size());
                for (int index = 0; index < conditions.values().size(); index++) {
                    result.add(decodeCondition(
                            requiredArrayValue(conditions, index,
                                    path + ".conditions[" + index + "]"),
                            path + ".conditions[" + index + "]", depth + 1));
                }
                return "all".equals(type)
                        ? new EncounterDefinition.All(result)
                        : new EncounterDefinition.Any(result);
            }
            default -> {
                fail(EncounterPersistenceError.UNKNOWN_VARIANT, path + ".type",
                        "unsupported condition type");
                throw new AssertionError("unreachable");
            }
        }
    }

    private static EncounterDefinition.ResetPolicy decodeResetPolicy(JsonValue value) {
        String path = "$.definition.resetPolicy";
        JsonObjectValue object = object(value, path);
        requireKeys(object, RESET_POLICY_KEYS, path);
        return new EncounterDefinition.ResetPolicy(
                number(required(object, "leashRadius", path), path + ".leashRadius"),
                integer(required(object, "resetAfterNoTargetTicks", path),
                        path + ".resetAfterNoTargetTicks"),
                bool(required(object, "resetOnLeash", path), path + ".resetOnLeash"),
                bool(required(object, "resetHealth", path), path + ".resetHealth"),
                bool(required(object, "resetPhase", path), path + ".resetPhase"));
    }

    private static EncounterDefinition.VictoryPolicy decodeVictoryPolicy(JsonValue value) {
        String path = "$.definition.victoryPolicy";
        JsonObjectValue object = object(value, path);
        requireKeys(object, VICTORY_POLICY_KEYS, path);
        return new EncounterDefinition.VictoryPolicy(
                decodeCondition(required(object, "condition", path), path + ".condition", 0));
    }

    private static EncounterDefinition.FailurePolicy decodeFailurePolicy(JsonValue value) {
        String path = "$.definition.failurePolicy";
        JsonObjectValue object = object(value, path);
        requireKeys(object, FAILURE_POLICY_KEYS, path);
        return new EncounterDefinition.FailurePolicy(
                decodeCondition(required(object, "condition", path), path + ".condition", 0),
                failureMode(string(required(object, "mode", path), path + ".mode"),
                        path + ".mode"));
    }

    private static List<String> decodeReferences(JsonValue value, String path) {
        return decodeReferences(value, path, true);
    }

    private static List<String> decodeOrderedReferences(JsonValue value, String path) {
        return decodeReferences(value, path, false);
    }

    private static List<String> decodeReferences(JsonValue value, String path,
                                                  boolean rejectDuplicates) {
        JsonArrayValue array = array(value, path);
        List<String> result = new ArrayList<>(array.values().size());
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < array.values().size(); index++) {
            String entryPath = path + "[" + index + "]";
            String reference = string(requiredArrayValue(array, index, entryPath), entryPath);
            if (!DefinitionSupport.isNamespacedId(reference)) {
                fail(EncounterPersistenceError.INVALID_NAMESPACED_ID, entryPath,
                        "reference must use the canonical lower-case namespaced grammar");
            }
            if (rejectDuplicates && !seen.add(reference)) {
                fail(EncounterPersistenceError.DUPLICATE_REFERENCE, entryPath,
                        "reference is duplicated");
            }
            result.add(reference);
        }
        return List.copyOf(result);
    }

    private static Set<String> decodeReferenceSet(JsonValue value, String path) {
        JsonArrayValue array = array(value, path);
        Set<String> result = new LinkedHashSet<>();
        for (int index = 0; index < array.values().size(); index++) {
            String entryPath = path + "[" + index + "]";
            String reference = string(requiredArrayValue(array, index, entryPath), entryPath);
            if (!DefinitionSupport.isNamespacedId(reference)) {
                fail(EncounterPersistenceError.INVALID_NAMESPACED_ID, entryPath,
                        "reference must use the canonical lower-case namespaced grammar");
            }
            if (!result.add(reference)) {
                fail(EncounterPersistenceError.DUPLICATE_REFERENCE, entryPath,
                        "reference is duplicated");
            }
        }
        return Set.copyOf(result);
    }

    private static EncounterPersistenceError validateDefinition(EncounterDefinition definition,
                                                                String path) {
        if (definition == null) {
            return error(EncounterPersistenceError.INVALID_DEFINITION, path,
                    "EncounterDefinition is required");
        }
        if (definition.schemaVersion() != EncounterDefinition.SCHEMA_VERSION) {
            return error(EncounterPersistenceError.UNSUPPORTED_SCHEMA,
                    path + ".schemaVersion", "only schema version 1 is supported");
        }
        if (!DefinitionSupport.isNamespacedId(definition.encounterId())) {
            return error(EncounterPersistenceError.INVALID_NAMESPACED_ID, path + ".id",
                    "encounter id must use the canonical lower-case namespaced grammar");
        }
        if (definition.revision() < 0) {
            return error(EncounterPersistenceError.NEGATIVE_REVISION, path + ".revision",
                    "revision must be non-negative");
        }
        if (definition.revision() > CONTENT_BOUNDS.maxLongRevision()) {
            return error(EncounterPersistenceError.NUMBER_OUT_OF_RANGE, path + ".revision",
                    "revision exceeds the existing content bound");
        }

        EncounterPersistenceError sizeError = collectionSize(definition.actors(),
                path + ".definition.actors");
        if (sizeError != null) return sizeError;
        sizeError = collectionSize(definition.phases(), path + ".definition.phases");
        if (sizeError != null) return sizeError;
        sizeError = collectionSize(definition.rewardReferences(),
                path + ".definition.rewardReferences");
        if (sizeError != null) return sizeError;
        if (definition.actors() == null || definition.actors().isEmpty()) {
            return error(EncounterPersistenceError.EMPTY_DEFINITION,
                    path + ".definition.actors", "encounter must contain at least one actor");
        }
        if (definition.phases() == null || definition.phases().isEmpty()) {
            return error(EncounterPersistenceError.EMPTY_DEFINITION,
                    path + ".definition.phases", "encounter must contain at least one phase");
        }
        if (definition.resetPolicy() == null) {
            return missing(path + ".definition.resetPolicy", "reset policy is required");
        }
        EncounterPersistenceError resetError = validateResetPolicy(definition.resetPolicy(),
                path + ".definition.resetPolicy");
        if (resetError != null) return resetError;

        Set<String> actorIds = new LinkedHashSet<>();
        for (int index = 0; index < definition.actors().size(); index++) {
            EncounterDefinition.Actor actor = definition.actors().get(index);
            String actorPath = path + ".definition.actors[" + index + "]";
            if (actor == null) {
                return missing(actorPath, "actor is required");
            }
            EncounterPersistenceError error = localId(actor.actorId(), actorPath + ".actorId");
            if (error != null) return error;
            if (!actorIds.add(actor.actorId())) {
                return error(EncounterPersistenceError.DUPLICATE_LOCAL_ID,
                        actorPath + ".actorId", "duplicate actor id " + actor.actorId());
            }
            error = namespacedId(actor.mobReference(), actorPath + ".mobReference",
                    "mob reference must use the canonical lower-case namespaced grammar");
            if (error != null) return error;
        }

        if (definition.victoryPolicy() == null) {
            return missing(path + ".definition.victoryPolicy", "victory policy is required");
        }
        EncounterPersistenceError conditionError = validateCondition(
                definition.victoryPolicy().condition(),
                path + ".definition.victoryPolicy.condition", actorIds, 0);
        if (conditionError != null) return conditionError;
        if (definition.failurePolicy() == null) {
            return missing(path + ".definition.failurePolicy", "failure policy is required");
        }
        conditionError = validateCondition(definition.failurePolicy().condition(),
                path + ".definition.failurePolicy.condition", actorIds, 0);
        if (conditionError != null) return conditionError;
        if (definition.failurePolicy().mode() == null) {
            return missing(path + ".definition.failurePolicy.mode", "failure mode is required");
        }
        EncounterPersistenceError referencesError = validateReferences(
                definition.rewardReferences(), path + ".definition.rewardReferences");
        if (referencesError != null) return referencesError;

        Map<String, Integer> phaseIndexes = new LinkedHashMap<>();
        int entryCount = 0;
        for (int index = 0; index < definition.phases().size(); index++) {
            EncounterDefinition.Phase phase = definition.phases().get(index);
            String phasePath = path + ".definition.phases[" + index + "]";
            if (phase == null) return missing(phasePath, "phase is required");
            EncounterPersistenceError error = localId(phase.phaseId(), phasePath + ".phaseId");
            if (error != null) return error;
            if (phaseIndexes.putIfAbsent(phase.phaseId(), index) != null) {
                return error(EncounterPersistenceError.DUPLICATE_LOCAL_ID,
                        phasePath + ".phaseId", "duplicate phase id " + phase.phaseId());
            }
            if (phase.entry()) entryCount++;
        }
        if (entryCount == 0) {
            return error(EncounterPersistenceError.NO_ENTRY_PHASE,
                    path + ".definition.phases", "encounter must have exactly one entry phase");
        }
        if (entryCount > 1) {
            return error(EncounterPersistenceError.MULTIPLE_ENTRY_PHASES,
                    path + ".definition.phases", "encounter must have exactly one entry phase");
        }

        for (int index = 0; index < definition.phases().size(); index++) {
            EncounterPersistenceError phaseError = validatePhase(definition.phases().get(index),
                    path + ".definition.phases[" + index + "]", actorIds);
            if (phaseError != null) return phaseError;
        }
        EncounterPersistenceError graphError = validatePhaseGraph(definition.phases(),
                phaseIndexes, path + ".definition.phases");
        if (graphError != null) return graphError;
        return validatePhaseStateTransitions(definition.phases(), phaseIndexes,
                path + ".definition.phases");
    }

    private static EncounterPersistenceError validatePhase(EncounterDefinition.Phase phase,
                                                            String path, Set<String> actorIds) {
        EncounterPersistenceError sizeError = collectionSize(phase.actorBehaviors(),
                path + ".actorBehaviors");
        if (sizeError != null) return sizeError;
        sizeError = collectionSize(phase.transitions(), path + ".transitions");
        if (sizeError != null) return sizeError;
        if (phase.actorBehaviors() == null || phase.actorBehaviors().isEmpty()) {
            return error(EncounterPersistenceError.MISSING_ACTOR_BEHAVIOR,
                    path + ".actorBehaviors", "phase must define at least one actor behavior");
        }
        Set<String> behaviorActors = new HashSet<>();
        for (int index = 0; index < phase.actorBehaviors().size(); index++) {
            EncounterDefinition.ActorBehavior behavior = phase.actorBehaviors().get(index);
            String behaviorPath = path + ".actorBehaviors[" + index + "]";
            if (behavior == null) return missing(behaviorPath, "actor behavior is required");
            EncounterPersistenceError idError = localId(behavior.actorId(),
                    behaviorPath + ".actorId");
            if (idError != null) return idError;
            if (!actorIds.contains(behavior.actorId())) {
                return error(EncounterPersistenceError.UNRESOLVED_ACTOR_REFERENCE,
                        behaviorPath + ".actorId",
                        "actor reference is not present in the encounter");
            }
            if (!behaviorActors.add(behavior.actorId())) {
                return error(EncounterPersistenceError.DUPLICATE_ACTOR_BEHAVIOR,
                        behaviorPath + ".actorId",
                        "phase contains more than one behavior for the actor");
            }
            if (behavior.state() == null) {
                return missing(behaviorPath + ".state", "actor state is required");
            }
            sizeError = collectionSize(behavior.allowedAbilityReferences(),
                    behaviorPath + ".allowedAbilityReferences");
            if (sizeError != null) return sizeError;
            if (behavior.state() == EncounterDefinition.ActorState.DOWNED) {
                if (behavior.allowedAbilityReferences() != null
                        && !behavior.allowedAbilityReferences().isEmpty()) {
                    return error(EncounterPersistenceError.DOWNED_ABILITY_POOL,
                            behaviorPath + ".allowedAbilityReferences",
                            "downed actor behavior must not select abilities");
                }
                if (behavior.abilitySelectionPolicy() != null) {
                    return error(EncounterPersistenceError.DOWNED_ABILITY_POOL,
                            behaviorPath + ".abilitySelectionPolicy",
                            "downed actor behavior must not define an ability selection policy");
                }
            } else {
                if (behavior.allowedAbilityReferences() == null
                        || behavior.allowedAbilityReferences().isEmpty()) {
                    return error(EncounterPersistenceError.MISSING_ACTOR_ABILITY,
                            behaviorPath + ".allowedAbilityReferences",
                            "active actor behavior must allow at least one ability");
                }
                idError = validateReferenceSet(behavior.allowedAbilityReferences(),
                        behaviorPath + ".allowedAbilityReferences");
                if (idError != null) return idError;
                idError = validateSelection(behavior.abilitySelectionPolicy(),
                        behaviorPath + ".abilitySelectionPolicy",
                        behavior.allowedAbilityReferences());
                if (idError != null) return idError;
            }
        }
        for (String actorId : sortedStrings(actorIds)) {
            if (!behaviorActors.contains(actorId)) {
                return error(EncounterPersistenceError.MISSING_ACTOR_BEHAVIOR,
                        path + ".actorBehaviors", "phase is missing behavior for actor " + actorId);
            }
        }

        Set<String> transitionIds = new HashSet<>();
        for (int index = 0; index < phase.transitions().size(); index++) {
            EncounterDefinition.Transition transition = phase.transitions().get(index);
            String transitionPath = path + ".transitions[" + index + "]";
            if (transition == null) return missing(transitionPath, "transition is required");
            EncounterPersistenceError idError = localId(transition.transitionId(),
                    transitionPath + ".transitionId");
            if (idError != null) return idError;
            if (!transitionIds.add(transition.transitionId())) {
                return error(EncounterPersistenceError.DUPLICATE_LOCAL_ID,
                        transitionPath + ".transitionId",
                        "duplicate transition id " + transition.transitionId());
            }
            idError = localId(transition.targetPhaseId(), transitionPath + ".targetPhaseId");
            if (idError != null) return idError;
            EncounterPersistenceError conditionError = validateCondition(transition.condition(),
                    transitionPath + ".condition", actorIds, 0);
            if (conditionError != null) return conditionError;
            idError = validateStateEffects(transition.actorStateTransitions(),
                    transitionPath + ".actorStateTransitions", actorIds);
            if (idError != null) return idError;
        }
        return null;
    }

    private static EncounterPersistenceError validateSelection(
            EncounterDefinition.AbilitySelectionPolicy selection, String path, Set<String> allowed) {
        if (selection == null) return missing(path, "ability selection policy is required");
        Set<String> selected = new LinkedHashSet<>();
        if (selection instanceof EncounterDefinition.OrderedSelection ordered) {
            EncounterPersistenceError sizeError = collectionSize(ordered.abilityReferences(),
                    path + ".abilityReferences");
            if (sizeError != null) return sizeError;
            if (ordered.abilityReferences() == null || ordered.abilityReferences().isEmpty()) {
                return error(EncounterPersistenceError.INVALID_SELECTION, path,
                        "ordered selection must contain at least one ability");
            }
            for (int index = 0; index < ordered.abilityReferences().size(); index++) {
                String entryPath = path + ".abilityReferences[" + index + "]";
                EncounterPersistenceError referenceError = selectionReference(
                        ordered.abilityReferences().get(index), entryPath, allowed, selected);
                if (referenceError != null) return referenceError;
            }
        } else if (selection instanceof EncounterDefinition.WeightedSelection weighted) {
            EncounterPersistenceError sizeError = collectionSize(weighted.entries(),
                    path + ".entries");
            if (sizeError != null) return sizeError;
            if (weighted.entries() == null || weighted.entries().isEmpty()) {
                return error(EncounterPersistenceError.INVALID_SELECTION, path,
                        "weighted selection must contain at least one ability");
            }
            double total = 0.0;
            for (int index = 0; index < weighted.entries().size(); index++) {
                EncounterDefinition.WeightedAbility entry = weighted.entries().get(index);
                String entryPath = path + ".entries[" + index + "]";
                if (entry == null) return missing(entryPath, "weighted ability is required");
                if (!Double.isFinite(entry.weight())) {
                    return error(EncounterPersistenceError.NON_FINITE_NUMBER,
                            entryPath + ".weight", "number must be finite");
                }
                if (entry.weight() <= 0.0) {
                    return error(EncounterPersistenceError.INVALID_WEIGHT,
                            entryPath + ".weight", "weight must be greater than zero");
                }
                if (entry.weight() > CONTENT_BOUNDS.maxWeight()) {
                    return error(EncounterPersistenceError.NUMBER_OUT_OF_RANGE,
                            entryPath + ".weight", "weight exceeds the authoring bound");
                }
                EncounterPersistenceError referenceError = selectionReference(
                        entry.abilityReference(), entryPath + ".abilityReference", allowed,
                        selected);
                if (referenceError != null) return referenceError;
                total += entry.weight();
            }
            if (!Double.isFinite(total) || total <= 0.0) {
                return error(EncounterPersistenceError.INVALID_WEIGHT, path,
                        "weighted selection must have a finite positive total");
            }
        } else {
            return error(EncounterPersistenceError.INVALID_SELECTION, path,
                    "unsupported ability selection policy");
        }
        if (!selected.equals(allowed)) {
            Set<String> missing = new LinkedHashSet<>(allowed);
            missing.removeAll(selected);
            if (!missing.isEmpty()) {
                return error(EncounterPersistenceError.CONTRADICTORY_DEFINITION, path,
                        "allowed abilities are not selectable: " + sortedStrings(missing));
            }
        }
        return null;
    }

    private static EncounterPersistenceError selectionReference(String reference, String path,
                                                                Set<String> allowed,
                                                                Set<String> selected) {
        EncounterPersistenceError error = namespacedId(reference, path,
                "ability reference must use the canonical lower-case namespaced grammar");
        if (error != null) return error;
        if (!allowed.contains(reference)) {
            return error(EncounterPersistenceError.INVALID_SELECTION, path,
                    "selected ability is not in the phase allow-list");
        }
        if (!selected.add(reference)) {
            return error(EncounterPersistenceError.INVALID_SELECTION, path,
                    "ability appears more than once in the selection policy");
        }
        return null;
    }

    private static EncounterPersistenceError validateCondition(EncounterDefinition.Condition condition,
                                                               String path, Set<String> actorIds,
                                                               int depth) {
        if (condition == null) {
            return error(EncounterPersistenceError.INVALID_CONDITION, path,
                    "condition is required");
        }
        if (depth > MAX_CONDITION_DEPTH) {
            return error(EncounterPersistenceError.NUMBER_OUT_OF_RANGE, path,
                    "condition nesting exceeds the authoring bound");
        }
        if (condition instanceof EncounterDefinition.Always) return null;
        if (condition instanceof EncounterDefinition.ActorHealthRatioAtMost health) {
            EncounterPersistenceError idError = localId(health.actorId(), path + ".actorId");
            if (idError != null) return idError;
            if (!actorIds.contains(health.actorId())) {
                return error(EncounterPersistenceError.UNRESOLVED_ACTOR_REFERENCE,
                        path + ".actorId", "actor reference is not present in the encounter");
            }
            return finiteRange(health.ratio(), path + ".ratio", 0.0, 1.0, true);
        }
        if (condition instanceof EncounterDefinition.ElapsedTicksAtLeast elapsed) {
            if (elapsed.clock() == null) {
                return missing(path + ".clock", "elapsed clock is required");
            }
            if (elapsed.ticks() < 0 || elapsed.ticks() > CONTENT_BOUNDS.maxLongTicks()) {
                return error(EncounterPersistenceError.NUMBER_OUT_OF_RANGE, path + ".ticks",
                        elapsed.ticks() < 0
                                ? "elapsed ticks must be non-negative"
                                : "elapsed ticks exceeds the authoring bound");
            }
            return null;
        }
        if (condition instanceof EncounterDefinition.All all) {
            return validateCompoundConditions(all.conditions(), path, actorIds, depth);
        }
        if (condition instanceof EncounterDefinition.Any any) {
            return validateCompoundConditions(any.conditions(), path, actorIds, depth);
        }
        return error(EncounterPersistenceError.INVALID_CONDITION, path,
                "unsupported condition");
    }

    private static EncounterPersistenceError validateCompoundConditions(
            List<EncounterDefinition.Condition> conditions, String path, Set<String> actorIds,
            int depth) {
        EncounterPersistenceError sizeError = collectionSize(conditions, path + ".conditions");
        if (sizeError != null) return sizeError;
        if (conditions == null || conditions.isEmpty()) {
            return error(EncounterPersistenceError.INVALID_CONDITION, path,
                    "compound condition must contain at least one condition");
        }
        for (int index = 0; index < conditions.size(); index++) {
            EncounterPersistenceError error = validateCondition(conditions.get(index),
                    path + ".conditions[" + index + "]", actorIds, depth + 1);
            if (error != null) return error;
        }
        return null;
    }

    private static EncounterPersistenceError validateStateEffects(
            List<EncounterDefinition.ActorStateTransition> effects, String path,
            Set<String> actorIds) {
        EncounterPersistenceError sizeError = collectionSize(effects, path);
        if (sizeError != null) return sizeError;
        Set<String> seenActors = new HashSet<>();
        for (int index = 0; index < effects.size(); index++) {
            EncounterDefinition.ActorStateTransition effect = effects.get(index);
            String effectPath = path + "[" + index + "]";
            if (effect == null) return missing(effectPath, "actor state transition is required");
            EncounterPersistenceError idError = localId(effect.actorId(), effectPath + ".actorId");
            if (idError != null) return idError;
            if (!actorIds.contains(effect.actorId())) {
                return error(EncounterPersistenceError.UNRESOLVED_ACTOR_REFERENCE,
                        effectPath + ".actorId", "actor reference is not present in the encounter");
            }
            if (!seenActors.add(effect.actorId())) {
                return error(EncounterPersistenceError.DUPLICATE_STATE_TRANSITION,
                        effectPath + ".actorId",
                        "transition contains more than one state effect for the actor");
            }
            if (effect.from() == null) {
                return missing(effectPath + ".from", "source actor state is required");
            }
            if (effect.to() == null) {
                return missing(effectPath + ".to", "target actor state is required");
            }
            if (effect.from() == effect.to()) {
                return error(EncounterPersistenceError.INVALID_STATE_TRANSITION, effectPath,
                        "actor state transition must change state");
            }
            boolean enteringDown = effect.from() == EncounterDefinition.ActorState.ACTIVE
                    && effect.to() == EncounterDefinition.ActorState.DOWNED;
            boolean leavingDown = effect.from() == EncounterDefinition.ActorState.DOWNED
                    && effect.to() == EncounterDefinition.ActorState.ACTIVE;
            if (effect.downControlPolicy() == null) {
                return error(EncounterPersistenceError.MISSING_DOWN_CONTROL_POLICY,
                        effectPath + ".downControlPolicy",
                        "down boundary transition requires its canonical control policy");
            }
            if (enteringDown && effect.downControlPolicy()
                    != EncounterDefinition.DownControlPolicy.ENTER_DOWN) {
                return error(EncounterPersistenceError.INVALID_DOWN_CONTROL_POLICY,
                        effectPath + ".downControlPolicy",
                        "entering down must cancel ability, clear current CC, and suppress without buffering");
            }
            if (leavingDown && effect.downControlPolicy()
                    != EncounterDefinition.DownControlPolicy.EXIT_DOWN) {
                return error(EncounterPersistenceError.INVALID_DOWN_CONTROL_POLICY,
                        effectPath + ".downControlPolicy",
                        "leaving down must unsuppress CC without restoring or buffering old CC");
            }
        }
        return null;
    }

    private static EncounterPersistenceError validatePhaseGraph(
            List<EncounterDefinition.Phase> phases, Map<String, Integer> phaseIndexes,
            String path) {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        Map<String, List<GraphEdge>> edges = new LinkedHashMap<>();
        for (String phaseId : phaseIndexes.keySet()) {
            graph.put(phaseId, new LinkedHashSet<>());
            edges.put(phaseId, new ArrayList<>());
        }
        for (int index = 0; index < phases.size(); index++) {
            EncounterDefinition.Phase phase = phases.get(index);
            Set<String> targets = graph.get(phase.phaseId());
            for (int transitionIndex = 0; transitionIndex < phase.transitions().size();
                 transitionIndex++) {
                EncounterDefinition.Transition transition = phase.transitions().get(transitionIndex);
                if (!phaseIndexes.containsKey(transition.targetPhaseId())) {
                    return error(EncounterPersistenceError.MISSING_PHASE_REFERENCE,
                            path + "[" + index + "].transitions[" + transitionIndex
                                    + "].targetPhaseId",
                            "transition target phase is not present in the encounter");
                }
                targets.add(transition.targetPhaseId());
                edges.get(phase.phaseId()).add(new GraphEdge(
                        transition.targetPhaseId(), guaranteesPhaseProgress(transition.condition(), 0)));
            }
        }

        Set<String> entries = new LinkedHashSet<>();
        for (EncounterDefinition.Phase phase : phases) {
            if (phase.entry()) entries.add(phase.phaseId());
        }
        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(entries);
        while (!pending.isEmpty()) {
            String phaseId = pending.removeFirst();
            if (!reachable.add(phaseId)) continue;
            pending.addAll(graph.getOrDefault(phaseId, Set.of()));
        }
        for (Map.Entry<String, Integer> entry : phaseIndexes.entrySet()) {
            if (!reachable.contains(entry.getKey())) {
                return error(EncounterPersistenceError.UNREACHABLE_PHASE,
                        path + "[" + entry.getValue() + "].phaseId",
                        "phase is not reachable from an entry phase");
            }
        }

        Set<String> cycleNodes = new HashSet<>();
        detectUnprogressingCycles(edges, reachable, cycleNodes);
        for (Map.Entry<String, Integer> entry : phaseIndexes.entrySet()) {
            if (cycleNodes.contains(entry.getKey())) {
                return error(EncounterPersistenceError.PHASE_CYCLE,
                        path + "[" + entry.getValue() + "].phaseId",
                        "reachable phase cycle has no guaranteed positive phase-relative delay");
            }
        }
        return null;
    }

    private static void detectUnprogressingCycles(Map<String, List<GraphEdge>> graph,
                                                  Set<String> reachable,
                                                  Set<String> cycleNodes) {
        Set<String> visited = new HashSet<>();
        Map<String, Integer> stackPositions = new HashMap<>();
        List<String> path = new ArrayList<>();
        ArrayDeque<GraphTraversalFrame> stack = new ArrayDeque<>();
        for (String node : graph.keySet()) {
            if (!reachable.contains(node) || visited.contains(node)) continue;
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
                if (edge.guaranteedProgress() || !graph.containsKey(edge.target())) continue;
                Integer targetPosition = stackPositions.get(edge.target());
                if (targetPosition != null) {
                    for (int index = targetPosition; index < path.size(); index++) {
                        cycleNodes.add(path.get(index));
                    }
                } else if (!visited.contains(edge.target())) {
                    stack.push(new GraphTraversalFrame(edge.target()));
                    stackPositions.put(edge.target(), path.size());
                    path.add(edge.target());
                }
            }
        }
    }

    private static boolean guaranteesPhaseProgress(EncounterDefinition.Condition condition,
                                                   int depth) {
        if (condition == null || depth > MAX_CONDITION_DEPTH) return false;
        if (condition instanceof EncounterDefinition.ElapsedTicksAtLeast elapsed) {
            return elapsed.clock() == EncounterDefinition.Clock.PHASE
                    && elapsed.ticks() > 0 && elapsed.ticks() <= CONTENT_BOUNDS.maxLongTicks();
        }
        if (condition instanceof EncounterDefinition.All all) {
            return all.conditions() != null
                    && all.conditions().stream().anyMatch(
                    child -> guaranteesPhaseProgress(child, depth + 1));
        }
        if (condition instanceof EncounterDefinition.Any any) {
            return any.conditions() != null && !any.conditions().isEmpty()
                    && any.conditions().stream().allMatch(
                    child -> guaranteesPhaseProgress(child, depth + 1));
        }
        return false;
    }

    private static EncounterPersistenceError validatePhaseStateTransitions(
            List<EncounterDefinition.Phase> phases, Map<String, Integer> phaseIndexes,
            String path) {
        for (int index = 0; index < phases.size(); index++) {
            EncounterDefinition.Phase source = phases.get(index);
            Map<String, EncounterDefinition.ActorState> sourceStates = actorStates(source);
            for (int transitionIndex = 0; transitionIndex < source.transitions().size();
                 transitionIndex++) {
                EncounterDefinition.Transition transition = source.transitions().get(transitionIndex);
                Integer targetIndex = phaseIndexes.get(transition.targetPhaseId());
                if (targetIndex == null) continue;
                EncounterDefinition.Phase target = phases.get(targetIndex);
                Map<String, EncounterDefinition.ActorState> targetStates = actorStates(target);
                Map<String, EncounterDefinition.ActorStateTransition> effects = new LinkedHashMap<>();
                for (EncounterDefinition.ActorStateTransition effect
                        : transition.actorStateTransitions()) {
                    if (effect != null) effects.put(effect.actorId(), effect);
                }
                String transitionPath = path + "[" + index + "].transitions["
                        + transitionIndex + "]";
                for (Map.Entry<String, EncounterDefinition.ActorState> entry
                        : sourceStates.entrySet()) {
                    EncounterDefinition.ActorState targetState = targetStates.get(entry.getKey());
                    if (targetState == null || entry.getValue() == targetState) continue;
                    EncounterDefinition.ActorStateTransition effect = effects.get(entry.getKey());
                    if (effect == null) {
                        return error(EncounterPersistenceError.MISSING_STATE_TRANSITION,
                                transitionPath + ".actorStateTransitions",
                                "phase actor state change requires an explicit typed state transition");
                    }
                    if (effect.from() != entry.getValue() || effect.to() != targetState) {
                        return error(EncounterPersistenceError.INVALID_STATE_TRANSITION,
                                transitionPath + ".actorStateTransitions",
                                "typed actor state transition does not match source and target phases");
                    }
                }
                for (EncounterDefinition.ActorStateTransition effect
                        : transition.actorStateTransitions()) {
                    if (effect == null) continue;
                    EncounterDefinition.ActorState sourceState = sourceStates.get(effect.actorId());
                    EncounterDefinition.ActorState targetState = targetStates.get(effect.actorId());
                    if (sourceState == null || targetState == null
                            || effect.from() != sourceState || effect.to() != targetState) {
                        return error(EncounterPersistenceError.INVALID_STATE_TRANSITION,
                                transitionPath + ".actorStateTransitions",
                                "typed actor state transition does not match source and target phases");
                    }
                }
            }
        }
        return null;
    }

    private static Map<String, EncounterDefinition.ActorState> actorStates(
            EncounterDefinition.Phase phase) {
        Map<String, EncounterDefinition.ActorState> states = new LinkedHashMap<>();
        for (EncounterDefinition.ActorBehavior behavior : phase.actorBehaviors()) {
            if (behavior != null && behavior.actorId() != null && behavior.state() != null) {
                states.putIfAbsent(behavior.actorId(), behavior.state());
            }
        }
        return states;
    }

    private static EncounterPersistenceError validateResetPolicy(
            EncounterDefinition.ResetPolicy reset, String path) {
        if (Double.isFinite(reset.leashRadius()) && reset.leashRadius() == 0.0
                && reset.resetOnLeash()) {
            return error(EncounterPersistenceError.CONTRADICTORY_DEFINITION,
                    path + ".leashRadius",
                    "reset-on-leash requires a positive leash radius");
        }
        EncounterPersistenceError numberError = finiteRange(reset.leashRadius(),
                path + ".leashRadius", 0.0, CONTENT_BOUNDS.maxLeashRadius(), false);
        if (numberError != null) return numberError;
        if (reset.resetAfterNoTargetTicks() < 0
                || reset.resetAfterNoTargetTicks() > CONTENT_BOUNDS.maxTicks()) {
            return error(EncounterPersistenceError.NUMBER_OUT_OF_RANGE,
                    path + ".resetAfterNoTargetTicks",
                    reset.resetAfterNoTargetTicks() < 0
                            ? "ticks must be non-negative"
                            : "ticks exceed the authoring bound");
        }
        return null;
    }

    private static EncounterPersistenceError validateReferenceSet(Set<String> values,
                                                                  String path) {
        if (values == null) return missing(path, "reference collection is required");
        Set<String> seen = new HashSet<>();
        List<String> sorted = sortedStrings(values);
        for (int index = 0; index < sorted.size(); index++) {
            String value = sorted.get(index);
            EncounterPersistenceError error = namespacedId(value, path + "[" + index + "]",
                    "reference must use the canonical lower-case namespaced grammar");
            if (error != null) return error;
            if (!seen.add(value)) {
                return error(EncounterPersistenceError.DUPLICATE_REFERENCE,
                        path + "[" + index + "]", "reference is duplicated");
            }
        }
        return null;
    }

    private static EncounterPersistenceError validateReferences(List<String> values, String path) {
        if (values == null) return missing(path, "reference collection is required");
        EncounterPersistenceError sizeError = collectionSize(values, path);
        if (sizeError != null) return sizeError;
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String entryPath = path + "[" + index + "]";
            EncounterPersistenceError error = namespacedId(values.get(index), entryPath,
                    "reference must use the canonical lower-case namespaced grammar");
            if (error != null) return error;
            if (!seen.add(values.get(index))) {
                return error(EncounterPersistenceError.DUPLICATE_REFERENCE, entryPath,
                        "reference is duplicated");
            }
        }
        return null;
    }

    private static EncounterPersistenceError collectionSize(Object collection, String path) {
        if (collection == null) return null;
        int size = collection instanceof List<?> list ? list.size()
                : collection instanceof Set<?> set ? set.size() : -1;
        if (size > MAX_COLLECTION_ENTRIES) {
            return error(EncounterPersistenceError.COLLECTION_TOO_LARGE, path,
                    "collection contains more than 4096 entries");
        }
        return null;
    }

    private static EncounterPersistenceError localId(String value, String path) {
        if (!DefinitionSupport.isLocalId(value)) {
            return error(EncounterPersistenceError.INVALID_LOCAL_ID, path,
                    "local id must match [a-z][a-z0-9_-]{0,31}");
        }
        return null;
    }

    private static EncounterPersistenceError namespacedId(String value, String path,
                                                          String detail) {
        if (!DefinitionSupport.isNamespacedId(value)) {
            return error(EncounterPersistenceError.INVALID_NAMESPACED_ID, path, detail);
        }
        return null;
    }

    private static EncounterPersistenceError finiteRange(double value, String path,
                                                        double minimum, double maximum,
                                                        boolean inclusiveMinimum) {
        if (!Double.isFinite(value)) {
            return error(EncounterPersistenceError.NON_FINITE_NUMBER, path,
                    "number must be finite");
        }
        if (value < minimum || (!inclusiveMinimum && value == minimum) || value > maximum) {
            return error(EncounterPersistenceError.NUMBER_OUT_OF_RANGE, path,
                    "number is outside the authoring bound");
        }
        return null;
    }

    private static EncounterDefinition.ActorState actorState(String value, String path) {
        for (EncounterDefinition.ActorState candidate : EncounterDefinition.ActorState.values()) {
            if (wireName(candidate).equals(value)) return candidate;
        }
        fail(EncounterPersistenceError.UNSUPPORTED_ENUM, path,
                "unsupported actor state wire value");
        throw new AssertionError("unreachable");
    }

    private static EncounterDefinition.DownControlPolicy downControlPolicy(String value,
                                                                            String path) {
        for (EncounterDefinition.DownControlPolicy candidate
                : EncounterDefinition.DownControlPolicy.values()) {
            if (wireName(candidate).equals(value)) return candidate;
        }
        fail(EncounterPersistenceError.UNSUPPORTED_ENUM, path,
                "unsupported down control policy wire value");
        throw new AssertionError("unreachable");
    }

    private static EncounterDefinition.Clock clock(String value, String path) {
        for (EncounterDefinition.Clock candidate : EncounterDefinition.Clock.values()) {
            if (wireName(candidate).equals(value)) return candidate;
        }
        fail(EncounterPersistenceError.UNSUPPORTED_ENUM, path,
                "unsupported elapsed clock wire value");
        throw new AssertionError("unreachable");
    }

    private static EncounterDefinition.FailureMode failureMode(String value, String path) {
        for (EncounterDefinition.FailureMode candidate : EncounterDefinition.FailureMode.values()) {
            if (wireName(candidate).equals(value)) return candidate;
        }
        fail(EncounterPersistenceError.UNSUPPORTED_ENUM, path,
                "unsupported failure mode wire value");
        throw new AssertionError("unreachable");
    }

    private static String render(EncounterDefinition definition) {
        StringBuilder out = new StringBuilder(4_096);
        out.append('{');
        boolean first = true;
        first = field(out, first, "format", quote(FORMAT));
        first = field(out, first, "schemaVersion", Integer.toString(SCHEMA_VERSION));
        first = field(out, first, "kind", quote(KIND));
        first = field(out, first, "id", quote(definition.encounterId()));
        first = field(out, first, "revision", Long.toString(definition.revision()));
        field(out, first, "definition", renderDefinition(definition));
        out.append('}');
        return out.toString();
    }

    private static String renderDefinition(EncounterDefinition definition) {
        StringBuilder out = new StringBuilder(3_584);
        out.append('{');
        boolean first = true;
        first = field(out, first, "actors", renderActors(definition.actors()));
        first = field(out, first, "phases", renderPhases(definition.phases()));
        first = field(out, first, "resetPolicy", renderResetPolicy(definition.resetPolicy()));
        first = field(out, first, "victoryPolicy", renderVictoryPolicy(definition.victoryPolicy()));
        first = field(out, first, "failurePolicy", renderFailurePolicy(definition.failurePolicy()));
        field(out, first, "rewardReferences", renderStrings(definition.rewardReferences()));
        out.append('}');
        return out.toString();
    }

    private static String renderActors(List<EncounterDefinition.Actor> actors) {
        StringBuilder out = new StringBuilder(128);
        out.append('[');
        for (int index = 0; index < actors.size(); index++) {
            if (index > 0) out.append(',');
            EncounterDefinition.Actor actor = actors.get(index);
            boolean first = true;
            out.append('{');
            first = field(out, first, "actorId", quote(actor.actorId()));
            field(out, first, "mobReference", quote(actor.mobReference()));
            out.append('}');
        }
        return out.append(']').toString();
    }

    private static String renderPhases(List<EncounterDefinition.Phase> phases) {
        StringBuilder out = new StringBuilder(2_048);
        out.append('[');
        for (int index = 0; index < phases.size(); index++) {
            if (index > 0) out.append(',');
            EncounterDefinition.Phase phase = phases.get(index);
            boolean first = true;
            out.append('{');
            first = field(out, first, "phaseId", quote(phase.phaseId()));
            first = field(out, first, "entry", Boolean.toString(phase.entry()));
            first = field(out, first, "actorBehaviors", renderBehaviors(phase.actorBehaviors()));
            field(out, first, "transitions", renderTransitions(phase.transitions()));
            out.append('}');
        }
        return out.append(']').toString();
    }

    private static String renderBehaviors(List<EncounterDefinition.ActorBehavior> behaviors) {
        StringBuilder out = new StringBuilder(512);
        out.append('[');
        for (int index = 0; index < behaviors.size(); index++) {
            if (index > 0) out.append(',');
            EncounterDefinition.ActorBehavior behavior = behaviors.get(index);
            boolean first = true;
            out.append('{');
            first = field(out, first, "actorId", quote(behavior.actorId()));
            first = field(out, first, "state", quote(wireName(behavior.state())));
            first = field(out, first, "allowedAbilityReferences",
                    renderSortedStrings(behavior.allowedAbilityReferences()));
            field(out, first, "abilitySelectionPolicy",
                    behavior.abilitySelectionPolicy() == null
                            ? "null" : renderSelection(behavior.abilitySelectionPolicy()));
            out.append('}');
        }
        return out.append(']').toString();
    }

    private static String renderTransitions(List<EncounterDefinition.Transition> transitions) {
        StringBuilder out = new StringBuilder(1_024);
        out.append('[');
        for (int index = 0; index < transitions.size(); index++) {
            if (index > 0) out.append(',');
            EncounterDefinition.Transition transition = transitions.get(index);
            boolean first = true;
            out.append('{');
            first = field(out, first, "transitionId", quote(transition.transitionId()));
            first = field(out, first, "condition", renderCondition(transition.condition()));
            first = field(out, first, "targetPhaseId", quote(transition.targetPhaseId()));
            field(out, first, "actorStateTransitions",
                    renderStateTransitions(transition.actorStateTransitions()));
            out.append('}');
        }
        return out.append(']').toString();
    }

    private static String renderStateTransitions(
            List<EncounterDefinition.ActorStateTransition> transitions) {
        StringBuilder out = new StringBuilder(256);
        out.append('[');
        for (int index = 0; index < transitions.size(); index++) {
            if (index > 0) out.append(',');
            EncounterDefinition.ActorStateTransition transition = transitions.get(index);
            boolean first = true;
            out.append('{');
            first = field(out, first, "actorId", quote(transition.actorId()));
            first = field(out, first, "from", quote(wireName(transition.from())));
            first = field(out, first, "to", quote(wireName(transition.to())));
            field(out, first, "downControlPolicy",
                    quote(wireName(transition.downControlPolicy())));
            out.append('}');
        }
        return out.append(']').toString();
    }

    private static String renderSelection(EncounterDefinition.AbilitySelectionPolicy selection) {
        StringBuilder out = new StringBuilder(384);
        out.append('{');
        boolean first = true;
        if (selection instanceof EncounterDefinition.OrderedSelection ordered) {
            first = field(out, first, "type", quote("ordered"));
            field(out, first, "abilityReferences", renderStrings(ordered.abilityReferences()));
        } else if (selection instanceof EncounterDefinition.WeightedSelection weighted) {
            first = field(out, first, "type", quote("weighted"));
            field(out, first, "entries", renderWeightedEntries(weighted.entries()));
        }
        return out.append('}').toString();
    }

    private static String renderWeightedEntries(List<EncounterDefinition.WeightedAbility> entries) {
        StringBuilder out = new StringBuilder(256);
        out.append('[');
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) out.append(',');
            EncounterDefinition.WeightedAbility entry = entries.get(index);
            boolean first = true;
            out.append('{');
            first = field(out, first, "abilityReference", quote(entry.abilityReference()));
            field(out, first, "weight", Double.toString(entry.weight()));
            out.append('}');
        }
        return out.append(']').toString();
    }

    private static String renderCondition(EncounterDefinition.Condition condition) {
        StringBuilder out = new StringBuilder(256);
        out.append('{');
        boolean first = true;
        if (condition instanceof EncounterDefinition.Always) {
            field(out, first, "type", quote("always"));
        } else if (condition instanceof EncounterDefinition.ActorHealthRatioAtMost health) {
            first = field(out, first, "type", quote("actor_health_ratio_at_most"));
            first = field(out, first, "actorId", quote(health.actorId()));
            field(out, first, "ratio", Double.toString(health.ratio()));
        } else if (condition instanceof EncounterDefinition.ElapsedTicksAtLeast elapsed) {
            first = field(out, first, "type", quote("elapsed_ticks_at_least"));
            first = field(out, first, "ticks", Long.toString(elapsed.ticks()));
            field(out, first, "clock", quote(wireName(elapsed.clock())));
        } else if (condition instanceof EncounterDefinition.All all) {
            first = field(out, first, "type", quote("all"));
            field(out, first, "conditions", renderConditions(all.conditions()));
        } else if (condition instanceof EncounterDefinition.Any any) {
            first = field(out, first, "type", quote("any"));
            field(out, first, "conditions", renderConditions(any.conditions()));
        }
        return out.append('}').toString();
    }

    private static String renderConditions(List<EncounterDefinition.Condition> conditions) {
        StringBuilder out = new StringBuilder(384);
        out.append('[');
        for (int index = 0; index < conditions.size(); index++) {
            if (index > 0) out.append(',');
            out.append(renderCondition(conditions.get(index)));
        }
        return out.append(']').toString();
    }

    private static String renderResetPolicy(EncounterDefinition.ResetPolicy policy) {
        StringBuilder out = new StringBuilder(192);
        out.append('{');
        boolean first = true;
        first = field(out, first, "leashRadius", Double.toString(policy.leashRadius()));
        first = field(out, first, "resetAfterNoTargetTicks",
                Integer.toString(policy.resetAfterNoTargetTicks()));
        first = field(out, first, "resetOnLeash", Boolean.toString(policy.resetOnLeash()));
        first = field(out, first, "resetHealth", Boolean.toString(policy.resetHealth()));
        field(out, first, "resetPhase", Boolean.toString(policy.resetPhase()));
        return out.append('}').toString();
    }

    private static String renderVictoryPolicy(EncounterDefinition.VictoryPolicy policy) {
        StringBuilder out = new StringBuilder(160);
        out.append('{');
        field(out, true, "condition", renderCondition(policy.condition()));
        return out.append('}').toString();
    }

    private static String renderFailurePolicy(EncounterDefinition.FailurePolicy policy) {
        StringBuilder out = new StringBuilder(192);
        out.append('{');
        boolean first = true;
        first = field(out, first, "condition", renderCondition(policy.condition()));
        field(out, first, "mode", quote(wireName(policy.mode())));
        return out.append('}').toString();
    }

    private static String renderStrings(List<String> values) {
        StringBuilder out = new StringBuilder(128);
        out.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) out.append(',');
            out.append(quote(values.get(index)));
        }
        return out.append(']').toString();
    }

    private static String renderSortedStrings(Set<String> values) {
        return renderStrings(sortedStrings(values));
    }

    private static boolean field(StringBuilder out, boolean first, String name, String value) {
        return StrictJson.field(out, first, name, value);
    }

    private static String quote(String value) {
        return StrictJson.quote(value);
    }

    private static JsonValue required(JsonObjectValue object, String key, String path) {
        if (!object.values().containsKey(key)) {
            fail(EncounterPersistenceError.MISSING_VALUE, pathForKey(path, key),
                    "required field is missing");
        }
        JsonValue value = object.values().get(key);
        if (value instanceof JsonNull) {
            fail(EncounterPersistenceError.NULL_REQUIRED_FIELD, pathForKey(path, key),
                    "required field must not be null");
        }
        return value;
    }

    private static JsonValue requiredAllowNull(JsonObjectValue object, String key, String path) {
        if (!object.values().containsKey(key)) {
            fail(EncounterPersistenceError.MISSING_VALUE, pathForKey(path, key),
                    "required field is missing");
        }
        return object.values().get(key);
    }

    private static JsonValue requiredArrayValue(JsonArrayValue array, int index, String path) {
        JsonValue value = array.values().get(index);
        if (value instanceof JsonNull) {
            fail(EncounterPersistenceError.NULL_REQUIRED_FIELD, path,
                    "array entry must not be null");
        }
        return value;
    }

    private static JsonObjectValue object(JsonValue value, String path) {
        if (!(value instanceof JsonObjectValue object)) {
            fail(EncounterPersistenceError.INVALID_VALUE, path, "object is required");
        }
        return (JsonObjectValue) value;
    }

    private static JsonArrayValue array(JsonValue value, String path) {
        if (!(value instanceof JsonArrayValue array)) {
            fail(EncounterPersistenceError.INVALID_VALUE, path, "array is required");
        }
        return (JsonArrayValue) value;
    }

    private static String string(JsonValue value, String path) {
        if (!(value instanceof JsonString string)) {
            fail(EncounterPersistenceError.INVALID_VALUE, path, "string is required");
        }
        return ((JsonString) value).value();
    }

    private static boolean bool(JsonValue value, String path) {
        if (!(value instanceof JsonBoolean bool)) {
            fail(EncounterPersistenceError.INVALID_VALUE, path, "boolean is required");
        }
        return ((JsonBoolean) value).value();
    }

    private static int integer(JsonValue value, String path) {
        BigDecimal number = decimal(value, path);
        try {
            return number.toBigIntegerExact().intValueExact();
        } catch (ArithmeticException exception) {
            fail(EncounterPersistenceError.INVALID_VALUE, path,
                    "number must be an integral 32-bit value");
            throw new AssertionError("unreachable");
        }
    }

    private static long revision(JsonValue value, String path) {
        return longNumber(value, path, EncounterPersistenceError.REVISION_OVERFLOW);
    }

    private static long longNumber(JsonValue value, String path, String overflowCode) {
        BigDecimal number = decimal(value, path);
        try {
            return number.toBigIntegerExact().longValueExact();
        } catch (ArithmeticException exception) {
            if (number.stripTrailingZeros().scale() > 0) {
                fail(EncounterPersistenceError.NON_INTEGRAL_NUMBER, path,
                        "number must be integral");
            }
            fail(overflowCode, path, "number is outside the signed 64-bit range");
            throw new AssertionError("unreachable");
        }
    }

    private static double number(JsonValue value, String path) {
        BigDecimal decimal = decimal(value, path);
        double result = decimal.doubleValue();
        if (!Double.isFinite(result)) {
            fail(EncounterPersistenceError.NON_FINITE_NUMBER, path, "number must be finite");
        }
        return result;
    }

    private static BigDecimal decimal(JsonValue value, String path) {
        if (!(value instanceof JsonNumber number)) {
            fail(EncounterPersistenceError.INVALID_VALUE, path, "number is required");
        }
        return ((JsonNumber) value).value();
    }

    private static void requireKeys(JsonObjectValue object, Set<String> expected, String path) {
        object.values().keySet().stream()
                .filter(key -> !expected.contains(key))
                .sorted()
                .findFirst()
                .ifPresent(key -> fail(EncounterPersistenceError.UNKNOWN_KEY,
                        pathForKey(path, key), "unknown object key"));
    }

    private static String pathForKey(String parent, String key) {
        if (key != null && key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return parent + "." + key;
        }
        String escaped = key == null ? "null" : key.replace("\\", "\\\\")
                .replace("'", "\\'");
        return parent + "['" + escaped + "']";
    }

    private static String wireName(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static List<String> sortedStrings(Iterable<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) result.add(value);
        result.sort(Comparator.nullsFirst(String::compareTo));
        return result;
    }

    private static EncounterPersistenceError missing(String path, String detail) {
        return error(EncounterPersistenceError.MISSING_VALUE, path, detail);
    }

    private static EncounterPersistenceError error(String code, String path, String detail) {
        return new EncounterPersistenceError(code, path, bounded(detail, "persistence error"));
    }

    private static String bounded(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value;
        return result.length() <= 256 ? result : result.substring(0, 255) + "…";
    }

    private static void fail(String code, String path, String detail) {
        throw new CodecFailure(error(code, path, detail));
    }

    private static final class CodecFailure extends RuntimeException {
        private final EncounterPersistenceError error;

        private CodecFailure(EncounterPersistenceError error) {
            super(error.detail());
            this.error = error;
        }

        private EncounterPersistenceError error() {
            return error;
        }
    }

    private record GraphEdge(String target, boolean guaranteedProgress) {
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

    public record EncodeResult(byte[] bytes, EncounterPersistenceError error) {
        public EncodeResult {
            bytes = bytes == null ? null : bytes.clone();
        }

        public boolean success() {
            return error == null;
        }

        @Override
        public byte[] bytes() {
            return bytes == null ? null : bytes.clone();
        }

        private static EncodeResult success(byte[] bytes) {
            return new EncodeResult(bytes, null);
        }

        private static EncodeResult failure(EncounterPersistenceError error) {
            return new EncodeResult(null, Objects.requireNonNull(error, "error"));
        }
    }

    public record DecodeResult(EncounterDefinition definition,
                               EncounterPersistenceError error) {
        public boolean success() {
            return error == null;
        }

        private static DecodeResult success(EncounterDefinition definition) {
            return new DecodeResult(definition, null);
        }

        private static DecodeResult failure(EncounterPersistenceError error) {
            return new DecodeResult(null, Objects.requireNonNull(error, "error"));
        }
    }
}
