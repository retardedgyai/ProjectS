package io.github.gyai.projects.player.progress;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable, Bukkit-free aggregate captured at the persistence boundary. */
public record PlayerProgressSnapshot(
        UUID playerId,
        int level,
        long experience,
        long grantedPassivePoints,
        long spentPassivePoints,
        Set<String> allocatedPassiveNodeIds,
        String selectedClassId,
        Map<String, Long> professionMastery,
        Map<String, QuestProgressState> questStates,
        Set<String> unlockIds,
        Map<String, Long> currencies,
        Map<String, Long> persistentResources,
        Map<String, String> settings,
        long revision,
        Instant lastSavedAt
) {
    public PlayerProgressSnapshot {
        if (playerId == null) throw new IllegalArgumentException("playerId is required");
        level = PlayerProgressValidation.level(level);
        experience = PlayerProgressValidation.nonNegative(experience, "experience");
        grantedPassivePoints = PlayerProgressValidation.nonNegative(
                grantedPassivePoints, "granted passive points");
        spentPassivePoints = PlayerProgressValidation.nonNegative(
                spentPassivePoints, "spent passive points");
        if (spentPassivePoints > grantedPassivePoints) {
            throw new IllegalArgumentException("spent passive points exceed granted points");
        }
        allocatedPassiveNodeIds = immutableIds(
                allocatedPassiveNodeIds, "passive node ID");
        if (allocatedPassiveNodeIds.size() > spentPassivePoints) {
            throw new IllegalArgumentException(
                    "allocated node count exceeds spent passive points");
        }
        if (selectedClassId != null) {
            selectedClassId = PlayerProgressValidation.canonicalId(
                    selectedClassId, "selected class ID");
        }
        professionMastery = immutableQuantities(
                professionMastery, "profession ID", "mastery progress");
        questStates = immutableQuestStates(questStates);
        unlockIds = immutableIds(unlockIds, "unlock ID");
        currencies = immutableQuantities(
                currencies, "currency ID", "currency value");
        persistentResources = immutableQuantities(
                persistentResources, "resource ID", "resource value");
        settings = immutableSettings(settings);
        revision = PlayerProgressValidation.nonNegative(revision, "revision");
        if (lastSavedAt == null) {
            throw new IllegalArgumentException("lastSavedAt is required");
        }
    }

    public long availablePassivePoints() {
        return grantedPassivePoints - spentPassivePoints;
    }

    private static Set<String> immutableIds(Set<String> values, String name) {
        Set<String> source = values == null ? Set.of() : values;
        PlayerProgressValidation.bounded(source, name + "s");
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        source.forEach(id -> copy.add(
                PlayerProgressValidation.canonicalId(id, name)));
        return Set.copyOf(copy);
    }

    private static Map<String, Long> immutableQuantities(
            Map<String, Long> values,
            String idName,
            String valueName
    ) {
        Map<String, Long> source = values == null ? Map.of() : values;
        PlayerProgressValidation.bounded(source, idName + " map");
        LinkedHashMap<String, Long> copy = new LinkedHashMap<>();
        source.forEach((id, value) -> copy.put(
                PlayerProgressValidation.canonicalId(id, idName),
                PlayerProgressValidation.nonNegative(
                        value == null ? -1L : value, valueName)));
        return Map.copyOf(copy);
    }

    private static Map<String, QuestProgressState> immutableQuestStates(
            Map<String, QuestProgressState> values
    ) {
        Map<String, QuestProgressState> source = values == null ? Map.of() : values;
        PlayerProgressValidation.bounded(source, "quest states");
        LinkedHashMap<String, QuestProgressState> copy = new LinkedHashMap<>();
        source.forEach((id, value) -> copy.put(
                PlayerProgressValidation.canonicalId(id, "quest ID"),
                java.util.Objects.requireNonNull(value, "quest state")));
        return Map.copyOf(copy);
    }

    private static Map<String, String> immutableSettings(Map<String, String> values) {
        Map<String, String> source = values == null ? Map.of() : values;
        PlayerProgressValidation.bounded(source, "settings");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        source.forEach((id, value) -> copy.put(
                PlayerProgressValidation.canonicalId(id, "setting ID"),
                PlayerProgressValidation.settingValue(value)));
        return Map.copyOf(copy);
    }
}
