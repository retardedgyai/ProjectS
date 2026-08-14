package io.github.gyai.projects.content.persistence;

import io.github.gyai.projects.combat.damage.DamageElement;
import io.github.gyai.projects.content.definition.ContentDefinitionValidator;
import io.github.gyai.projects.content.definition.DefinitionSupport;
import io.github.gyai.projects.content.definition.MobDefinition;
import io.github.gyai.projects.content.persistence.StrictJson.JsonArrayValue;
import io.github.gyai.projects.content.persistence.StrictJson.JsonNull;
import io.github.gyai.projects.content.persistence.StrictJson.JsonNumber;
import io.github.gyai.projects.content.persistence.StrictJson.JsonObjectValue;
import io.github.gyai.projects.content.persistence.StrictJson.JsonString;
import io.github.gyai.projects.content.persistence.StrictJson.JsonValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Strict, deterministic JSON codec for the schema-v1 Mob content envelope.
 *
 * <p>The parser is deliberately independent of lenient JSON modes. It checks
 * duplicate keys while parsing, bounds containers before allocating their
 * contents, and reports failures with a stable code and JSON path.</p>
 */
public final class MobDefinitionJsonCodec {
    public static final String FORMAT = "projects-content";
    public static final int SCHEMA_VERSION = 1;
    public static final String KIND = "mob";
    public static final int MAX_DOCUMENT_BYTES = StrictJson.MAX_DOCUMENT_BYTES;
    public static final int MAX_NESTING_DEPTH = StrictJson.MAX_NESTING_DEPTH;
    public static final int MAX_COLLECTION_ENTRIES = StrictJson.MAX_COLLECTION_ENTRIES;
    public static final int MAX_STRING_LENGTH = StrictJson.MAX_STRING_LENGTH;

    private static final Set<String> ENVELOPE_KEYS = Set.of(
            "format", "schemaVersion", "kind", "id", "revision", "definition");
    private static final Set<String> DEFINITION_KEYS = Set.of(
            "presentation", "entityType", "category", "stats", "elementValues",
            "resistanceValues", "equipmentReferences", "abilityReferences");
    private static final Set<String> PRESENTATION_KEYS = Set.of("displayName", "nameplatePolicy");
    private static final Set<String> STATS_KEYS = Set.of(
            "maxHealth", "attackDamage", "movementSpeed", "knockbackResistance",
            "followRange", "scale");
    private static final ContentDefinitionValidator.Bounds CONTENT_BOUNDS =
            ContentDefinitionValidator.DEFAULT_BOUNDS;

    /** Encode a MobDefinition into canonical UTF-8 JSON with a final LF. */
    public EncodeResult encode(MobDefinition definition) {
        MobPersistenceError validation = validateDefinition(definition, "$");
        if (validation != null) return EncodeResult.failure(validation);
        try {
            String json = render(definition);
            byte[] bytes = strictUtf8(json + "\n");
            if (bytes.length > MAX_DOCUMENT_BYTES) {
                return EncodeResult.failure(error(MobPersistenceError.DOCUMENT_TOO_LARGE, "$",
                        "encoded document exceeds 1 MiB"));
            }
            return EncodeResult.success(bytes);
        } catch (CharacterCodingException exception) {
            return EncodeResult.failure(error(MobPersistenceError.INVALID_UTF8, "$",
                    "definition contains an invalid Unicode string"));
        } catch (RuntimeException exception) {
            return EncodeResult.failure(error(MobPersistenceError.INVALID_DEFINITION, "$",
                    bounded(exception.getMessage(), "definition cannot be encoded")));
        }
    }

    /** Decode one strict JSON document without throwing expected content errors. */
    public DecodeResult decode(byte[] bytes) {
        if (bytes == null) {
            return DecodeResult.failure(error(MobPersistenceError.INVALID_VALUE, "$",
                    "document bytes are required"));
        }
        if (bytes.length > MAX_DOCUMENT_BYTES) {
            return DecodeResult.failure(error(MobPersistenceError.DOCUMENT_TOO_LARGE, "$",
                    "document exceeds 1 MiB"));
        }
        if (hasUtf8Bom(bytes)) {
            return DecodeResult.failure(error(MobPersistenceError.BOM_REJECTED, "$",
                    "UTF-8 BOM is not permitted"));
        }
        final String json;
        try {
            json = strictUtf8(bytes);
        } catch (CharacterCodingException exception) {
            return DecodeResult.failure(error(MobPersistenceError.INVALID_UTF8, "$",
                    "document is not valid UTF-8"));
        }
        try {
            JsonValue root = StrictJson.parse(json);
            MobDefinition definition = decodeDefinition(root);
            return DecodeResult.success(definition);
        } catch (StrictJson.Failure failure) {
            StrictJson.Error error = failure.error();
            return DecodeResult.failure(error(error.code(), error.path(), error.detail()));
        } catch (CodecFailure failure) {
            return DecodeResult.failure(failure.error());
        } catch (RuntimeException exception) {
            return DecodeResult.failure(error(MobPersistenceError.INVALID_JSON, "$",
                    bounded(exception.getMessage(), "document is invalid")));
        }
    }

    private static MobDefinition decodeDefinition(JsonValue root) {
        JsonObjectValue envelope = object(root, "$");
        requireKeys(envelope, ENVELOPE_KEYS, "$");

        String format = string(required(envelope, "format", "$"), "$.format");
        if (!FORMAT.equals(format)) {
            fail(MobPersistenceError.WRONG_FORMAT, "$.format",
                    "format must be " + FORMAT);
        }
        int schemaVersion = integer(required(envelope, "schemaVersion", "$"),
                "$.schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            fail(MobPersistenceError.UNSUPPORTED_SCHEMA, "$.schemaVersion",
                    "only schema version 1 is supported");
        }
        String kind = string(required(envelope, "kind", "$"), "$.kind");
        if (!KIND.equals(kind)) {
            fail(MobPersistenceError.WRONG_KIND, "$.kind", "kind must be mob");
        }

        String id = string(required(envelope, "id", "$"), "$.id");
        if (!DefinitionSupport.isNamespacedId(id)) {
            fail(MobPersistenceError.INVALID_NAMESPACED_ID, "$.id",
                    "id must use the canonical lower-case namespaced grammar");
        }
        long revision = revision(required(envelope, "revision", "$"), "$.revision");
        if (revision < 0) {
            fail(MobPersistenceError.NEGATIVE_REVISION, "$.revision",
                    "revision must be non-negative");
        }

        JsonObjectValue payload = object(required(envelope, "definition", "$"), "$.definition");
        requireKeys(payload, DEFINITION_KEYS, "$.definition");
        MobDefinition.Presentation presentation = decodePresentation(
                required(payload, "presentation", "$.definition"));
        String entityType = string(required(payload, "entityType", "$.definition"),
                "$.definition.entityType");
        if (!DefinitionSupport.isNamespacedId(entityType)) {
            fail(MobPersistenceError.INVALID_NAMESPACED_ID, "$.definition.entityType",
                    "entityType must use the canonical lower-case namespaced grammar");
        }
        MobDefinition.Category category = category(
                string(required(payload, "category", "$.definition"), "$.definition.category"),
                "$.definition.category");
        MobDefinition.Stats stats = decodeStats(required(payload, "stats", "$.definition"));
        Map<DamageElement, Double> elementValues = decodeElementMap(
                required(payload, "elementValues", "$.definition"),
                "$.definition.elementValues");
        Map<DamageElement, Double> resistanceValues = decodeElementMap(
                required(payload, "resistanceValues", "$.definition"),
                "$.definition.resistanceValues");
        List<String> equipmentReferences = decodeReferences(
                required(payload, "equipmentReferences", "$.definition"),
                "$.definition.equipmentReferences");
        List<String> abilityReferences = decodeReferences(
                required(payload, "abilityReferences", "$.definition"),
                "$.definition.abilityReferences");

        MobDefinition definition = new MobDefinition(
                schemaVersion,
                id,
                revision,
                presentation,
                entityType,
                category,
                stats,
                elementValues,
                resistanceValues,
                equipmentReferences,
                abilityReferences);
        MobPersistenceError validation = validateDefinition(definition, "$");
        if (validation != null) throw new CodecFailure(validation);
        return definition;
    }

    private static MobDefinition.Presentation decodePresentation(JsonValue value) {
        JsonObjectValue object = object(value, "$.definition.presentation");
        requireKeys(object, PRESENTATION_KEYS, "$.definition.presentation");
        String displayName = string(required(object, "displayName", "$.definition.presentation"),
                "$.definition.presentation.displayName");
        String nameplatePolicy = string(
                required(object, "nameplatePolicy", "$.definition.presentation"),
                "$.definition.presentation.nameplatePolicy");
        return new MobDefinition.Presentation(displayName, nameplatePolicy);
    }

    private static MobDefinition.Stats decodeStats(JsonValue value) {
        JsonObjectValue object = object(value, "$.definition.stats");
        requireKeys(object, STATS_KEYS, "$.definition.stats");
        return new MobDefinition.Stats(
                number(required(object, "maxHealth", "$.definition.stats"),
                        "$.definition.stats.maxHealth"),
                number(required(object, "attackDamage", "$.definition.stats"),
                        "$.definition.stats.attackDamage"),
                number(required(object, "movementSpeed", "$.definition.stats"),
                        "$.definition.stats.movementSpeed"),
                number(required(object, "knockbackResistance", "$.definition.stats"),
                        "$.definition.stats.knockbackResistance"),
                number(required(object, "followRange", "$.definition.stats"),
                        "$.definition.stats.followRange"),
                number(required(object, "scale", "$.definition.stats"),
                        "$.definition.stats.scale"));
    }

    private static Map<DamageElement, Double> decodeElementMap(JsonValue value, String path) {
        JsonObjectValue object = object(value, path);
        if (object.values().size() > MAX_COLLECTION_ENTRIES) {
            fail(MobPersistenceError.COLLECTION_TOO_LARGE, path,
                    "map contains more than 4096 entries");
        }
        EnumMap<DamageElement, Double> result = new EnumMap<>(DamageElement.class);
        for (Map.Entry<String, JsonValue> entry : object.values().entrySet()) {
            DamageElement element = element(entry.getKey(), pathForKey(path, entry.getKey()));
            double amount = number(entry.getValue(), pathForKey(path, entry.getKey()));
            if (result.put(element, amount) != null) {
                fail(MobPersistenceError.DUPLICATE_KEY,
                        pathForKey(path, entry.getKey()), "duplicate element key");
            }
        }
        return Collections.unmodifiableMap(new EnumMap<>(result));
    }

    private static List<String> decodeReferences(JsonValue value, String path) {
        JsonArrayValue array = array(value, path);
        if (array.values().size() > MAX_COLLECTION_ENTRIES) {
            fail(MobPersistenceError.COLLECTION_TOO_LARGE, path,
                    "array contains more than 4096 entries");
        }
        List<String> result = new ArrayList<>(array.values().size());
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < array.values().size(); index++) {
            String entryPath = path + "[" + index + "]";
            JsonValue raw = array.values().get(index);
            if (raw instanceof JsonNull) {
                fail(MobPersistenceError.NULL_REQUIRED_FIELD, entryPath,
                        "reference must not be null");
            }
            String reference = string(raw, entryPath);
            if (!DefinitionSupport.isNamespacedId(reference)) {
                fail(MobPersistenceError.INVALID_NAMESPACED_ID, entryPath,
                        "reference must use the canonical lower-case namespaced grammar");
            }
            if (!seen.add(reference)) {
                fail(MobPersistenceError.DUPLICATE_REFERENCE, entryPath,
                        "reference is duplicated");
            }
            result.add(reference);
        }
        return List.copyOf(result);
    }

    private static DamageElement element(String value, String path) {
        for (DamageElement candidate : DamageElement.values()) {
            if (wireName(candidate).equals(value)) return candidate;
        }
        fail(MobPersistenceError.UNSUPPORTED_ENUM, path,
                "unsupported DamageElement wire value");
        throw new AssertionError("unreachable");
    }

    private static MobDefinition.Category category(String value, String path) {
        for (MobDefinition.Category candidate : MobDefinition.Category.values()) {
            if (wireName(candidate).equals(value)) return candidate;
        }
        fail(MobPersistenceError.UNSUPPORTED_ENUM, path,
                "unsupported mob category wire value");
        throw new AssertionError("unreachable");
    }

    private static int integer(JsonValue value, String path) {
        BigDecimal number = decimal(value, path);
        try {
            BigInteger integral = number.toBigIntegerExact();
            return integral.intValueExact();
        } catch (ArithmeticException exception) {
            fail(MobPersistenceError.INVALID_VALUE, path,
                    "schemaVersion must be an integral 32-bit number");
            throw new AssertionError("unreachable");
        }
    }

    private static long revision(JsonValue value, String path) {
        BigDecimal number = decimal(value, path);
        try {
            return number.toBigIntegerExact().longValueExact();
        } catch (ArithmeticException exception) {
            if (number.stripTrailingZeros().scale() > 0) {
                fail(MobPersistenceError.NON_INTEGRAL_NUMBER, path,
                        "revision must be integral");
            }
            fail(MobPersistenceError.REVISION_OVERFLOW, path,
                    "revision is outside the signed 64-bit range");
            throw new AssertionError("unreachable");
        }
    }

    private static double number(JsonValue value, String path) {
        BigDecimal decimal = decimal(value, path);
        double result = decimal.doubleValue();
        if (!Double.isFinite(result)) {
            fail(MobPersistenceError.NON_FINITE_NUMBER, path,
                    "number must be finite");
        }
        return result;
    }

    private static BigDecimal decimal(JsonValue value, String path) {
        if (!(value instanceof JsonNumber number)) {
            fail(MobPersistenceError.INVALID_VALUE, path, "number is required");
        }
        return ((JsonNumber) value).value();
    }

    private static JsonValue required(JsonObjectValue object, String key, String path) {
        if (!object.values().containsKey(key)) {
            fail(MobPersistenceError.MISSING_VALUE, pathForKey(path, key),
                    "required field is missing");
        }
        JsonValue value = object.values().get(key);
        if (value instanceof JsonNull) {
            fail(MobPersistenceError.NULL_REQUIRED_FIELD, pathForKey(path, key),
                    "required field must not be null");
        }
        return value;
    }

    private static JsonObjectValue object(JsonValue value, String path) {
        if (!(value instanceof JsonObjectValue object)) {
            fail(MobPersistenceError.INVALID_VALUE, path, "object is required");
        }
        return (JsonObjectValue) value;
    }

    private static JsonArrayValue array(JsonValue value, String path) {
        if (!(value instanceof JsonArrayValue array)) {
            fail(MobPersistenceError.INVALID_VALUE, path, "array is required");
        }
        return (JsonArrayValue) value;
    }

    private static String string(JsonValue value, String path) {
        if (!(value instanceof JsonString string)) {
            fail(MobPersistenceError.INVALID_VALUE, path, "string is required");
        }
        return ((JsonString) value).value();
    }

    private static void requireKeys(JsonObjectValue object, Set<String> expected, String path) {
        object.values().keySet().stream()
                .filter(key -> !expected.contains(key))
                .sorted()
                .findFirst()
                .ifPresent(key -> fail(MobPersistenceError.UNKNOWN_KEY,
                        pathForKey(path, key), "unknown object key"));
    }

    private static String render(MobDefinition definition) {
        StringBuilder out = new StringBuilder(2_048);
        out.append('{');
        boolean first = true;
        first = field(out, first, "format", quote(FORMAT));
        first = field(out, first, "schemaVersion", Integer.toString(SCHEMA_VERSION));
        first = field(out, first, "kind", quote(KIND));
        first = field(out, first, "id", quote(definition.mobId()));
        first = field(out, first, "revision", Long.toString(definition.revision()));
        first = field(out, first, "definition", renderPayload(definition));
        out.append('}');
        return out.toString();
    }

    private static String renderPayload(MobDefinition definition) {
        StringBuilder out = new StringBuilder(1_536);
        out.append('{');
        boolean first = true;
        first = field(out, first, "presentation", renderPresentation(definition.presentation()));
        first = field(out, first, "entityType", quote(definition.entityType()));
        first = field(out, first, "category", quote(wireName(definition.category())));
        first = field(out, first, "stats", renderStats(definition.stats()));
        first = field(out, first, "elementValues", renderElementMap(definition.elementValues()));
        first = field(out, first, "resistanceValues",
                renderElementMap(definition.resistanceValues()));
        first = field(out, first, "equipmentReferences",
                renderStrings(definition.equipmentReferences()));
        field(out, first, "abilityReferences", renderStrings(definition.abilityReferences()));
        out.append('}');
        return out.toString();
    }

    private static String renderPresentation(MobDefinition.Presentation presentation) {
        StringBuilder out = new StringBuilder(128);
        out.append('{');
        boolean first = true;
        first = field(out, first, "displayName", quote(presentation.displayName()));
        field(out, first, "nameplatePolicy", quote(presentation.nameplatePolicy()));
        out.append('}');
        return out.toString();
    }

    private static String renderStats(MobDefinition.Stats stats) {
        StringBuilder out = new StringBuilder(192);
        out.append('{');
        boolean first = true;
        first = field(out, first, "maxHealth", Double.toString(stats.maxHealth()));
        first = field(out, first, "attackDamage", Double.toString(stats.attackDamage()));
        first = field(out, first, "movementSpeed", Double.toString(stats.movementSpeed()));
        first = field(out, first, "knockbackResistance",
                Double.toString(stats.knockbackResistance()));
        first = field(out, first, "followRange", Double.toString(stats.followRange()));
        field(out, first, "scale", Double.toString(stats.scale()));
        out.append('}');
        return out.toString();
    }

    private static String renderElementMap(Map<DamageElement, Double> values) {
        StringBuilder out = new StringBuilder(96);
        out.append('{');
        boolean first = true;
        List<Map.Entry<DamageElement, Double>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Comparator.comparing(entry -> wireName(entry.getKey())));
        for (Map.Entry<DamageElement, Double> entry : entries) {
            first = field(out, first, wireName(entry.getKey()),
                    Double.toString(entry.getValue()));
        }
        out.append('}');
        return out.toString();
    }

    private static String renderStrings(List<String> values) {
        StringBuilder out = new StringBuilder(128);
        out.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) out.append(',');
            out.append(quote(values.get(index)));
        }
        out.append(']');
        return out.toString();
    }

    private static boolean field(StringBuilder out, boolean first, String name, String value) {
        return StrictJson.field(out, first, name, value);
    }

    private static String quote(String value) {
        return StrictJson.quote(value);
    }

    private static MobPersistenceError validateDefinition(MobDefinition definition, String path) {
        if (definition == null) {
            return error(MobPersistenceError.INVALID_DEFINITION, path,
                    "MobDefinition is required");
        }
        if (definition.schemaVersion() != MobDefinition.SCHEMA_VERSION) {
            return error(MobPersistenceError.UNSUPPORTED_SCHEMA, path + ".schemaVersion",
                    "only schema version 1 is supported");
        }
        if (!DefinitionSupport.isNamespacedId(definition.mobId())) {
            return error(MobPersistenceError.INVALID_NAMESPACED_ID, path + ".id",
                    "mob id must use the canonical lower-case namespaced grammar");
        }
        if (definition.revision() < 0) {
            return error(MobPersistenceError.NEGATIVE_REVISION, path + ".revision",
                    "revision must be non-negative");
        }
        if (definition.revision() > CONTENT_BOUNDS.maxLongRevision()) {
            return error(MobPersistenceError.NUMBER_OUT_OF_RANGE, path + ".revision",
                    "revision exceeds the existing content bound");
        }
        MobDefinition.Presentation presentation = definition.presentation();
        if (presentation == null) {
            return missing(path + ".definition.presentation", "presentation is required");
        }
        MobPersistenceError textError = text(presentation.displayName(),
                path + ".definition.presentation.displayName", 128);
        if (textError != null) return textError;
        textError = text(presentation.nameplatePolicy(),
                path + ".definition.presentation.nameplatePolicy", 64);
        if (textError != null) return textError;
        if (!DefinitionSupport.isNamespacedId(definition.entityType())) {
            return error(MobPersistenceError.INVALID_NAMESPACED_ID,
                    path + ".definition.entityType",
                    "entity type must use the canonical lower-case namespaced grammar");
        }
        if (definition.category() == null) {
            return missing(path + ".definition.category", "category is required");
        }
        MobDefinition.Stats stats = definition.stats();
        if (stats == null) return missing(path + ".definition.stats", "stats are required");
        MobPersistenceError numberError = finiteRange(stats.maxHealth(),
                path + ".definition.stats.maxHealth", 0.0, CONTENT_BOUNDS.maxHealth(), false);
        if (numberError != null) return numberError;
        numberError = finiteRange(stats.attackDamage(),
                path + ".definition.stats.attackDamage", 0.0,
                CONTENT_BOUNDS.maxAttackDamage(), true);
        if (numberError != null) return numberError;
        numberError = finiteRange(stats.movementSpeed(),
                path + ".definition.stats.movementSpeed", 0.0,
                CONTENT_BOUNDS.maxMovementSpeed(), false);
        if (numberError != null) return numberError;
        numberError = finiteRange(stats.knockbackResistance(),
                path + ".definition.stats.knockbackResistance", 0.0,
                CONTENT_BOUNDS.maxKnockbackResistance(), true);
        if (numberError != null) return numberError;
        numberError = finiteRange(stats.followRange(),
                path + ".definition.stats.followRange", 0.0,
                CONTENT_BOUNDS.maxFollowRange(), false);
        if (numberError != null) return numberError;
        numberError = finiteRange(stats.scale(), path + ".definition.stats.scale", 0.0,
                CONTENT_BOUNDS.maxScale(), false);
        if (numberError != null) return numberError;

        MobPersistenceError mapError = validateElementMap(definition.elementValues(),
                path + ".definition.elementValues");
        if (mapError != null) return mapError;
        mapError = validateElementMap(definition.resistanceValues(),
                path + ".definition.resistanceValues");
        if (mapError != null) return mapError;
        MobPersistenceError referencesError = validateReferences(definition.equipmentReferences(),
                path + ".definition.equipmentReferences");
        if (referencesError != null) return referencesError;
        return validateReferences(definition.abilityReferences(),
                path + ".definition.abilityReferences");
    }

    private static MobPersistenceError text(String value, String path, int maximumLength) {
        if (value == null || value.isBlank()) return missing(path, "text value is required");
        if (value.length() > maximumLength) {
            return error(MobPersistenceError.NUMBER_OUT_OF_RANGE, path,
                    "text value exceeds its existing content bound");
        }
        if (!validUnicode(value)) {
            return error(MobPersistenceError.INVALID_VALUE, path,
                    "text value contains an unpaired surrogate");
        }
        return null;
    }

    private static MobPersistenceError finiteRange(double value, String path, double minimum,
                                                   double maximum, boolean inclusiveMinimum) {
        if (!Double.isFinite(value)) {
            return error(MobPersistenceError.NON_FINITE_NUMBER, path, "number must be finite");
        }
        if (value < minimum || (!inclusiveMinimum && value == minimum) || value > maximum) {
            return error(MobPersistenceError.NUMBER_OUT_OF_RANGE, path,
                    "number is outside its existing content bound");
        }
        return null;
    }

    private static MobPersistenceError validateElementMap(Map<?, ?> values, String path) {
        if (values == null) return missing(path, "element map is required");
        if (values.size() > MAX_COLLECTION_ENTRIES) {
            return error(MobPersistenceError.COLLECTION_TOO_LARGE, path,
                    "map contains more than 4096 entries");
        }
        List<Map.Entry<?, ?>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
        for (Map.Entry<?, ?> entry : entries) {
            String entryPath = pathForKey(path, String.valueOf(entry.getKey()));
            if (!(entry.getKey() instanceof DamageElement element)) {
                return error(MobPersistenceError.UNSUPPORTED_ENUM, entryPath,
                        "element key is unsupported");
            }
            if (!(entry.getValue() instanceof Number number)) {
                return missing(entryPath, "element value is required");
            }
            MobPersistenceError valueError = finiteRange(number.doubleValue(), entryPath, 0.0,
                    CONTENT_BOUNDS.maxElementValue(), true);
            if (valueError != null) return valueError;
            if (element == null) {
                return error(MobPersistenceError.UNSUPPORTED_ENUM, entryPath,
                        "element key is unsupported");
            }
        }
        return null;
    }

    private static MobPersistenceError validateReferences(List<String> values, String path) {
        if (values == null) return missing(path, "reference collection is required");
        if (values.size() > MAX_COLLECTION_ENTRIES) {
            return error(MobPersistenceError.COLLECTION_TOO_LARGE, path,
                    "array contains more than 4096 entries");
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String entryPath = path + "[" + index + "]";
            String value = values.get(index);
            if (!DefinitionSupport.isNamespacedId(value)) {
                return error(MobPersistenceError.INVALID_NAMESPACED_ID, entryPath,
                        "reference must use the canonical lower-case namespaced grammar");
            }
            if (!seen.add(value)) {
                return error(MobPersistenceError.DUPLICATE_REFERENCE, entryPath,
                        "reference is duplicated");
            }
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

    private static MobPersistenceError missing(String path, String detail) {
        return error(MobPersistenceError.MISSING_VALUE, path, detail);
    }

    private static MobPersistenceError error(String code, String path, String detail) {
        return new MobPersistenceError(code, path, bounded(detail, "persistence error"));
    }

    private static String bounded(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value;
        return result.length() <= 256 ? result : result.substring(0, 255) + "…";
    }

    private static boolean hasUtf8Bom(byte[] bytes) {
        return StrictJson.hasUtf8Bom(bytes);
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
        private final MobPersistenceError error;

        private CodecFailure(MobPersistenceError error) {
            super(error.detail());
            this.error = error;
        }

        private MobPersistenceError error() {
            return error;
        }
    }

    public record EncodeResult(byte[] bytes, MobPersistenceError error) {
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

        private static EncodeResult failure(MobPersistenceError error) {
            return new EncodeResult(null, Objects.requireNonNull(error, "error"));
        }
    }

    public record DecodeResult(MobDefinition definition, MobPersistenceError error) {
        public boolean success() {
            return error == null;
        }

        private static DecodeResult success(MobDefinition definition) {
            return new DecodeResult(definition, null);
        }

        private static DecodeResult failure(MobPersistenceError error) {
            return new DecodeResult(null, Objects.requireNonNull(error, "error"));
        }
    }
}
