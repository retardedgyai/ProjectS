package io.github.gyai.projects.feature;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class FeatureFlagServiceTest {
    public static void main(String[] args) throws Exception {
        allKeysAreCanonicalAndUnique();
        defaultsAndInvalidValuesAreDisabled();
        snapshotsAreImmutableAndDetached();
        concurrentReadsObserveCompleteSnapshots();
    }

    private static void allKeysAreCanonicalAndUnique() {
        assert FeatureKey.values().length == 18;
        assert FeatureKey.byId().size() == FeatureKey.values().length;
        assert FeatureKey.byId().keySet().equals(Set.of(
                "fire-system", "ice-system", "lightning-system",
                "equipment-v2", "mod-system", "player-persistence",
                "passive-tree", "gathering", "refining", "crafting",
                "tier-promotion", "enhancement-v2", "repair-v2",
                "party", "quests", "reward-v2", "mob-editor-v2",
                "client-beta-ui"));
        assert FeatureKey.fromId("fire-system").orElseThrow()
                == FeatureKey.FIRE_SYSTEM;
        assert FeatureKey.fromId("unknown").isEmpty();
        assert FeatureKey.fromId(null).isEmpty();
        expectUnsupported(() -> FeatureKey.byId().clear());
    }

    private static void defaultsAndInvalidValuesAreDisabled() {
        FeatureFlagService defaults = new FeatureFlagService();
        for (FeatureKey key : FeatureKey.values()) {
            assert !defaults.isEnabled(key) : key;
            assert !defaults.isEnabled(key.id()) : key;
        }
        assert !defaults.isEnabled((FeatureKey) null);
        assert !defaults.isEnabled((String) null);
        assert !defaults.isEnabled("unknown-feature");

        Map<String, Object> configured = new HashMap<>();
        configured.put("fire-system", true);
        configured.put("ice-system", "true");
        configured.put("lightning-system", 1);
        configured.put("equipment-v2", false);
        FeatureFlagService service = new FeatureFlagService(configured);
        assert service.isEnabled(FeatureKey.FIRE_SYSTEM);
        assert !service.isEnabled(FeatureKey.ICE_SYSTEM);
        assert !service.isEnabled(FeatureKey.LIGHTNING_SYSTEM);
        assert !service.isEnabled(FeatureKey.EQUIPMENT_V2);

        service.reload(null);
        for (FeatureKey key : FeatureKey.values()) assert !service.isEnabled(key);
    }

    private static void snapshotsAreImmutableAndDetached() {
        EnumMap<FeatureKey, Boolean> source = new EnumMap<>(FeatureKey.class);
        source.put(FeatureKey.PARTY, true);
        FeatureFlagSnapshot snapshot = FeatureFlagSnapshot.of(source);
        source.put(FeatureKey.PARTY, false);
        assert snapshot.isEnabled(FeatureKey.PARTY);
        assert snapshot.asMap().size() == FeatureKey.values().length;
        expectUnsupported(() -> snapshot.asMap().put(FeatureKey.QUESTS, true));

        Map<String, Object> configured = new HashMap<>();
        configured.put("quests", true);
        FeatureFlagService service = new FeatureFlagService(configured);
        FeatureFlagSnapshot beforeReload = service.snapshot();
        configured.put("quests", false);
        assert beforeReload.isEnabled(FeatureKey.QUESTS);
        assert service.isEnabled(FeatureKey.QUESTS);
        service.reload(configured);
        assert !service.isEnabled(FeatureKey.QUESTS);
        assert beforeReload.isEnabled(FeatureKey.QUESTS);
    }

    private static void concurrentReadsObserveCompleteSnapshots() throws Exception {
        FeatureFlagService service = new FeatureFlagService();
        var executor = Executors.newFixedThreadPool(4);
        try {
            for (int writer = 0; writer < 250; writer++) {
                boolean enabled = (writer & 1) == 0;
                service.reload(Map.of(
                        "party", enabled,
                        "quests", enabled,
                        "reward-v2", enabled));
                executor.submit(() -> {
                    FeatureFlagSnapshot snapshot = service.snapshot();
                    assert snapshot.asMap().size() == FeatureKey.values().length;
                    for (FeatureKey key : FeatureKey.values()) {
                        assert snapshot.asMap().containsKey(key);
                        assert snapshot.asMap().get(key) != null;
                    }
                });
            }
        } finally {
            executor.shutdown();
            assert executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }
}

