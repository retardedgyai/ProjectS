package io.github.gyai.projects.player.progress;

import java.util.Collection;
import java.util.Map;

final class PlayerProgressValidation {
    static final int MAX_LEVEL = 45;
    static final int MAX_ID_LENGTH = 128;
    static final int MAX_CONTAINER_ENTRIES = 4_096;
    static final int MAX_SETTING_VALUE_LENGTH = 1_024;
    private static final String ID_PATTERN =
            "[a-z0-9][a-z0-9._:-]{0," + (MAX_ID_LENGTH - 1) + "}";

    private PlayerProgressValidation() {
    }

    static String canonicalId(String value, String name) {
        if (value == null || !value.matches(ID_PATTERN)) {
            throw new IllegalArgumentException(name + " must be a canonical ID");
        }
        return value;
    }

    static long nonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    static int level(int value) {
        if (value < 1 || value > MAX_LEVEL) {
            throw new IllegalArgumentException("level must be between 1 and 45");
        }
        return value;
    }

    static void bounded(Collection<?> values, String name) {
        if (values.size() > MAX_CONTAINER_ENTRIES) {
            throw new IllegalArgumentException(name + " exceeds entry limit");
        }
    }

    static void bounded(Map<?, ?> values, String name) {
        if (values.size() > MAX_CONTAINER_ENTRIES) {
            throw new IllegalArgumentException(name + " exceeds entry limit");
        }
    }

    static String settingValue(String value) {
        if (value == null || value.length() > MAX_SETTING_VALUE_LENGTH) {
            throw new IllegalArgumentException("setting value exceeds limit");
        }
        return value;
    }
}
