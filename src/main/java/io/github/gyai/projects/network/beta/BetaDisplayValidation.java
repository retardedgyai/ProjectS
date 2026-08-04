package io.github.gyai.projects.network.beta;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

final class BetaDisplayValidation {
    private BetaDisplayValidation() {
    }

    static String string(String value, String name) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length
                > BetaProtocolLimits.DEFAULTS.stringBytes()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    static String id(String value, String name) {
        string(value, name);
        if (value.isBlank() || value.length() > 128
                || !value.matches("[a-z0-9][a-z0-9._:/-]*")) {
            throw new IllegalArgumentException(name + " is not canonical");
        }
        return value;
    }

    static <T> List<T> list(List<T> values, int maximum, String name) {
        List<T> copy = List.copyOf(values == null ? List.of() : values);
        if (copy.size() > maximum) throw new IllegalArgumentException(name + " is oversized");
        return copy;
    }

    static <K, V> Map<K, V> map(Map<K, V> values, int maximum, String name) {
        Map<K, V> copy = Map.copyOf(values == null ? Map.of() : values);
        if (copy.size() > maximum) throw new IllegalArgumentException(name + " is oversized");
        return copy;
    }

    static double finite(double value, String name) {
        BetaProtocolCodec.requireFinite(value, name);
        return value;
    }
}
