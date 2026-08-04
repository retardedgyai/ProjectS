package io.github.gyai.projects.feature;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class FeatureFlagSnapshot {
    private final Map<FeatureKey, Boolean> flags;

    private FeatureFlagSnapshot(Map<FeatureKey, Boolean> source) {
        EnumMap<FeatureKey, Boolean> values = new EnumMap<>(FeatureKey.class);
        for (FeatureKey key : FeatureKey.values()) {
            values.put(key, source != null && Boolean.TRUE.equals(source.get(key)));
        }
        flags = Collections.unmodifiableMap(values);
    }

    public static FeatureFlagSnapshot allDisabled() {
        return new FeatureFlagSnapshot(Map.of());
    }

    public static FeatureFlagSnapshot of(Map<FeatureKey, Boolean> flags) {
        return new FeatureFlagSnapshot(flags);
    }

    public boolean isEnabled(FeatureKey key) {
        return key != null && Boolean.TRUE.equals(flags.get(key));
    }

    public boolean isEnabled(String id) {
        return FeatureKey.fromId(id).map(this::isEnabled).orElse(false);
    }

    public Map<FeatureKey, Boolean> asMap() {
        return flags;
    }
}

