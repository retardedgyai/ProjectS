package io.github.gyai.projects.player.progress;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Bukkit-free, content-agnostic quest persistence container. */
public record QuestProgressState(
        String stateId,
        Map<String, Long> counters,
        Set<String> claimMarkerIds
) {
    public QuestProgressState {
        stateId = PlayerProgressValidation.canonicalId(stateId, "quest state ID");
        counters = immutableQuantities(counters, "quest counter");
        claimMarkerIds = immutableIds(claimMarkerIds, "claim marker ID");
    }

    private static Map<String, Long> immutableQuantities(
            Map<String, Long> values,
            String name
    ) {
        Map<String, Long> source = values == null ? Map.of() : values;
        PlayerProgressValidation.bounded(source, name + "s");
        LinkedHashMap<String, Long> copy = new LinkedHashMap<>();
        source.forEach((id, value) -> copy.put(
                PlayerProgressValidation.canonicalId(id, name),
                PlayerProgressValidation.nonNegative(
                        value == null ? -1L : value, name + " value")));
        return Map.copyOf(copy);
    }

    private static Set<String> immutableIds(Set<String> values, String name) {
        Set<String> source = values == null ? Set.of() : values;
        PlayerProgressValidation.bounded(source, name + "s");
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        source.forEach(value -> copy.add(
                PlayerProgressValidation.canonicalId(value, name)));
        return Set.copyOf(copy);
    }
}
