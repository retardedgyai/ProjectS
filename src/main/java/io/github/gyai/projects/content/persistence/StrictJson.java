package io.github.gyai.projects.content.persistence;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Package-private strict JSON mechanics shared by typed content codecs.
 *
 * <p>This class deliberately stops at JSON values. Envelope fields, schema
 * semantics, and type-specific errors remain in each typed codec.</p>
 */
final class StrictJson {
    static final int MAX_DOCUMENT_BYTES = 1_048_576;
    static final int MAX_NESTING_DEPTH = 32;
    static final int MAX_COLLECTION_ENTRIES = 4_096;
    static final int MAX_STRING_LENGTH = 8_192;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static final class Codes {
        private static final String INVALID_JSON = "INVALID_JSON";
        private static final String TRAILING_DATA = "TRAILING_DATA";
        private static final String DUPLICATE_KEY = "DUPLICATE_KEY";
        private static final String NON_FINITE_NUMBER = "NON_FINITE_NUMBER";
        private static final String COLLECTION_TOO_LARGE = "COLLECTION_TOO_LARGE";
        private static final String NESTING_TOO_DEEP = "NESTING_TOO_DEEP";
        private static final String STRING_TOO_LONG = "STRING_TOO_LONG";

        private Codes() {
        }
    }

    private StrictJson() {
    }

    static JsonValue parse(String input) {
        return new Parser(input).parse();
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\u2028' -> out.append("\\u2028");
                case '\u2029' -> out.append("\\u2029");
                default -> {
                    if (character < 0x20) {
                        out.append("\\u00");
                        out.append(HEX[(character >>> 4) & 0x0f]);
                        out.append(HEX[character & 0x0f]);
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    static boolean field(StringBuilder out, boolean first, String name, String value) {
        if (!first) out.append(',');
        out.append(quote(name)).append(':').append(value);
        return false;
    }

    static byte[] encodeUtf8(String value) throws CharacterCodingException {
        ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        return bytes;
    }

    static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf;
    }

    interface JsonValue {
    }

    record JsonObjectValue(LinkedHashMap<String, JsonValue> values) implements JsonValue {
    }

    record JsonArrayValue(List<JsonValue> values) implements JsonValue {
    }

    record JsonString(String value) implements JsonValue {
    }

    record JsonNumber(BigDecimal value) implements JsonValue {
    }

    record JsonBoolean(boolean value) implements JsonValue {
    }

    enum JsonNull implements JsonValue {
        INSTANCE
    }

    static final class Failure extends RuntimeException {
        private final Error error;

        private Failure(Error error) {
            super(error.detail());
            this.error = error;
        }

        Error error() {
            return error;
        }
    }

    record Error(String code, String path, String detail) {
    }

    private static void fail(String code, String path, String detail) {
        throw new Failure(new Error(code, path, detail));
    }

    private static final class Parser {
        private final String input;
        private int index;

        private Parser(String input) {
            this.input = input;
        }

        private JsonValue parse() {
            skipWhitespace();
            if (index == input.length()) {
                fail(Codes.INVALID_JSON, "$", "document is empty");
            }
            JsonValue value = parseValue("$", 1);
            skipWhitespace();
            if (index != input.length()) {
                fail(Codes.TRAILING_DATA, "$",
                        "trailing JSON data is not permitted");
            }
            return value;
        }

        private JsonValue parseValue(String path, int depth) {
            skipWhitespace();
            if (index >= input.length()) {
                fail(Codes.INVALID_JSON, path, "value is missing");
            }
            char character = input.charAt(index);
            return switch (character) {
                case '{' -> parseObject(path, depth);
                case '[' -> parseArray(path, depth);
                case '"' -> new JsonString(parseString(path));
                case 't' -> literal("true", path, new JsonBoolean(true));
                case 'f' -> literal("false", path, new JsonBoolean(false));
                case 'n' -> literal("null", path, JsonNull.INSTANCE);
                case 'N', 'I' -> {
                    fail(Codes.NON_FINITE_NUMBER, path,
                            "NaN and Infinity are not valid JSON numbers");
                    yield JsonNull.INSTANCE;
                }
                default -> {
                    if (character == '-' || character >= '0' && character <= '9') {
                        yield parseNumber(path);
                    }
                    fail(Codes.INVALID_JSON, path,
                            "unexpected JSON token");
                    yield JsonNull.INSTANCE;
                }
            };
        }

        private JsonObjectValue parseObject(String path, int depth) {
            checkDepth(path, depth);
            index++;
            skipWhitespace();
            LinkedHashMap<String, JsonValue> values = new LinkedHashMap<>();
            if (consume('}')) return new JsonObjectValue(values);
            while (true) {
                if (index >= input.length() || input.charAt(index) != '"') {
                    fail(Codes.INVALID_JSON, path,
                            "object key must be a JSON string");
                }
                String key = parseString(path);
                String keyPath = pathForKey(path, key);
                if (values.containsKey(key)) {
                    fail(Codes.DUPLICATE_KEY, keyPath,
                            "object key is duplicated");
                }
                skipWhitespace();
                if (!consume(':')) {
                    fail(Codes.INVALID_JSON, keyPath,
                            "object key must be followed by a colon");
                }
                if (values.size() >= MAX_COLLECTION_ENTRIES) {
                    fail(Codes.COLLECTION_TOO_LARGE, path,
                            "object contains more than 4096 entries");
                }
                values.put(key, parseValue(keyPath, depth + 1));
                skipWhitespace();
                if (consume('}')) break;
                if (!consume(',')) {
                    fail(Codes.INVALID_JSON, path,
                            "object members must be comma-separated");
                }
                skipWhitespace();
                if (consume('}')) {
                    fail(Codes.INVALID_JSON, path,
                            "trailing comma is not permitted");
                }
            }
            return new JsonObjectValue(values);
        }

        private JsonArrayValue parseArray(String path, int depth) {
            checkDepth(path, depth);
            index++;
            skipWhitespace();
            List<JsonValue> values = new ArrayList<>();
            if (consume(']')) return new JsonArrayValue(List.copyOf(values));
            while (true) {
                if (values.size() >= MAX_COLLECTION_ENTRIES) {
                    fail(Codes.COLLECTION_TOO_LARGE, path,
                            "array contains more than 4096 entries");
                }
                values.add(parseValue(path + "[" + values.size() + "]", depth + 1));
                skipWhitespace();
                if (consume(']')) break;
                if (!consume(',')) {
                    fail(Codes.INVALID_JSON, path,
                            "array values must be comma-separated");
                }
                skipWhitespace();
                if (consume(']')) {
                    fail(Codes.INVALID_JSON, path,
                            "trailing comma is not permitted");
                }
            }
            return new JsonArrayValue(List.copyOf(values));
        }

        private JsonValue literal(String literal, String path, JsonValue value) {
            if (!input.startsWith(literal, index)) {
                fail(Codes.INVALID_JSON, path, "invalid JSON literal");
            }
            index += literal.length();
            return value;
        }

        private JsonNumber parseNumber(String path) {
            int start = index;
            if (consume('-') && index >= input.length()) {
                fail(Codes.INVALID_JSON, path, "invalid JSON number");
            }
            if (index < input.length() && input.charAt(index) == '0') {
                index++;
                if (index < input.length() && Character.isDigit(input.charAt(index))) {
                    fail(Codes.INVALID_JSON, path,
                            "leading zero is not permitted");
                }
            } else {
                if (index >= input.length() || !isDigitOneToNine(input.charAt(index))) {
                    fail(Codes.INVALID_JSON, path, "invalid JSON number");
                }
                while (index < input.length() && isDigit(input.charAt(index))) index++;
            }
            if (consume('.')) {
                int fractionStart = index;
                while (index < input.length() && isDigit(input.charAt(index))) index++;
                if (fractionStart == index) {
                    fail(Codes.INVALID_JSON, path,
                            "fraction must contain at least one digit");
                }
            }
            if (index < input.length()
                    && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
                index++;
                if (index < input.length()
                        && (input.charAt(index) == '+' || input.charAt(index) == '-')) index++;
                int exponentStart = index;
                while (index < input.length() && isDigit(input.charAt(index))) index++;
                if (exponentStart == index) {
                    fail(Codes.INVALID_JSON, path,
                            "exponent must contain at least one digit");
                }
            }
            String token = input.substring(start, index);
            try {
                return new JsonNumber(new BigDecimal(token));
            } catch (NumberFormatException exception) {
                    fail(Codes.INVALID_JSON, path, "invalid JSON number");
                throw new AssertionError("unreachable");
            }
        }

        private String parseString(String path) {
            if (!consume('"')) {
                fail(Codes.INVALID_JSON, path, "string must start with a quote");
            }
            StringBuilder value = new StringBuilder();
            while (index < input.length()) {
                char character = input.charAt(index++);
                if (character == '"') return value.toString();
                if (character < 0x20) {
                    fail(Codes.INVALID_JSON, path,
                            "unescaped control character in string");
                }
                if (character != '\\') {
                    value.append(character);
                    checkStringLength(value.length(), path);
                    continue;
                }
                if (index >= input.length()) {
                    fail(Codes.INVALID_JSON, path,
                            "unterminated string escape");
                }
                char escape = input.charAt(index++);
                switch (escape) {
                    case '"', '\\', '/' -> value.append(escape);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> appendUnicodeEscape(value, path);
                    default -> fail(Codes.INVALID_JSON, path,
                            "unsupported string escape");
                }
                checkStringLength(value.length(), path);
            }
            fail(Codes.INVALID_JSON, path, "unterminated string");
            throw new AssertionError("unreachable");
        }

        private void appendUnicodeEscape(StringBuilder value, String path) {
            char high = unicodeUnit(path);
            if (Character.isHighSurrogate(high)) {
                if (index + 1 >= input.length()
                        || input.charAt(index) != '\\' || input.charAt(index + 1) != 'u') {
                    fail(Codes.INVALID_JSON, path,
                            "high surrogate must be followed by a low surrogate escape");
                }
                index += 2;
                char low = unicodeUnit(path);
                if (!Character.isLowSurrogate(low)) {
                    fail(Codes.INVALID_JSON, path,
                            "high surrogate must be followed by a low surrogate");
                }
                value.appendCodePoint(Character.toCodePoint(high, low));
            } else if (Character.isLowSurrogate(high)) {
                fail(Codes.INVALID_JSON, path,
                        "low surrogate must follow a high surrogate");
            } else {
                value.append(high);
            }
        }

        private char unicodeUnit(String path) {
            if (index + 4 > input.length()) {
                fail(Codes.INVALID_JSON, path,
                        "Unicode escape must contain four hexadecimal digits");
            }
            int value = 0;
            for (int offset = 0; offset < 4; offset++) {
                int digit = Character.digit(input.charAt(index++), 16);
                if (digit < 0) {
                fail(Codes.INVALID_JSON, path,
                            "Unicode escape contains a non-hexadecimal digit");
                }
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private void checkDepth(String path, int depth) {
            if (depth > MAX_NESTING_DEPTH) {
                fail(Codes.NESTING_TOO_DEEP, path,
                        "JSON nesting exceeds depth 32");
            }
        }

        private void checkStringLength(int length, String path) {
            if (length > MAX_STRING_LENGTH) {
                fail(Codes.STRING_TOO_LONG, path,
                        "JSON string exceeds 8192 characters");
            }
        }

        private void skipWhitespace() {
            while (index < input.length()) {
                char character = input.charAt(index);
                if (character == ' ' || character == '\t'
                        || character == '\n' || character == '\r') {
                    index++;
                } else {
                    return;
                }
            }
        }

        private boolean consume(char expected) {
            if (index < input.length() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private static boolean isDigit(char value) {
            return value >= '0' && value <= '9';
        }

        private static boolean isDigitOneToNine(char value) {
            return value >= '1' && value <= '9';
        }

        private static String pathForKey(String parent, String key) {
            if (key != null && key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                return parent + "." + key;
            }
            String escaped = key == null ? "null" : key.replace("\\", "\\\\")
                    .replace("'", "\\'");
            return parent + "['" + escaped + "']";
        }
    }
}
