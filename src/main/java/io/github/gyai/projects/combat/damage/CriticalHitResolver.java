package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.combat.stat.StatCalculator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;

public final class CriticalHitResolver {
    private final int maximumEntries;
    private final Map<CriticalKey, Boolean> results;

    public CriticalHitResolver(int maximumEntries) {
        if (maximumEntries < 1) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        this.maximumEntries = maximumEntries;
        results = new LinkedHashMap<>(128, .75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CriticalKey, Boolean> eldest) {
                return size() > CriticalHitResolver.this.maximumEntries;
            }
        };
    }

    public boolean resolve(
            UUID attackerId,
            UUID castId,
            double criticalChance,
            DoubleSupplier roll
    ) {
        if (attackerId == null || castId == null || roll == null) {
            throw new IllegalArgumentException("Critical resolution inputs must not be null");
        }
        CriticalKey key = new CriticalKey(attackerId, castId);
        return results.computeIfAbsent(key, ignored ->
                StatCalculator.clamp01(roll.getAsDouble())
                        < StatCalculator.criticalChanceForRoll(criticalChance));
    }

    public void clear() {
        results.clear();
    }

    private record CriticalKey(UUID attackerId, UUID castId) {
    }
}
