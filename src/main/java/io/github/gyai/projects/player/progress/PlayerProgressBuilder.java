package io.github.gyai.projects.player.progress;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Command-style builder; balance curves and rewards intentionally stay outside. */
public final class PlayerProgressBuilder {
    private final UUID playerId;
    private final Set<String> allowedSettingIds;
    private int level = 1;
    private long experience;
    private long grantedPassivePoints;
    private long spentPassivePoints;
    private Set<String> allocatedPassiveNodeIds = Set.of();
    private String selectedClassId;
    private Map<String, Long> professionMastery = Map.of();
    private Map<String, QuestProgressState> questStates = Map.of();
    private Set<String> unlockIds = Set.of();
    private Map<String, Long> currencies = Map.of();
    private Map<String, Long> persistentResources = Map.of();
    private Map<String, String> settings = Map.of();
    private long revision;
    private Instant lastSavedAt = Instant.EPOCH;

    public PlayerProgressBuilder(UUID playerId) {
        this(playerId, Set.of());
    }

    public PlayerProgressBuilder(UUID playerId, Set<String> allowedSettingIds) {
        if (playerId == null) throw new IllegalArgumentException("playerId is required");
        this.playerId = playerId;
        LinkedHashSet<String> validated = new LinkedHashSet<>();
        (allowedSettingIds == null ? Set.<String>of() : allowedSettingIds)
                .forEach(id -> validated.add(
                        PlayerProgressValidation.canonicalId(id, "setting ID")));
        this.allowedSettingIds = Set.copyOf(validated);
    }

    public static PlayerProgressBuilder from(
            PlayerProgressSnapshot snapshot,
            Set<String> allowedSettingIds
    ) {
        return new PlayerProgressBuilder(snapshot.playerId(), allowedSettingIds)
                .level(snapshot.level())
                .experience(snapshot.experience())
                .passivePoints(snapshot.grantedPassivePoints(), snapshot.spentPassivePoints())
                .allocatedPassiveNodeIds(snapshot.allocatedPassiveNodeIds())
                .selectedClassId(snapshot.selectedClassId())
                .professionMastery(snapshot.professionMastery())
                .questStates(snapshot.questStates())
                .unlockIds(snapshot.unlockIds())
                .currencies(snapshot.currencies())
                .persistentResources(snapshot.persistentResources())
                .settings(snapshot.settings())
                .revision(snapshot.revision())
                .lastSavedAt(snapshot.lastSavedAt());
    }

    public PlayerProgressBuilder level(int value) { level = value; return this; }
    public PlayerProgressBuilder experience(long value) { experience = value; return this; }
    public PlayerProgressBuilder passivePoints(long granted, long spent) {
        grantedPassivePoints = granted;
        spentPassivePoints = spent;
        return this;
    }
    public PlayerProgressBuilder allocatedPassiveNodeIds(Set<String> values) {
        allocatedPassiveNodeIds = values; return this;
    }
    public PlayerProgressBuilder selectedClassId(String value) {
        selectedClassId = value; return this;
    }
    public PlayerProgressBuilder professionMastery(Map<String, Long> values) {
        professionMastery = values; return this;
    }
    public PlayerProgressBuilder questStates(Map<String, QuestProgressState> values) {
        questStates = values; return this;
    }
    public PlayerProgressBuilder unlockIds(Set<String> values) {
        unlockIds = values; return this;
    }
    public PlayerProgressBuilder currencies(Map<String, Long> values) {
        currencies = values; return this;
    }
    public PlayerProgressBuilder persistentResources(Map<String, Long> values) {
        persistentResources = values; return this;
    }
    public PlayerProgressBuilder settings(Map<String, String> values) {
        Map<String, String> source = values == null ? Map.of() : values;
        if (!allowedSettingIds.containsAll(source.keySet())) {
            throw new IllegalArgumentException("settings contain a non-whitelisted ID");
        }
        settings = new LinkedHashMap<>(source);
        return this;
    }
    public PlayerProgressBuilder revision(long value) { revision = value; return this; }
    public PlayerProgressBuilder lastSavedAt(Instant value) {
        lastSavedAt = value; return this;
    }

    public PlayerProgressSnapshot build() {
        return new PlayerProgressSnapshot(
                playerId, level, experience,
                grantedPassivePoints, spentPassivePoints,
                allocatedPassiveNodeIds, selectedClassId,
                professionMastery, questStates, unlockIds,
                currencies, persistentResources, settings,
                revision, lastSavedAt);
    }
}
