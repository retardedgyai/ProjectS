package io.github.gyai.projects.combat.telegraph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Deterministically selects oldest active telegraphs to evict before insertion. */
public final class TelegraphCapacityPolicy {
    private final int maximumGlobal;
    private final int maximumPerSource;

    public TelegraphCapacityPolicy(int maximumGlobal, int maximumPerSource) {
        if (maximumGlobal < 1 || maximumPerSource < 1) {
            throw new IllegalArgumentException("Telegraph limits must be positive");
        }
        this.maximumGlobal = maximumGlobal;
        this.maximumPerSource = maximumPerSource;
    }

    public List<UUID> evictionsBeforeInsert(
            List<ActiveEntry> active,
            UUID sourceId
    ) {
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(sourceId, "sourceId");
        LinkedHashMap<UUID, UUID> retained = new LinkedHashMap<>();
        for (ActiveEntry entry : active) {
            ActiveEntry nonNull = Objects.requireNonNull(entry, "active entry");
            retained.put(nonNull.id(), nonNull.sourceId());
        }
        List<UUID> evictions = new ArrayList<>();
        while (countSource(retained, sourceId) >= maximumPerSource) {
            UUID oldest = oldestForSource(retained, sourceId);
            if (oldest == null) break;
            retained.remove(oldest);
            evictions.add(oldest);
        }
        while (retained.size() >= maximumGlobal) {
            UUID oldest = retained.keySet().iterator().next();
            retained.remove(oldest);
            evictions.add(oldest);
        }
        return List.copyOf(evictions);
    }

    private static long countSource(Map<UUID, UUID> active, UUID sourceId) {
        return active.values().stream().filter(sourceId::equals).count();
    }

    private static UUID oldestForSource(
            Map<UUID, UUID> active,
            UUID sourceId
    ) {
        for (Map.Entry<UUID, UUID> entry : active.entrySet()) {
            if (sourceId.equals(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    public record ActiveEntry(UUID id, UUID sourceId) {
        public ActiveEntry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(sourceId, "sourceId");
        }
    }
}
