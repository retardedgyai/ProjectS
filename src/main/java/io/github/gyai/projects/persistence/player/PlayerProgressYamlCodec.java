package io.github.gyai.projects.persistence.player;

import io.github.gyai.projects.player.progress.PlayerProgressBuilder;
import io.github.gyai.projects.player.progress.PlayerProgressRecordV1;
import io.github.gyai.projects.player.progress.PlayerProgressSnapshot;
import io.github.gyai.projects.player.progress.QuestProgressState;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict codec for the bounded YAML subset owned by player-data v1. */
final class PlayerProgressYamlCodec {
    private final Set<String> settingWhitelist;

    PlayerProgressYamlCodec(Set<String> settingWhitelist) {
        this.settingWhitelist = Set.copyOf(
                settingWhitelist == null ? Set.of() : settingWhitelist);
    }

    Header inspectHeader(String source) throws InvalidConfigurationException {
        YamlConfiguration yaml = parse(source);
        return new Header(
                requiredString(yaml, "schema-id"),
                exactInt(yaml.get("schema-version"), "schema-version"));
    }

    PlayerProgressRecordV1 decode(String source)
            throws InvalidConfigurationException {
        YamlConfiguration yaml = parse(source);
        String schemaId = requiredString(yaml, "schema-id");
        int schemaVersion = exactInt(
                yaml.get("schema-version"), "schema-version");
        UUID playerId;
        Instant savedAt;
        try {
            playerId = UUID.fromString(requiredString(yaml, "player-uuid"));
            savedAt = Instant.parse(requiredString(yaml, "last-saved-at"));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalid("invalid UUID or timestamp", exception);
        }
        try {
            PlayerProgressSnapshot snapshot = new PlayerProgressBuilder(
                    playerId, settingWhitelist)
                    .level(exactInt(yaml.get("level"), "level"))
                    .experience(exactLong(yaml.get("experience"), "experience"))
                    .passivePoints(
                            exactLong(yaml.get("passive-points.granted"),
                                    "passive-points.granted"),
                            exactLong(yaml.get("passive-points.spent"),
                                    "passive-points.spent"))
                    .allocatedPassiveNodeIds(ids(
                            requiredList(yaml, "allocated-passive-nodes"),
                            "allocated-passive-nodes"))
                    .selectedClassId(optionalString(yaml.get("selected-class-id")))
                    .professionMastery(quantityEntries(
                            requiredList(yaml, "profession-mastery"),
                            "profession-mastery"))
                    .questStates(questEntries(requiredList(yaml, "quest-states")))
                    .unlockIds(ids(requiredList(yaml, "unlocks"), "unlocks"))
                    .currencies(quantityEntries(
                            requiredList(yaml, "currencies"), "currencies"))
                    .persistentResources(quantityEntries(
                            requiredList(yaml, "persistent-resources"),
                            "persistent-resources"))
                    .settings(settingEntries(requiredList(yaml, "settings")))
                    .revision(exactLong(yaml.get("revision"), "revision"))
                    .lastSavedAt(savedAt)
                    .build();
            return new PlayerProgressRecordV1(schemaId, schemaVersion, snapshot);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid("invalid player-data v1 value", exception);
        }
    }

    String encode(PlayerProgressRecordV1 record) {
        PlayerProgressSnapshot snapshot = record.snapshot();
        if (!settingWhitelist.containsAll(snapshot.settings().keySet())) {
            throw new IllegalArgumentException(
                    "record contains non-whitelisted setting IDs");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-id", record.schemaId());
        yaml.set("schema-version", record.schemaVersion());
        yaml.set("player-uuid", snapshot.playerId().toString());
        yaml.set("revision", snapshot.revision());
        yaml.set("last-saved-at", snapshot.lastSavedAt().toString());
        yaml.set("level", snapshot.level());
        yaml.set("experience", snapshot.experience());
        yaml.set("passive-points.granted", snapshot.grantedPassivePoints());
        yaml.set("passive-points.spent", snapshot.spentPassivePoints());
        yaml.set("allocated-passive-nodes", sorted(snapshot.allocatedPassiveNodeIds()));
        yaml.set("selected-class-id", snapshot.selectedClassId());
        yaml.set("profession-mastery", quantityList(snapshot.professionMastery()));
        yaml.set("quest-states", questList(snapshot.questStates()));
        yaml.set("unlocks", sorted(snapshot.unlockIds()));
        yaml.set("currencies", quantityList(snapshot.currencies()));
        yaml.set("persistent-resources", quantityList(snapshot.persistentResources()));
        yaml.set("settings", settingList(snapshot.settings()));
        return yaml.saveToString();
    }

    private static YamlConfiguration parse(String source)
            throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(source == null ? "" : source);
        return yaml;
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }

    private static List<Map<String, Object>> quantityList(Map<String, Long> values) {
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.add(Map.of(
                        "id", entry.getKey(), "value", entry.getValue())));
        return result;
    }

    private static List<Map<String, Object>> settingList(Map<String, String> values) {
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.add(Map.of(
                        "id", entry.getKey(), "value", entry.getValue())));
        return result;
    }

    private static List<Map<String, Object>> questList(
            Map<String, QuestProgressState> values
    ) {
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    LinkedHashMap<String, Object> value = new LinkedHashMap<>();
                    value.put("id", entry.getKey());
                    value.put("state", entry.getValue().stateId());
                    value.put("counters", quantityList(entry.getValue().counters()));
                    value.put("claims", sorted(entry.getValue().claimMarkerIds()));
                    result.add(value);
                });
        return result;
    }

    private static Set<String> ids(List<?> values, String field) {
        List<?> source = values == null ? List.of() : values;
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object value : source) {
            if (!(value instanceof String id) || !result.add(id)) {
                throw new IllegalArgumentException(field + " contains invalid or duplicate ID");
            }
        }
        return result;
    }

    private static Map<String, Long> quantityEntries(List<?> values, String field) {
        List<?> source = values == null ? List.of() : values;
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        for (Object raw : source) {
            Map<?, ?> entry = map(raw, field);
            String id = string(entry.get("id"), field + ".id");
            long value = exactLong(entry.get("value"), field + ".value");
            if (result.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException(field + " contains duplicate ID");
            }
        }
        return result;
    }

    private static Map<String, String> settingEntries(List<?> values) {
        List<?> source = values == null ? List.of() : values;
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Object raw : source) {
            Map<?, ?> entry = map(raw, "settings");
            String id = string(entry.get("id"), "settings.id");
            String value = string(entry.get("value"), "settings.value");
            if (result.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("settings contain duplicate ID");
            }
        }
        return result;
    }

    private static Map<String, QuestProgressState> questEntries(List<?> values) {
        List<?> source = values == null ? List.of() : values;
        LinkedHashMap<String, QuestProgressState> result = new LinkedHashMap<>();
        for (Object raw : source) {
            Map<?, ?> entry = map(raw, "quest-states");
            String id = string(entry.get("id"), "quest-states.id");
            QuestProgressState state = new QuestProgressState(
                    string(entry.get("state"), "quest-states.state"),
                    quantityEntries(list(entry.get("counters")), "quest counters"),
                    ids(list(entry.get("claims")), "quest claims"));
            if (result.putIfAbsent(id, state) != null) {
                throw new IllegalArgumentException("quest-states contain duplicate ID");
            }
        }
        return result;
    }

    private static Map<?, ?> map(Object value, String field) {
        if (!(value instanceof Map<?, ?> result)) {
            throw new IllegalArgumentException(field + " entry must be a map");
        }
        return result;
    }

    private static List<?> list(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> result)) {
            throw new IllegalArgumentException("entry must be a list");
        }
        return result;
    }

    private static List<?> requiredList(YamlConfiguration yaml, String path) {
        if (!yaml.contains(path)) {
            throw new IllegalArgumentException(path + " is required");
        }
        return list(yaml.get(path));
    }

    private static String requiredString(YamlConfiguration yaml, String path) {
        return string(yaml.get(path), path);
    }

    private static String string(Object value, String field) {
        if (!(value instanceof String result) || result.isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return result;
    }

    private static String optionalString(Object value) {
        if (value == null) return null;
        return string(value, "selected-class-id");
    }

    private static int exactInt(Object value, String field) {
        long parsed = exactLong(value, field);
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " is outside integer range");
        }
        return (int) parsed;
    }

    private static long exactLong(Object value, String field) {
        if (!(value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long)) {
            throw new IllegalArgumentException(field + " must be an integral number");
        }
        return ((Number) value).longValue();
    }

    private static InvalidConfigurationException invalid(
            String message,
            Exception cause
    ) {
        InvalidConfigurationException result =
                new InvalidConfigurationException(message);
        result.initCause(cause);
        return result;
    }

    record Header(String schemaId, int schemaVersion) {
    }
}
