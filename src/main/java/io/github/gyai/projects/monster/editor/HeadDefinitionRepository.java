package io.github.gyai.projects.monster.editor;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class HeadDefinitionRepository {
    static final int MAX_DEFINITIONS = 1_024;
    static final long MAX_DEFINITION_FILE_BYTES = 1_048_576L;
    private final Path directory;
    private final HeadDefinitionValidator validator;
    private final Consumer<String> warningSink;
    private volatile Map<String, HeadDefinition> definitions = Map.of();

    public HeadDefinitionRepository(
            Path directory,
            HeadDefinitionValidator validator,
            Consumer<String> warningSink
    ) {
        this.directory = directory;
        this.validator = validator;
        this.warningSink = warningSink;
    }

    public synchronized MobDefinitionRepository.LoadResult reload() {
        LinkedHashMap<String, HeadDefinition> loaded = new LinkedHashMap<>();
        int rejected = 0;
        try {
            Files.createDirectories(directory);
            List<Path> paths = DefinitionReloadGuard.yamlFiles(
                    directory, MAX_DEFINITIONS, MAX_DEFINITION_FILE_BYTES);
            for (Path path : paths) {
                    try {
                        HeadDefinition definition = read(path);
                        String fileId = path.getFileName().toString();
                        fileId = fileId.substring(0, fileId.length() - 4);
                        if (!fileId.equals(definition.id())) {
                            throw new IOException("ファイル名とHead IDが一致しません");
                        }
                        ValidationResult validation = validator.validate(definition);
                        if (!validation.valid()) throw new IOException(validation.message());
                        if (loaded.putIfAbsent(definition.id(), definition) != null) {
                            throw new IOException("重複IDです: " + definition.id());
                        }
                    } catch (IOException | RuntimeException exception) {
                        rejected++;
                        warningSink.accept(path.getFileName() + "を拒否しました: "
                                + exception.getMessage());
                    }
            }
            if (rejected > 0) {
                return new MobDefinitionRepository.LoadResult(
                        false, definitions.size(), rejected,
                        rejected + "件の不正なHead定義を拒否し、既存状態を維持しました");
            }
            definitions = Map.copyOf(loaded);
            return new MobDefinitionRepository.LoadResult(
                    true, loaded.size(), rejected, "Head定義を再読み込みしました");
        } catch (IOException exception) {
            warningSink.accept("Head定義の読み込みを拒否しました: "
                    + exception.getMessage());
            return new MobDefinitionRepository.LoadResult(
                    false, definitions.size(), rejected,
                    "Head定義の読み込みに失敗しました: " + exception.getMessage());
        }
    }

    public synchronized SaveResult save(HeadDefinition draft, long expectedRevision) {
        ValidationResult validation = validator.validate(draft);
        if (!validation.valid()) return SaveResult.failure(validation.message());
        HeadDefinition current = definitions.get(draft.id());
        if (current == null && definitions.size() >= MAX_DEFINITIONS) {
            return SaveResult.failure("Head定義は最大1024件です");
        }
        long currentRevision = current == null ? 0 : current.revision();
        if (currentRevision != expectedRevision) {
            return SaveResult.conflict("Head定義のrevisionが競合しました");
        }
        HeadDefinition saved = draft.withRevision(currentRevision + 1);
        try {
            MobDefinitionRepository.writeAtomic(
                    directory.resolve(saved.id() + ".yml"),
                    write(saved).saveToString());
            LinkedHashMap<String, HeadDefinition> updated =
                    new LinkedHashMap<>(definitions);
            updated.put(saved.id(), saved);
            definitions = Map.copyOf(updated);
            return SaveResult.success(saved);
        } catch (IOException exception) {
            return SaveResult.failure("Head保存に失敗しました: " + exception.getMessage());
        }
    }

    public synchronized SaveResult create(HeadDefinition draft) {
        ValidationResult validation = validator.validate(draft);
        if (!validation.valid()) return SaveResult.failure(validation.message());
        if (draft.revision() != 0 || definitions.containsKey(draft.id())) {
            return SaveResult.failure("同じHead IDが既に存在します");
        }
        if (definitions.size() >= MAX_DEFINITIONS) {
            return SaveResult.failure("Head定義は最大1024件です");
        }
        HeadDefinition saved = draft.withRevision(1);
        Path target = directory.resolve(saved.id() + ".yml");
        if (Files.exists(target)) {
            return SaveResult.failure(
                    "同じHead IDの拒否済みまたは未読込ファイルが存在します");
        }
        try {
            MobDefinitionRepository.writeAtomic(
                    target, write(saved).saveToString());
            LinkedHashMap<String, HeadDefinition> updated =
                    new LinkedHashMap<>(definitions);
            updated.put(saved.id(), saved);
            definitions = Map.copyOf(updated);
            return SaveResult.success(saved);
        } catch (IOException exception) {
            return SaveResult.failure("Head保存に失敗しました: " + exception.getMessage());
        }
    }

    public HeadDefinition get(String id) {
        return definitions.get(id);
    }

    public List<HeadDefinition> search(
            String query,
            int page,
            int pageSize
    ) {
        String needle = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        return definitions.values().stream()
                .filter(value -> needle.isBlank()
                        || value.id().toLowerCase(java.util.Locale.ROOT).contains(needle)
                        || value.displayName().toLowerCase(java.util.Locale.ROOT).contains(needle)
                        || value.tags().stream().anyMatch(tag ->
                        tag.toLowerCase(java.util.Locale.ROOT).contains(needle)))
                .sorted(Comparator.comparing(HeadDefinition::favorite).reversed()
                        .thenComparing(HeadDefinition::id))
                .skip((long) Math.max(0, page) * pageSize)
                .limit(pageSize).toList();
    }

    public List<HeadDefinition> all() {
        return List.copyOf(definitions.values());
    }

    public synchronized void replaceState(List<HeadDefinition> restored) {
        LinkedHashMap<String, HeadDefinition> updated = new LinkedHashMap<>();
        for (HeadDefinition definition : restored) {
            updated.put(definition.id(), definition);
        }
        definitions = Map.copyOf(updated);
    }

    private HeadDefinition read(Path path) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(path, StandardCharsets.UTF_8));
        } catch (InvalidConfigurationException exception) {
            throw new IOException("YAMLが不正です", exception);
        }
        java.util.Set<String> known = java.util.Set.of(
                "schema-version", "revision", "id", "display-name", "source-type",
                "player-name", "texture-value", "projects-item-id", "tags",
                "favorite", "source-note");
        for (String key : yaml.getKeys(true)) {
            if (!known.contains(key)) {
                warningSink.accept(path.getFileName()
                        + "の不明なキーを無視します: " + key);
            }
        }
        return new HeadDefinition(
                yaml.getInt("schema-version"), yaml.getLong("revision"),
                yaml.getString("id", ""), yaml.getString("display-name", ""),
                enumValue(yaml.getString("source-type")),
                yaml.getString("player-name", ""),
                yaml.getString("texture-value", ""),
                yaml.getString("projects-item-id", ""),
                yaml.getStringList("tags"), yaml.getBoolean("favorite"),
                yaml.getString("source-note", ""));
    }

    private static YamlConfiguration write(HeadDefinition definition) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", definition.schemaVersion());
        yaml.set("revision", definition.revision());
        yaml.set("id", definition.id());
        yaml.set("display-name", definition.displayName());
        yaml.set("source-type", definition.sourceType().name());
        if (!definition.playerName().isBlank()) {
            yaml.set("player-name", definition.playerName());
        }
        if (!definition.textureValue().isBlank()) {
            yaml.set("texture-value", definition.textureValue());
        }
        if (!definition.projectsItemId().isBlank()) {
            yaml.set("projects-item-id", definition.projectsItemId());
        }
        yaml.set("tags", definition.tags());
        yaml.set("favorite", definition.favorite());
        yaml.set("source-note", definition.sourceNote());
        return yaml;
    }

    private static HeadDefinition.SourceType enumValue(String value) {
        try {
            return HeadDefinition.SourceType.valueOf(
                    value == null ? "" : value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public record SaveResult(
            boolean success,
            boolean revisionConflict,
            String message,
            HeadDefinition definition
    ) {
        static SaveResult success(HeadDefinition definition) {
            return new SaveResult(true, false, "Headを保存しました", definition);
        }

        static SaveResult conflict(String message) {
            return new SaveResult(false, true, message, null);
        }

        static SaveResult failure(String message) {
            return new SaveResult(false, false, message, null);
        }
    }
}
