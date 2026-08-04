package io.github.gyai.projects.transaction;

import java.util.Objects;
import java.util.regex.Pattern;

public final class DomainId {
    private static final int MAX_LENGTH = 96;
    private static final Pattern NAMESPACED = Pattern.compile(
            "[a-z][a-z0-9._-]*:[a-z][a-z0-9._/-]*");

    private DomainId() {
    }

    public static String requireNamespaced(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.length() > MAX_LENGTH
                || !NAMESPACED.matcher(value).matches()
                || value.contains("..")
                || value.contains("//")
                || value.endsWith("/")) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
        return value;
    }

    public static String requireKey(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank() || value.length() > MAX_LENGTH
                || value.contains("..") || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid " + label);
        }
        return value;
    }
}
