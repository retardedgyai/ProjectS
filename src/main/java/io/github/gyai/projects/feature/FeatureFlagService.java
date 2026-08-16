package io.github.gyai.projects.feature;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class FeatureFlagService {
    private final AtomicReference<FeatureFlagSnapshot> current =
            new AtomicReference<>(FeatureFlagSnapshot.allDisabled());

    public FeatureFlagService() {
    }

    public FeatureFlagService(Map<String, ?> configuredValues) {
        reload(configuredValues);
    }

    public FeatureFlagSnapshot snapshot() {
        return current.get();
    }

    public boolean isEnabled(FeatureKey key) {
        return current.get().isEnabled(key);
    }

    public boolean isEnabled(String id) {
        return current.get().isEnabled(id);
    }

    public FeatureFlagSnapshot reload(Map<String, ?> configuredValues) {
        EnumMap<FeatureKey, Boolean> parsed = new EnumMap<>(FeatureKey.class);
        for (FeatureKey key : FeatureKey.values()) {
            Object raw = configuredValues == null ? null : configuredValues.get(key.id());
            parsed.put(key, raw instanceof Boolean value && value);
        }
        FeatureFlagSnapshot replacement = FeatureFlagSnapshot.of(parsed);
        current.set(replacement);
        return replacement;
    }
}

