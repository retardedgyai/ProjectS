package io.github.gyai.projects.equipment;

import java.util.regex.Pattern;

public final class MetadataIds {
    private static final Pattern CANONICAL = Pattern.compile(
            "[a-z0-9][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9-]{0,63}");
    private MetadataIds() { }
    public static String requireCanonical(String name, String value) {
        if (value == null || !CANONICAL.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a canonical namespaced ID");
        }
        return value;
    }
    public static String requireBoundedText(String name, String value, int maximum) {
        if (value == null || value.length() > maximum || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " is missing or oversized");
        }
        return value;
    }
}
