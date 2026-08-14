package io.github.gyai.projects.content.persistence;

import io.github.gyai.projects.ability.TargetSelector;
import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.combat.damage.DamageElement;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageType;
import io.github.gyai.projects.combat.damage.ElementProfile;
import io.github.gyai.projects.content.definition.AbilityDefinition;
import io.github.gyai.projects.content.definition.ContentDefinitionValidator;
import io.github.gyai.projects.content.definition.DefinitionSupport;
import io.github.gyai.projects.content.persistence.StrictJson.JsonArrayValue;
import io.github.gyai.projects.content.persistence.StrictJson.JsonBoolean;
import io.github.gyai.projects.content.persistence.StrictJson.JsonNull;
import io.github.gyai.projects.content.persistence.StrictJson.JsonNumber;
import io.github.gyai.projects.content.persistence.StrictJson.JsonObjectValue;
import io.github.gyai.projects.content.persistence.StrictJson.JsonString;
import io.github.gyai.projects.content.persistence.StrictJson.JsonValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict, deterministic JSON codec for the schema-v1 Ability content envelope. */
public final class AbilityDefinitionJsonCodec {
    public static final String FORMAT = "projects-content";
    public static final int SCHEMA_VERSION = 1;
    public static final String KIND = "ability";
    public static final int MAX_DOCUMENT_BYTES = StrictJson.MAX_DOCUMENT_BYTES;
    public static final int MAX_NESTING_DEPTH = StrictJson.MAX_NESTING_DEPTH;
    public static final int MAX_COLLECTION_ENTRIES = StrictJson.MAX_COLLECTION_ENTRIES;
    public static final int MAX_STRING_LENGTH = StrictJson.MAX_STRING_LENGTH;

    private static final Set<String> ENVELOPE_KEYS = Set.of(
            "format", "schemaVersion", "kind", "id", "revision", "definition");
    private static final Set<String> DEFINITION_KEYS = Set.of(
            "displayName", "timing", "targeting", "timeline", "interruptPolicy",
            "visualReference");
    private static final Set<String> TIMING_KEYS = Set.of(
            "castTicks", "recoveryTicks", "cooldownTicks");
    private static final Set<String> TARGETING_KEYS = Set.of("selector", "maxRange");
    private static final Set<String> WAIT_KEYS = Set.of("type", "stepId", "ticks");
    private static final Set<String> TELEGRAPH_KEYS = Set.of(
            "type", "stepId", "origin", "shape", "durationTicks", "lockAtCreation");
    private static final Set<String> DAMAGE_KEYS = Set.of(
            "type", "stepId", "target", "shape", "damageType", "damageKind",
            "fixedDamage", "coefficient", "criticalAllowed", "metadata");
    private static final Set<String> CHARGE_KEYS = Set.of(
            "type", "stepId", "target", "path", "durationTicks", "speed");
    private static final Set<String> KNOCKBACK_KEYS = Set.of(
            "type", "stepId", "target", "shape", "horizontalStrength", "verticalStrength");
    private static final Set<String> CIRCLE_KEYS = Set.of("type", "radius");
    private static final Set<String> DONUT_KEYS = Set.of("type", "innerRadius", "outerRadius");
    private static final Set<String> LINE_KEYS = Set.of("type", "length", "width");
    private static final Set<String> METADATA_KEYS = Set.of("tags", "elements");
    private static final Set<String> ELEMENT_PROFILE_KEYS = Set.of("values", "scalingRates");
    private static final ContentDefinitionValidator.Bounds CONTENT_BOUNDS =
            ContentDefinitionValidator.DEFAULT_BOUNDS;

    /** Encode an AbilityDefinition into canonical UTF-8 JSON with a final LF. */
    public EncodeResult encode(AbilityDefinition definition) {
        AbilityPersistenceError validation = validateDefinition(definition, "$");
        if (validation != null) return EncodeResult.failure(validation);
        try {
            byte[] bytes = strictUtf8(render(definition) + "\n");
            if (bytes.length > MAX_DOCUMENT_BYTES) {
                return EncodeResult.failure(error(AbilityPersistenceError.DOCUMENT_TOO_LARGE, "$",
                        "encoded document exceeds 1 MiB"));
            }
            return EncodeResult.success(bytes);
        } catch (CharacterCodingException exception) {
            return EncodeResult.failure(error(AbilityPersistenceError.INVALID_UTF8, "$",
                    "definition contains an invalid Unicode string"));
        } catch (RuntimeException exception) {
            return EncodeResult.failure(error(AbilityPersistenceError.INVALID_DEFINITION, "$",
                    bounded(exception.getMessage(), "definition cannot be encoded")));
        }
    }

    /** Decode one strict JSON document without throwing expected content errors. */
    public DecodeResult decode(byte[] bytes) {
        if (bytes == null) {
            return DecodeResult.failure(error(AbilityPersistenceError.INVALID_VALUE, "$",
                    "document bytes are required"));
        }
        if (bytes.length > MAX_DOCUMENT_BYTES) {
            return DecodeResult.failure(error(AbilityPersistenceError.DOCUMENT_TOO_LARGE, "$",
                    "document exceeds 1 MiB"));
        }
        if (StrictJson.hasUtf8Bom(bytes)) {
            return DecodeResult.failure(error(AbilityPersistenceError.BOM_REJECTED, "$",
                    "UTF-8 BOM is not permitted"));
        }
        final String json;
        try {
            json = strictUtf8(bytes);
        } catch (CharacterCodingException exception) {
            return DecodeResult.failure(error(AbilityPersistenceError.INVALID_UTF8, "$",
                    "document is not valid UTF-8"));
        }
        try {
            AbilityDefinition definition = decodeDefinition(StrictJson.parse(json));
            return DecodeResult.success(definition);
        } catch (StrictJson.Failure failure) {
            StrictJson.Error jsonError = failure.error();
            return DecodeResult.failure(error(jsonError.code(), jsonError.path(),
                    jsonError.detail()));
        } catch (CodecFailure failure) {
            return DecodeResult.failure(failure.error());
        } catch (RuntimeException exception) {
            return DecodeResult.failure(error(AbilityPersistenceError.INVALID_JSON, "$",
                    bounded(exception.getMessage(), "document is invalid")));
        }
    }

    private static AbilityDefinition decodeDefinition(JsonValue root) {
        JsonObjectValue envelope = object(root, "$");
        requireKeys(envelope, ENVELOPE_KEYS, "$");

        String format = string(required(envelope, "format", "$"), "$.format");
        if (!FORMAT.equals(format)) {
            fail(AbilityPersistenceError.WRONG_FORMAT, "$.format", "format must be " + FORMAT);
        }
        int schemaVersion = integer(required(envelope, "schemaVersion", "$"),
                "$.schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            fail(AbilityPersistenceError.UNSUPPORTED_SCHEMA, "$.schemaVersion",
                    "only schema version 1 is supported");
        }
        String kind = string(required(envelope, "kind", "$"), "$.kind");
        if (!KIND.equals(kind)) {
            fail(AbilityPersistenceError.WRONG_KIND, "$.kind", "kind must be ability");
        }

        String abilityId = string(required(envelope, "id", "$"), "$.id");
        if (!DefinitionSupport.isNamespacedId(abilityId)) {
            fail(AbilityPersistenceError.INVALID_NAMESPACED_ID, "$.id",
                    "id must use the canonical lower-case namespaced grammar");
        }
        long revision = revision(required(envelope, "revision", "$"), "$.revision");
        if (revision < 0) {
            fail(AbilityPersistenceError.NEGATIVE_REVISION, "$.revision",
                    "revision must be non-negative");
        }

        JsonObjectValue payload = object(required(envelope, "definition", "$"),
                "$.definition");
        requireKeys(payload, DEFINITION_KEYS, "$.definition");
        String displayName = string(required(payload, "displayName", "$.definition"),
                "$.definition.displayName");
        AbilityDefinition.Timing timing = decodeTiming(
                required(payload, "timing", "$.definition"));
        AbilityDefinition.Targeting targeting = decodeTargeting(
                required(payload, "targeting", "$.definition"));
        List<AbilityDefinition.TimelineAction> timeline = decodeTimeline(
                required(payload, "timeline", "$.definition"));
        AbilityDefinition.InterruptPolicy interruptPolicy = interruptPolicy(
                string(required(payload, "interruptPolicy", "$.definition"),
                        "$.definition.interruptPolicy"),
                "$.definition.interruptPolicy");
        String visualReference = optionalString(payload, "visualReference", "$.definition");

        AbilityDefinition definition = new AbilityDefinition(
                schemaVersion, abilityId, revision, displayName, timing, targeting, timeline,
                interruptPolicy, visualReference);
        AbilityPersistenceError validation = validateDefinition(definition, "$");
        if (validation != null) throw new CodecFailure(validation);
        return definition;
    }

    private static AbilityDefinition.Timing decodeTiming(JsonValue value) {
        JsonObjectValue object = object(value, "$.definition.timing");
        requireKeys(object, TIMING_KEYS, "$.definition.timing");
        return new AbilityDefinition.Timing(
                integer(required(object, "castTicks", "$.definition.timing"),
                        "$.definition.timing.castTicks"),
                integer(required(object, "recoveryTicks", "$.definition.timing"),
                        "$.definition.timing.recoveryTicks"),
                integer(required(object, "cooldownTicks", "$.definition.timing"),
                        "$.definition.timing.cooldownTicks"));
    }

    private static AbilityDefinition.Targeting decodeTargeting(JsonValue value) {
        JsonObjectValue object = object(value, "$.definition.targeting");
        requireKeys(object, TARGETING_KEYS, "$.definition.targeting");
        return new AbilityDefinition.Targeting(
                selector(string(required(object, "selector", "$.definition.targeting"),
                        "$.definition.targeting.selector"), "$.definition.targeting.selector"),
                number(required(object, "maxRange", "$.definition.targeting"),
                        "$.definition.targeting.maxRange"));
    }

    private static List<AbilityDefinition.TimelineAction> decodeTimeline(JsonValue value) {
        JsonArrayValue array = array(value, "$.definition.timeline");
        if (array.values().size() > MAX_COLLECTION_ENTRIES) {
            fail(AbilityPersistenceError.COLLECTION_TOO_LARGE, "$.definition.timeline",
                    "array contains more than 4096 entries");
        }
        List<AbilityDefinition.TimelineAction> result = new ArrayList<>(array.values().size());
        for (int index = 0; index < array.values().size(); index++) {
            String path = "$.definition.timeline[" + index + "]";
            JsonValue entry = array.values().get(index);
            if (entry instanceof JsonNull) {
                fail(AbilityPersistenceError.NULL_REQUIRED_FIELD, path,
                        "timeline action must not be null");
            }
            result.add(decodeAction(entry, path));
        }
        return List.copyOf(result);
    }

    private static AbilityDefinition.TimelineAction decodeAction(JsonValue value, String path) {
        JsonObjectValue object = object(value, path);
        JsonValue rawType = required(object, "type", path);
        String type = string(rawType, pathForKey(path, "type"));
        return switch (type) {
            case "wait" -> {
                requireKeys(object, WAIT_KEYS, path);
                yield new AbilityDefinition.Wait(
                        string(required(object, "stepId", path), pathForKey(path, "stepId")),
                        integer(required(object, "ticks", path), pathForKey(path, "ticks")));
            }
            case "telegraph" -> {
                requireKeys(object, TELEGRAPH_KEYS, path);
                yield new AbilityDefinition.Telegraph(
                        string(required(object, "stepId", path), pathForKey(path, "stepId")),
                        selector(string(required(object, "origin", path), pathForKey(path, "origin")),
                                pathForKey(path, "origin")),
                        decodeShape(required(object, "shape", path), pathForKey(path, "shape")),
                        integer(required(object, "durationTicks", path),
                                pathForKey(path, "durationTicks")),
                        bool(required(object, "lockAtCreation", path),
                                pathForKey(path, "lockAtCreation")));
            }
            case "damage" -> {
                requireKeys(object, DAMAGE_KEYS, path);
                yield new AbilityDefinition.Damage(
                        string(required(object, "stepId", path), pathForKey(path, "stepId")),
                        selector(string(required(object, "target", path), pathForKey(path, "target")),
                                pathForKey(path, "target")),
                        decodeShape(required(object, "shape", path), pathForKey(path, "shape")),
                        damageType(string(required(object, "damageType", path),
                                pathForKey(path, "damageType")), pathForKey(path, "damageType")),
                        damageKind(string(required(object, "damageKind", path),
                                pathForKey(path, "damageKind")), pathForKey(path, "damageKind")),
                        number(required(object, "fixedDamage", path),
                                pathForKey(path, "fixedDamage")),
                        number(required(object, "coefficient", path),
                                pathForKey(path, "coefficient")),
                        bool(required(object, "criticalAllowed", path),
                                pathForKey(path, "criticalAllowed")),
                        decodeMetadata(required(object, "metadata", path),
                                pathForKey(path, "metadata")));
            }
            case "charge" -> {
                requireKeys(object, CHARGE_KEYS, path);
                AbilityDefinition.RelativeShape pathShape = decodeShape(
                        required(object, "path", path), pathForKey(path, "path"));
                if (!(pathShape instanceof AbilityDefinition.Line line)) {
                    fail(AbilityPersistenceError.VARIANT_MISMATCH,
                            pathForKey(path, "path") + ".type",
                            "charge path must use the line shape variant");
                    yield null;
                }
                yield new AbilityDefinition.Charge(
                        string(required(object, "stepId", path), pathForKey(path, "stepId")),
                        selector(string(required(object, "target", path), pathForKey(path, "target")),
                                pathForKey(path, "target")),
                        line,
                        integer(required(object, "durationTicks", path),
                                pathForKey(path, "durationTicks")),
                        number(required(object, "speed", path), pathForKey(path, "speed")));
            }
            case "knockback" -> {
                requireKeys(object, KNOCKBACK_KEYS, path);
                yield new AbilityDefinition.Knockback(
                        string(required(object, "stepId", path), pathForKey(path, "stepId")),
                        selector(string(required(object, "target", path), pathForKey(path, "target")),
                                pathForKey(path, "target")),
                        decodeShape(required(object, "shape", path), pathForKey(path, "shape")),
                        number(required(object, "horizontalStrength", path),
                                pathForKey(path, "horizontalStrength")),
                        number(required(object, "verticalStrength", path),
                                pathForKey(path, "verticalStrength")));
            }
            default -> {
                fail(AbilityPersistenceError.UNKNOWN_VARIANT, pathForKey(path, "type"),
                        "unsupported timeline action type");
                yield null;
            }
        };
    }

    private static AbilityDefinition.RelativeShape decodeShape(JsonValue value, String path) {
        JsonObjectValue object = object(value, path);
        String type = string(required(object, "type", path), pathForKey(path, "type"));
        return switch (type) {
            case "circle" -> {
                requireKeys(object, CIRCLE_KEYS, path);
                yield new AbilityDefinition.Circle(number(required(object, "radius", path),
                        pathForKey(path, "radius")));
            }
            case "donut" -> {
                requireKeys(object, DONUT_KEYS, path);
                yield new AbilityDefinition.Donut(
                        number(required(object, "innerRadius", path),
                                pathForKey(path, "innerRadius")),
                        number(required(object, "outerRadius", path),
                                pathForKey(path, "outerRadius")));
            }
            case "line" -> {
                requireKeys(object, LINE_KEYS, path);
                yield new AbilityDefinition.Line(
                        number(required(object, "length", path), pathForKey(path, "length")),
                        number(required(object, "width", path), pathForKey(path, "width")));
            }
            default -> {
                fail(AbilityPersistenceError.UNKNOWN_VARIANT, pathForKey(path, "type"),
                        "unsupported relative shape type");
                yield null;
            }
        };
    }

    private static AttackMetadata decodeMetadata(JsonValue value, String path) {
        JsonObjectValue object = object(value, path);
        requireKeys(object, METADATA_KEYS, path);
        Set<AttackTag> tags = decodeTags(required(object, "tags", path),
                pathForKey(path, "tags"));
        ElementProfile elements = decodeElementProfile(required(object, "elements", path),
                pathForKey(path, "elements"));
        return new AttackMetadata(tags, elements);
    }

    private static Set<AttackTag> decodeTags(JsonValue value, String path) {
        JsonArrayValue array = array(value, path);
        if (array.values().size() > MAX_COLLECTION_ENTRIES) {
            fail(AbilityPersistenceError.COLLECTION_TOO_LARGE, path,
                    "array contains more than 4096 entries");
        }
        EnumSet<AttackTag> tags = EnumSet.noneOf(AttackTag.class);
        for (int index = 0; index < array.values().size(); index++) {
            String entryPath = path + "[" + index + "]";
            JsonValue entry = array.values().get(index);
            if (entry instanceof JsonNull) {
                fail(AbilityPersistenceError.NULL_REQUIRED_FIELD, entryPath,
                        "attack tag must not be null");
            }
            AttackTag tag = attackTag(string(entry, entryPath), entryPath);
            if (!tags.add(tag)) {
                fail(AbilityPersistenceError.DUPLICATE_TAG, entryPath,
                        "attack tag is duplicated");
            }
        }
        return Set.copyOf(tags);
    }

    private static ElementProfile decodeElementProfile(JsonValue value, String path) {
        JsonObjectValue object = object(value, path);
        requireKeys(object, ELEMENT_PROFILE_KEYS, path);
        Map<DamageElement, Double> values = decodeElementMap(
                required(object, "values", path), pathForKey(path, "values"));
        Map<DamageElement, Double> scalingRates = decodeElementMap(
                required(object, "scalingRates", path), pathForKey(path, "scalingRates"));
        return new ElementProfile(values, scalingRates);
    }

    private static Map<DamageElement, Double> decodeElementMap(JsonValue value, String path) {
        JsonObjectValue object = object(value, path);
        if (object.values().size() > MAX_COLLECTION_ENTRIES) {
            fail(AbilityPersistenceError.COLLECTION_TOO_LARGE, path,
                    "map contains more than 4096 entries");
        }
        EnumMap<DamageElement, Double> result = new EnumMap<>(DamageElement.class);
        for (Map.Entry<String, JsonValue> entry : object.values().entrySet()) {
            String entryPath = pathForKey(path, entry.getKey());
            DamageElement element = element(entry.getKey(), entryPath);
            double amount = number(entry.getValue(), entryPath);
            if (amount < 0.0) {
                throw new CodecFailure(error(AbilityPersistenceError.NUMBER_OUT_OF_RANGE,
                        entryPath, "number is outside its existing content bound"));
            }
            if (result.put(element, amount) != null) {
                fail(AbilityPersistenceError.DUPLICATE_KEY, entryPath,
                        "duplicate element key");
            }
        }
        return Map.copyOf(result);
    }

    private static String optionalString(JsonObjectValue object, String key, String path) {
        if (!object.values().containsKey(key)) {
            fail(AbilityPersistenceError.MISSING_VALUE, pathForKey(path, key),
                    "required field is missing");
        }
        JsonValue value = object.values().get(key);
        if (value instanceof JsonNull) return null;
        return string(value, pathForKey(path, key));
    }

    private static TargetSelector selector(String value, String path) {
        for (TargetSelector candidate : TargetSelector.values()) {
            if (wireName(candidate).equals(value)) return candidate;
        }
        fail(AbilityPersistenceError.UNSUPPORTED_ENUM, path,
                "unsupported TargetSelector wire value");
        throw new AssertionError("unreachable");
    }

    private static DamageType damageType(String value, String path) {
        for (DamageType candidate : DamageType.values()) {
            if (wireName(candidate).equals(value)) return candidate;
        }
        fail(AbilityPersistenceError.UNSUPPORTED_ENUM, path,
                "unsupported DamageType wire value");
        throw new AssertionError("unreachable");
    }

    private static DamageKind damageKind(String value, String path) {
        for (DamageKind candidate : DamageKind.values()) {
            if (wireName(candidate).equals(value)) return candidate;
        }
        fail(AbilityPersistenceError.UNSUPPORTED_ENUM, path,
                "unsupported DamageKind wire value");
        throw new AssertionError("unreachable");
    }

    private static AbilityDefinition.InterruptPolicy interruptPolicy(String value, String path) {
        for (AbilityDefinition.InterruptPolicy candidate
                : AbilityDefinition.InterruptPolicy.values()) {
            if (wireName(candidate).equals(value)) return candidate;
        }
        fail(AbilityPersistenceError.UNSUPPORTED_ENUM, path,
                "unsupported InterruptPolicy wire value");
        throw new AssertionError("unreachable");
    }

    private static AttackTag attackTag(String value, String path) {
        for (AttackTag candidate : AttackTag.values()) {
            if (wireName(candidate).equals(value)) return candidate;
        }
        fail(AbilityPersistenceError.UNSUPPORTED_ENUM, path,
                "unsupported AttackTag wire value");
        throw new AssertionError("unreachable");
    }

    private static DamageElement element(String value, String path) {
        for (DamageElement candidate : DamageElement.values()) {
            if (wireName(candidate).equals(value)) return candidate;
        }
        fail(AbilityPersistenceError.UNSUPPORTED_ENUM, path,
                "unsupported DamageElement wire value");
        throw new AssertionError("unreachable");
    }

    private static int integer(JsonValue value, String path) {
        BigDecimal number = decimal(value, path);
        try {
            return number.toBigIntegerExact().intValueExact();
        } catch (ArithmeticException exception) {
            fail(AbilityPersistenceError.INVALID_VALUE, path,
                    "number must be an integral 32-bit value");
            throw new AssertionError("unreachable");
        }
    }

    private static long revision(JsonValue value, String path) {
        BigDecimal number = decimal(value, path);
        try {
            return number.toBigIntegerExact().longValueExact();
        } catch (ArithmeticException exception) {
            if (number.stripTrailingZeros().scale() > 0) {
                fail(AbilityPersistenceError.NON_INTEGRAL_NUMBER, path,
                        "revision must be integral");
            }
            fail(AbilityPersistenceError.REVISION_OVERFLOW, path,
                    "revision is outside the signed 64-bit range");
            throw new AssertionError("unreachable");
        }
    }

    private static double number(JsonValue value, String path) {
        BigDecimal decimal = decimal(value, path);
        double result = decimal.doubleValue();
        if (!Double.isFinite(result)) {
            fail(AbilityPersistenceError.NON_FINITE_NUMBER, path, "number must be finite");
        }
        return result;
    }

    private static BigDecimal decimal(JsonValue value, String path) {
        if (!(value instanceof JsonNumber number)) {
            fail(AbilityPersistenceError.INVALID_VALUE, path, "number is required");
        }
        return ((JsonNumber) value).value();
    }

    private static boolean bool(JsonValue value, String path) {
        if (!(value instanceof JsonBoolean booleanValue)) {
            fail(AbilityPersistenceError.INVALID_VALUE, path, "boolean is required");
        }
        return ((JsonBoolean) value).value();
    }

    private static JsonValue required(JsonObjectValue object, String key, String path) {
        if (!object.values().containsKey(key)) {
            fail(AbilityPersistenceError.MISSING_VALUE, pathForKey(path, key),
                    "required field is missing");
        }
        JsonValue value = object.values().get(key);
        if (value instanceof JsonNull) {
            fail(AbilityPersistenceError.NULL_REQUIRED_FIELD, pathForKey(path, key),
                    "required field must not be null");
        }
        return value;
    }

    private static JsonObjectValue object(JsonValue value, String path) {
        if (!(value instanceof JsonObjectValue object)) {
            fail(AbilityPersistenceError.INVALID_VALUE, path, "object is required");
        }
        return (JsonObjectValue) value;
    }

    private static JsonArrayValue array(JsonValue value, String path) {
        if (!(value instanceof JsonArrayValue array)) {
            fail(AbilityPersistenceError.INVALID_VALUE, path, "array is required");
        }
        return (JsonArrayValue) value;
    }

    private static String string(JsonValue value, String path) {
        if (!(value instanceof JsonString string)) {
            fail(AbilityPersistenceError.INVALID_VALUE, path, "string is required");
        }
        return ((JsonString) value).value();
    }

    private static void requireKeys(JsonObjectValue object, Set<String> expected, String path) {
        object.values().keySet().stream()
                .filter(key -> !expected.contains(key))
                .sorted()
                .findFirst()
                .ifPresent(key -> fail(AbilityPersistenceError.UNKNOWN_KEY,
                        pathForKey(path, key), "unknown object key"));
    }

    private static String render(AbilityDefinition definition) {
        StringBuilder out = new StringBuilder(4_096);
        out.append('{');
        boolean first = true;
        first = field(out, first, "format", quote(FORMAT));
        first = field(out, first, "schemaVersion", Integer.toString(SCHEMA_VERSION));
        first = field(out, first, "kind", quote(KIND));
        first = field(out, first, "id", quote(definition.abilityId()));
        first = field(out, first, "revision", Long.toString(definition.revision()));
        field(out, first, "definition", renderPayload(definition));
        out.append('}');
        return out.toString();
    }

    private static String renderPayload(AbilityDefinition definition) {
        StringBuilder out = new StringBuilder(3_584);
        out.append('{');
        boolean first = true;
        first = field(out, first, "displayName", quote(definition.displayName()));
        first = field(out, first, "timing", renderTiming(definition.timing()));
        first = field(out, first, "targeting", renderTargeting(definition.targeting()));
        first = field(out, first, "timeline", renderTimeline(definition.timeline()));
        first = field(out, first, "interruptPolicy",
                quote(wireName(definition.interruptPolicy())));
        field(out, first, "visualReference",
                definition.visualReference() == null ? "null" : quote(definition.visualReference()));
        out.append('}');
        return out.toString();
    }

    private static String renderTiming(AbilityDefinition.Timing timing) {
        StringBuilder out = new StringBuilder(96);
        out.append('{');
        boolean first = true;
        first = field(out, first, "castTicks", Integer.toString(timing.castTicks()));
        first = field(out, first, "recoveryTicks", Integer.toString(timing.recoveryTicks()));
        field(out, first, "cooldownTicks", Integer.toString(timing.cooldownTicks()));
        out.append('}');
        return out.toString();
    }

    private static String renderTargeting(AbilityDefinition.Targeting targeting) {
        StringBuilder out = new StringBuilder(96);
        out.append('{');
        boolean first = true;
        first = field(out, first, "selector", quote(wireName(targeting.selector())));
        field(out, first, "maxRange", Double.toString(targeting.maxRange()));
        out.append('}');
        return out.toString();
    }

    private static String renderTimeline(List<AbilityDefinition.TimelineAction> actions) {
        StringBuilder out = new StringBuilder(1_536);
        out.append('[');
        for (int index = 0; index < actions.size(); index++) {
            if (index > 0) out.append(',');
            out.append(renderAction(actions.get(index)));
        }
        out.append(']');
        return out.toString();
    }

    private static String renderAction(AbilityDefinition.TimelineAction action) {
        if (action instanceof AbilityDefinition.Wait wait) {
            StringBuilder out = new StringBuilder(96);
            out.append('{');
            boolean first = true;
            first = field(out, first, "type", quote("wait"));
            first = field(out, first, "stepId", quote(wait.stepId()));
            field(out, first, "ticks", Integer.toString(wait.ticks()));
            return out.append('}').toString();
        }
        if (action instanceof AbilityDefinition.Telegraph telegraph) {
            StringBuilder out = new StringBuilder(192);
            out.append('{');
            boolean first = true;
            first = field(out, first, "type", quote("telegraph"));
            first = field(out, first, "stepId", quote(telegraph.stepId()));
            first = field(out, first, "origin", quote(wireName(telegraph.origin())));
            first = field(out, first, "shape", renderShape(telegraph.shape()));
            first = field(out, first, "durationTicks", Integer.toString(telegraph.durationTicks()));
            field(out, first, "lockAtCreation", Boolean.toString(telegraph.lockAtCreation()));
            return out.append('}').toString();
        }
        if (action instanceof AbilityDefinition.Damage damage) {
            StringBuilder out = new StringBuilder(384);
            out.append('{');
            boolean first = true;
            first = field(out, first, "type", quote("damage"));
            first = field(out, first, "stepId", quote(damage.stepId()));
            first = field(out, first, "target", quote(wireName(damage.target())));
            first = field(out, first, "shape", renderShape(damage.shape()));
            first = field(out, first, "damageType", quote(wireName(damage.damageType())));
            first = field(out, first, "damageKind", quote(wireName(damage.damageKind())));
            first = field(out, first, "fixedDamage", Double.toString(damage.fixedDamage()));
            first = field(out, first, "coefficient", Double.toString(damage.coefficient()));
            first = field(out, first, "criticalAllowed", Boolean.toString(damage.criticalAllowed()));
            field(out, first, "metadata", renderMetadata(damage.metadata()));
            return out.append('}').toString();
        }
        if (action instanceof AbilityDefinition.Charge charge) {
            StringBuilder out = new StringBuilder(192);
            out.append('{');
            boolean first = true;
            first = field(out, first, "type", quote("charge"));
            first = field(out, first, "stepId", quote(charge.stepId()));
            first = field(out, first, "target", quote(wireName(charge.target())));
            first = field(out, first, "path", renderShape(charge.path()));
            first = field(out, first, "durationTicks", Integer.toString(charge.durationTicks()));
            field(out, first, "speed", Double.toString(charge.speed()));
            return out.append('}').toString();
        }
        if (action instanceof AbilityDefinition.Knockback knockback) {
            StringBuilder out = new StringBuilder(192);
            out.append('{');
            boolean first = true;
            first = field(out, first, "type", quote("knockback"));
            first = field(out, first, "stepId", quote(knockback.stepId()));
            first = field(out, first, "target", quote(wireName(knockback.target())));
            first = field(out, first, "shape", renderShape(knockback.shape()));
            first = field(out, first, "horizontalStrength",
                    Double.toString(knockback.horizontalStrength()));
            field(out, first, "verticalStrength", Double.toString(knockback.verticalStrength()));
            return out.append('}').toString();
        }
        throw new IllegalArgumentException("unsupported timeline action implementation");
    }

    private static String renderShape(AbilityDefinition.RelativeShape shape) {
        if (shape instanceof AbilityDefinition.Circle circle) {
            return objectFields("type", quote("circle"),
                    "radius", Double.toString(circle.radius()));
        }
        if (shape instanceof AbilityDefinition.Donut donut) {
            return objectFields("type", quote("donut"),
                    "innerRadius", Double.toString(donut.innerRadius()),
                    "outerRadius", Double.toString(donut.outerRadius()));
        }
        if (shape instanceof AbilityDefinition.Line line) {
            return objectFields("type", quote("line"),
                    "length", Double.toString(line.length()),
                    "width", Double.toString(line.width()));
        }
        throw new IllegalArgumentException("unsupported relative shape implementation");
    }

    private static String renderMetadata(AttackMetadata metadata) {
        StringBuilder out = new StringBuilder(384);
        out.append('{');
        boolean first = true;
        first = field(out, first, "tags", renderTags(metadata.tags()));
        field(out, first, "elements", renderElementProfile(metadata.elements()));
        return out.append('}').toString();
    }

    private static String renderTags(Set<AttackTag> tags) {
        List<AttackTag> sorted = new ArrayList<>(tags);
        sorted.sort(Comparator.comparing(AbilityDefinitionJsonCodec::wireName));
        StringBuilder out = new StringBuilder(128);
        out.append('[');
        for (int index = 0; index < sorted.size(); index++) {
            if (index > 0) out.append(',');
            out.append(quote(wireName(sorted.get(index))));
        }
        return out.append(']').toString();
    }

    private static String renderElementProfile(ElementProfile elements) {
        return objectFields("values", renderElementMap(elements.values()),
                "scalingRates", renderElementMap(elements.scalingRates()));
    }

    private static String renderElementMap(Map<DamageElement, Double> values) {
        List<Map.Entry<DamageElement, Double>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Comparator.comparing(entry -> wireName(entry.getKey())));
        StringBuilder out = new StringBuilder(128);
        out.append('{');
        boolean first = true;
        for (Map.Entry<DamageElement, Double> entry : entries) {
            first = field(out, first, wireName(entry.getKey()), Double.toString(entry.getValue()));
        }
        return out.append('}').toString();
    }

    private static String objectFields(String... pairs) {
        if ((pairs.length & 1) != 0) throw new IllegalArgumentException("field pairs required");
        StringBuilder out = new StringBuilder(128);
        out.append('{');
        boolean first = true;
        for (int index = 0; index < pairs.length; index += 2) {
            first = field(out, first, pairs[index], pairs[index + 1]);
        }
        return out.append('}').toString();
    }

    private static boolean field(StringBuilder out, boolean first, String name, String value) {
        return StrictJson.field(out, first, name, value);
    }

    private static String quote(String value) {
        return StrictJson.quote(value);
    }

    private static AbilityPersistenceError validateDefinition(AbilityDefinition definition,
                                                               String path) {
        if (definition == null) {
            return error(AbilityPersistenceError.INVALID_DEFINITION, path,
                    "AbilityDefinition is required");
        }
        if (definition.schemaVersion() != AbilityDefinition.SCHEMA_VERSION) {
            return error(AbilityPersistenceError.UNSUPPORTED_SCHEMA, path + ".schemaVersion",
                    "only schema version 1 is supported");
        }
        if (!DefinitionSupport.isNamespacedId(definition.abilityId())) {
            return error(AbilityPersistenceError.INVALID_NAMESPACED_ID, path + ".id",
                    "ability id must use the canonical lower-case namespaced grammar");
        }
        if (definition.revision() < 0) {
            return error(AbilityPersistenceError.NEGATIVE_REVISION, path + ".revision",
                    "revision must be non-negative");
        }
        if (definition.revision() > CONTENT_BOUNDS.maxLongRevision()) {
            return error(AbilityPersistenceError.NUMBER_OUT_OF_RANGE, path + ".revision",
                    "revision exceeds the existing content bound");
        }
        AbilityPersistenceError textError = text(definition.displayName(),
                path + ".definition.displayName", 128);
        if (textError != null) return textError;

        AbilityDefinition.Timing timing = definition.timing();
        if (timing == null) {
            return missing(path + ".definition.timing", "timing is required");
        }
        AbilityPersistenceError numberError = ticks(timing.castTicks(),
                path + ".definition.timing.castTicks", true);
        if (numberError != null) return numberError;
        numberError = ticks(timing.recoveryTicks(),
                path + ".definition.timing.recoveryTicks", true);
        if (numberError != null) return numberError;
        numberError = ticks(timing.cooldownTicks(),
                path + ".definition.timing.cooldownTicks", true);
        if (numberError != null) return numberError;

        AbilityDefinition.Targeting targeting = definition.targeting();
        if (targeting == null) {
            return missing(path + ".definition.targeting", "targeting is required");
        }
        if (targeting.selector() == null) {
            return missing(path + ".definition.targeting.selector",
                    "target selector is required");
        }
        numberError = finiteRange(targeting.maxRange(), path + ".definition.targeting.maxRange",
                0.0, CONTENT_BOUNDS.maxTargetRange(), false);
        if (numberError != null) return numberError;

        if (definition.timeline() == null || definition.timeline().isEmpty()) {
            return error(AbilityPersistenceError.EMPTY_DEFINITION, path + ".definition.timeline",
                    "ability timeline must not be empty");
        }
        if (definition.timeline().size() > MAX_COLLECTION_ENTRIES) {
            return error(AbilityPersistenceError.COLLECTION_TOO_LARGE,
                    path + ".definition.timeline", "array contains more than 4096 entries");
        }
        if (definition.interruptPolicy() == null) {
            return missing(path + ".definition.interruptPolicy",
                    "interrupt policy is required");
        }
        if (definition.visualReference() != null
                && !DefinitionSupport.isNamespacedId(definition.visualReference())) {
            return error(AbilityPersistenceError.INVALID_NAMESPACED_ID,
                    path + ".definition.visualReference",
                    "visual reference must use the canonical lower-case namespaced grammar");
        }

        Set<String> stepIds = new HashSet<>();
        for (int index = 0; index < definition.timeline().size(); index++) {
            AbilityDefinition.TimelineAction action = definition.timeline().get(index);
            String actionPath = path + ".definition.timeline[" + index + "]";
            if (action == null) {
                return missing(actionPath, "timeline action is required");
            }
            AbilityPersistenceError localIdError = localId(action.stepId(),
                    actionPath + ".stepId");
            if (localIdError != null) return localIdError;
            if (!stepIds.add(action.stepId())) {
                return error(AbilityPersistenceError.DUPLICATE_LOCAL_ID,
                        actionPath + ".stepId", "duplicate timeline step id " + action.stepId());
            }
            AbilityPersistenceError actionError = validateAction(action, actionPath);
            if (actionError != null) return actionError;
        }
        return null;
    }

    private static AbilityPersistenceError validateAction(AbilityDefinition.TimelineAction action,
                                                           String path) {
        if (action instanceof AbilityDefinition.Wait wait) {
            return ticks(wait.ticks(), path + ".ticks", true);
        }
        if (action instanceof AbilityDefinition.Telegraph telegraph) {
            if (telegraph.origin() == null) {
                return missing(path + ".origin", "telegraph origin is required");
            }
            AbilityPersistenceError error = ticks(telegraph.durationTicks(),
                    path + ".durationTicks", false);
            if (error != null) return error;
            return shape(telegraph.shape(), path + ".shape");
        }
        if (action instanceof AbilityDefinition.Damage damage) {
            if (damage.target() == null) return missing(path + ".target", "damage target is required");
            AbilityPersistenceError error = shape(damage.shape(), path + ".shape");
            if (error != null) return error;
            if (damage.damageType() == null) {
                return missing(path + ".damageType", "damage type is required");
            }
            if (damage.damageKind() == null) {
                return missing(path + ".damageKind", "damage kind is required");
            }
            if (damage.criticalAllowed() && !damage.damageKind().criticalAllowed()) {
                return error(AbilityPersistenceError.CONTRADICTORY_DEFINITION,
                        path + ".criticalAllowed",
                        "damage kind does not allow critical damage");
            }
            error = metadata(damage.damageType(), damage.metadata(), path + ".metadata");
            if (error != null) return error;
            error = finiteRange(damage.fixedDamage(), path + ".fixedDamage", 0.0,
                    CONTENT_BOUNDS.maxDamage(), true);
            if (error != null) return error;
            error = finiteRange(damage.coefficient(), path + ".coefficient", 0.0,
                    CONTENT_BOUNDS.maxCoefficient(), true);
            if (error != null) return error;
            if (Double.isFinite(damage.fixedDamage()) && Double.isFinite(damage.coefficient())
                    && damage.fixedDamage() == 0.0 && damage.coefficient() == 0.0) {
                return error(AbilityPersistenceError.CONTRADICTORY_DEFINITION, path,
                        "damage action has no damage component");
            }
            return null;
        }
        if (action instanceof AbilityDefinition.Charge charge) {
            if (charge.target() == null) return missing(path + ".target", "charge target is required");
            AbilityPersistenceError error = ticks(charge.durationTicks(),
                    path + ".durationTicks", false);
            if (error != null) return error;
            if (charge.path() == null) return missing(path + ".path", "charge path is required");
            if (!(charge.path() instanceof AbilityDefinition.Line)) {
                return error(AbilityPersistenceError.VARIANT_MISMATCH, path + ".path",
                        "charge path must use the line shape variant");
            }
            error = shape(charge.path(), path + ".path");
            if (error != null) return error;
            return finiteRange(charge.speed(), path + ".speed", 0.0,
                    CONTENT_BOUNDS.maxSpeed(), false);
        }
        if (action instanceof AbilityDefinition.Knockback knockback) {
            if (knockback.target() == null) {
                return missing(path + ".target", "knockback target is required");
            }
            AbilityPersistenceError error = shape(knockback.shape(), path + ".shape");
            if (error != null) return error;
            error = finiteRange(knockback.horizontalStrength(), path + ".horizontalStrength", 0.0,
                    CONTENT_BOUNDS.maxKnockbackStrength(), true);
            if (error != null) return error;
            error = finiteRange(knockback.verticalStrength(), path + ".verticalStrength",
                    -CONTENT_BOUNDS.maxKnockbackStrength(), CONTENT_BOUNDS.maxKnockbackStrength(),
                    true);
            if (error != null) return error;
            if (Double.isFinite(knockback.horizontalStrength())
                    && Double.isFinite(knockback.verticalStrength())
                    && knockback.horizontalStrength() == 0.0
                    && knockback.verticalStrength() == 0.0) {
                return error(AbilityPersistenceError.CONTRADICTORY_DEFINITION, path,
                        "knockback action has no impulse");
            }
            return null;
        }
        return error(AbilityPersistenceError.UNKNOWN_VARIANT, path,
                "unsupported timeline action implementation");
    }

    private static AbilityPersistenceError shape(AbilityDefinition.RelativeShape shape,
                                                 String path) {
        if (shape == null) return missing(path, "relative shape is required");
        if (shape instanceof AbilityDefinition.Circle circle) {
            return finiteRange(circle.radius(), path + ".radius", 0.0,
                    CONTENT_BOUNDS.maxShapeRadius(), false);
        }
        if (shape instanceof AbilityDefinition.Donut donut) {
            AbilityPersistenceError error = finiteRange(donut.innerRadius(), path + ".innerRadius",
                    0.0, CONTENT_BOUNDS.maxShapeRadius(), true);
            if (error != null) return error;
            error = finiteRange(donut.outerRadius(), path + ".outerRadius", 0.0,
                    CONTENT_BOUNDS.maxShapeRadius(), false);
            if (error != null) return error;
            if (Double.isFinite(donut.innerRadius()) && Double.isFinite(donut.outerRadius())
                    && donut.innerRadius() >= donut.outerRadius()) {
                return error(AbilityPersistenceError.CONTRADICTORY_DEFINITION, path,
                        "donut inner radius must be less than outer radius");
            }
            return null;
        }
        if (shape instanceof AbilityDefinition.Line line) {
            AbilityPersistenceError error = finiteRange(line.length(), path + ".length", 0.0,
                    CONTENT_BOUNDS.maxShapeLength(), false);
            if (error != null) return error;
            return finiteRange(line.width(), path + ".width", 0.0,
                    CONTENT_BOUNDS.maxShapeWidth(), false);
        }
        return error(AbilityPersistenceError.UNKNOWN_VARIANT, path,
                "unsupported relative shape implementation");
    }

    private static AbilityPersistenceError metadata(DamageType damageType,
                                                     AttackMetadata metadata, String path) {
        if (metadata == null) return missing(path, "damage metadata is required");
        if (metadata.tags() == null || metadata.elements() == null) {
            return error(AbilityPersistenceError.INVALID_VALUE, path,
                    "damage metadata contains an invalid component");
        }
        if (metadata.tags().size() > MAX_COLLECTION_ENTRIES) {
            return error(AbilityPersistenceError.COLLECTION_TOO_LARGE, path + ".tags",
                    "array contains more than 4096 entries");
        }
        for (AttackTag tag : metadata.tags()) {
            if (tag == null) {
                return error(AbilityPersistenceError.UNSUPPORTED_ENUM, path + ".tags",
                        "attack tag is unsupported");
            }
        }
        if (damageType == DamageType.PHYSICAL) {
            if (!metadata.hasTag(AttackTag.PHYSICAL)) {
                return error(AbilityPersistenceError.DAMAGE_TYPE_TAG_MISMATCH,
                        path + ".tags", "PHYSICAL damage requires PHYSICAL attack tag");
            }
            if (metadata.hasTag(AttackTag.MAGIC)) {
                return error(AbilityPersistenceError.CONTRADICTORY_DEFINITION,
                        path + ".tags", "PHYSICAL damage forbids MAGIC attack tag");
            }
        } else if (damageType == DamageType.MAGICAL) {
            if (!metadata.hasTag(AttackTag.MAGIC)) {
                return error(AbilityPersistenceError.DAMAGE_TYPE_TAG_MISMATCH,
                        path + ".tags", "MAGICAL damage requires MAGIC attack tag");
            }
            if (metadata.hasTag(AttackTag.PHYSICAL)) {
                return error(AbilityPersistenceError.CONTRADICTORY_DEFINITION,
                        path + ".tags", "MAGICAL damage forbids PHYSICAL attack tag");
            }
        }
        AbilityPersistenceError error = elementMap(metadata.elements().values(),
                path + ".elements.values");
        if (error != null) return error;
        error = elementMap(metadata.elements().scalingRates(), path + ".elements.scalingRates");
        if (error != null) return error;
        for (DamageElement element : DamageElement.values()) {
            AttackTag elementTag = tagFor(element);
            double value = metadata.elements().value(element);
            double scalingRate = metadata.elements().scalingRate(element);
            if (value > 0.0 && !metadata.hasTag(elementTag)) {
                return error(AbilityPersistenceError.ELEMENT_TAG_MISMATCH,
                        path + ".elements.values[" + element + "]",
                        "positive " + element + " value requires " + elementTag + " attack tag");
            }
            if (scalingRate > 0.0 && !metadata.hasTag(elementTag)) {
                return error(AbilityPersistenceError.ELEMENT_TAG_MISMATCH,
                        path + ".elements.scalingRates[" + element + "]",
                        "positive " + element + " scaling rate requires " + elementTag
                                + " attack tag");
            }
        }
        return null;
    }

    private static AbilityPersistenceError elementMap(Map<?, ?> values, String path) {
        if (values == null) return missing(path, "element map is required");
        if (values.size() > MAX_COLLECTION_ENTRIES) {
            return error(AbilityPersistenceError.COLLECTION_TOO_LARGE, path,
                    "map contains more than 4096 entries");
        }
        List<Map.Entry<?, ?>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
        for (Map.Entry<?, ?> entry : entries) {
            String entryPath = path + "[" + String.valueOf(entry.getKey()) + "]";
            if (!(entry.getKey() instanceof DamageElement)) {
                return error(AbilityPersistenceError.UNSUPPORTED_ENUM, entryPath,
                        "element key is unsupported");
            }
            if (!(entry.getValue() instanceof Number number)) {
                return missing(entryPath, "element value is required");
            }
            AbilityPersistenceError error = finiteRange(number.doubleValue(), entryPath, 0.0,
                    CONTENT_BOUNDS.maxElementValue(), true);
            if (error != null) return error;
        }
        return null;
    }

    private static AttackTag tagFor(DamageElement element) {
        return switch (element) {
            case FIRE -> AttackTag.FIRE;
            case ICE -> AttackTag.ICE;
            case LIGHTNING -> AttackTag.LIGHTNING;
        };
    }

    private static AbilityPersistenceError ticks(int value, String path, boolean allowZero) {
        if (value < 0 || !allowZero && value == 0) {
            return error(AbilityPersistenceError.NUMBER_OUT_OF_RANGE, path,
                    allowZero ? "ticks must be non-negative" : "ticks must be positive");
        }
        if (value > CONTENT_BOUNDS.maxTicks()) {
            return error(AbilityPersistenceError.NUMBER_OUT_OF_RANGE, path,
                    "ticks exceed the authoring bound");
        }
        return null;
    }

    private static AbilityPersistenceError text(String value, String path, int maximumLength) {
        if (value == null || value.isBlank()) return missing(path, "text value is required");
        if (value.length() > maximumLength) {
            return error(AbilityPersistenceError.NUMBER_OUT_OF_RANGE, path,
                    "text value exceeds its existing content bound");
        }
        if (!validUnicode(value)) {
            return error(AbilityPersistenceError.INVALID_VALUE, path,
                    "text value contains an unpaired surrogate");
        }
        return null;
    }

    private static AbilityPersistenceError localId(String value, String path) {
        if (!DefinitionSupport.isLocalId(value)) {
            return error(AbilityPersistenceError.INVALID_LOCAL_ID, path,
                    "local id must use the canonical lower-case grammar");
        }
        return null;
    }

    private static AbilityPersistenceError finiteRange(double value, String path, double minimum,
                                                       double maximum, boolean inclusiveMinimum) {
        if (!Double.isFinite(value)) {
            return error(AbilityPersistenceError.NON_FINITE_NUMBER, path, "number must be finite");
        }
        if (value < minimum || (!inclusiveMinimum && value == minimum) || value > maximum) {
            return error(AbilityPersistenceError.NUMBER_OUT_OF_RANGE, path,
                    "number is outside its existing content bound");
        }
        return null;
    }

    private static String pathForKey(String parent, String key) {
        if (key != null && key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return parent + "." + key;
        }
        String escaped = key == null ? "null" : key.replace("\\", "\\\\").replace("'", "\\'");
        return parent + "['" + escaped + "']";
    }

    private static String wireName(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static AbilityPersistenceError missing(String path, String detail) {
        return error(AbilityPersistenceError.MISSING_VALUE, path, detail);
    }

    private static AbilityPersistenceError error(String code, String path, String detail) {
        return new AbilityPersistenceError(code, path, bounded(detail, "persistence error"));
    }

    private static String bounded(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value;
        return result.length() <= 256 ? result : result.substring(0, 255) + "…";
    }

    private static byte[] strictUtf8(String value) throws CharacterCodingException {
        return StrictJson.encodeUtf8(value);
    }

    private static String strictUtf8(byte[] bytes) throws CharacterCodingException {
        return StrictJson.decodeUtf8(bytes);
    }

    private static boolean validUnicode(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(++index))) return false;
            } else if (Character.isLowSurrogate(character)) {
                return false;
            }
        }
        return true;
    }

    private static void fail(String code, String path, String detail) {
        throw new CodecFailure(error(code, path, detail));
    }

    private static final class CodecFailure extends RuntimeException {
        private final AbilityPersistenceError error;

        private CodecFailure(AbilityPersistenceError error) {
            super(error.detail());
            this.error = error;
        }

        private AbilityPersistenceError error() {
            return error;
        }
    }

    public record EncodeResult(byte[] bytes, AbilityPersistenceError error) {
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

        private static EncodeResult failure(AbilityPersistenceError error) {
            return new EncodeResult(null, Objects.requireNonNull(error, "error"));
        }
    }

    public record DecodeResult(AbilityDefinition definition, AbilityPersistenceError error) {
        public boolean success() {
            return error == null;
        }

        private static DecodeResult success(AbilityDefinition definition) {
            return new DecodeResult(definition, null);
        }

        private static DecodeResult failure(AbilityPersistenceError error) {
            return new DecodeResult(null, Objects.requireNonNull(error, "error"));
        }
    }
}
