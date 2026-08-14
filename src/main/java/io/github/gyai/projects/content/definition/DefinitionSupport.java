package io.github.gyai.projects.content.definition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Small immutable-value helpers shared by the schema-v1 documents. */
public final class DefinitionSupport {
    static final int MAX_ID_LENGTH = 96;
    static final Pattern NAMESPACED_ID = Pattern.compile(
            "[a-z][a-z0-9._-]*:[a-z][a-z0-9._/-]*");
    static final Pattern LOCAL_ID = Pattern.compile("[a-z][a-z0-9_-]{0,31}");

    private DefinitionSupport() {
    }

    static <T> List<T> immutableList(Collection<? extends T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    static <T> Set<T> immutableSet(Collection<? extends T> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    static <K, V> Map<K, V> immutableMap(Map<? extends K, ? extends V> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * Returns whether {@code value} uses the canonical lower-case namespaced ID
     * grammar shared by content definitions and persistence.
     */
    public static boolean isNamespacedId(String value) {
        return value != null
                && value.length() <= MAX_ID_LENGTH
                && value.equals(value.toLowerCase(Locale.ROOT))
                && NAMESPACED_ID.matcher(value).matches()
                && !value.contains("..")
                && !value.contains("//")
                && !value.endsWith("/");
    }

    /** Returns whether {@code value} uses the canonical local ID grammar. */
    public static boolean isLocalId(String value) {
        return value != null && LOCAL_ID.matcher(value).matches();
    }
}
