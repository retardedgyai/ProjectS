package io.github.gyai.projects.quest;

import io.github.gyai.projects.transaction.DomainId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record QuestProgressSnapshot(
        UUID playerId,
        QuestDefinitionRef definition,
        State state,
        Map<String, Long> counters,
        Set<String> markers,
        boolean completionMarked,
        boolean claimedMarked,
        long progressRevision
) {
    private static final int MAX_ENTRIES = 256;

    public QuestProgressSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(state, "state");
        if (progressRevision < 0) throw new IllegalArgumentException("Negative progress revision");
        Map<String, Long> sourceCounters = counters == null ? Map.of() : counters;
        Set<String> sourceMarkers = markers == null ? Set.of() : markers;
        if (sourceCounters.size() > MAX_ENTRIES || sourceMarkers.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Quest progress exceeds bounds");
        }
        LinkedHashMap<String, Long> counterCopy = new LinkedHashMap<>();
        sourceCounters.forEach((id, value) -> {
            String canonical = DomainId.requireNamespaced(id, "quest counter ID");
            if (value == null || value < 0) throw new IllegalArgumentException("Negative counter");
            counterCopy.put(canonical, value);
        });
        LinkedHashSet<String> markerCopy = new LinkedHashSet<>();
        sourceMarkers.forEach(id -> markerCopy.add(
                DomainId.requireNamespaced(id, "quest marker ID")));
        counters = Map.copyOf(counterCopy);
        markers = Set.copyOf(markerCopy);
        if (completionMarked != (state == State.COMPLETED)) {
            throw new IllegalArgumentException("Completion marker and state disagree");
        }
        if (claimedMarked && !completionMarked) {
            throw new IllegalArgumentException("Uncompleted quest cannot be claimed");
        }
    }

    public enum State { ACTIVE, COMPLETED }
}
