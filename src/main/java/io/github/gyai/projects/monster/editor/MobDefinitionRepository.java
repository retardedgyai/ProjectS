package io.github.gyai.projects.monster.editor;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class MobDefinitionRepository {
    static final int MAX_DEFINITIONS = 1_024;
    static final long MAX_DEFINITION_FILE_BYTES = 1_048_576L;
    private final Path directory;
    private final MobDefinitionValidator validator;
    private final Consumer<String> warningSink;
    private volatile Map<String, MobDefinition> definitions = Map.of();

    public MobDefinitionRepository(
            Path directory,
            MobDefinitionValidator validator,
            Consumer<String> warningSink
    ) {
        this.directory = directory;
        this.validator = validator;
        this.warningSink = warningSink;
    }

    public synchronized LoadResult reload() {
        LinkedHashMap<String, MobDefinition> loaded = new LinkedHashMap<>();
        int rejected = 0;
        try {
            Files.createDirectories(directory);
            List<Path> paths = DefinitionReloadGuard.yamlFiles(
                    directory, MAX_DEFINITIONS, MAX_DEFINITION_FILE_BYTES);
            for (Path path : paths) {
                    try {
                        MobDefinition definition = read(path);
                        String fileId = path.getFileName().toString();
                        fileId = fileId.substring(0, fileId.length() - 4);
                        if (!fileId.equals(definition.id())) {
                            throw new IOException("ファイル名と内部IDが一致しません");
                        }
                        ValidationResult validation = validator.validate(definition);
                        if (!validation.valid()) {
                            throw new IOException(validation.message());
                        }
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
                return new LoadResult(false, definitions.size(), rejected,
                        rejected + "件の不正なMob定義を拒否し、既存状態を維持しました");
            }
            definitions = Map.copyOf(loaded);
            return new LoadResult(true, loaded.size(), rejected, "再読み込みしました");
        } catch (IOException exception) {
            warningSink.accept("Mob定義の読み込みを拒否しました: "
                    + exception.getMessage());
            return new LoadResult(false, definitions.size(), rejected,
                    "Mob定義の読み込みに失敗しました: " + exception.getMessage());
        }
    }

    public synchronized SaveResult save(
            MobDefinition draft,
            long expectedRevision
    ) {
        ValidationResult validation = validator.validate(draft);
        if (!validation.valid()) return SaveResult.failure(validation.message());
        MobDefinition current = definitions.get(draft.id());
        if (current == null && definitions.size() >= MAX_DEFINITIONS) {
            return SaveResult.failure("Mob定義は最大1024件です");
        }
        long currentRevision = current == null ? 0 : current.revision();
        if (currentRevision != expectedRevision) {
            return SaveResult.conflict(
                    "別の編集によってモブ定義が更新されています。最新状態を再読み込みしてください");
        }
        MobDefinition saved = draft.withRevision(currentRevision + 1);
        Path target = directory.resolve(saved.id() + ".yml");
        if (current == null && Files.exists(target)) {
            return SaveResult.failure(
                    "同じIDの拒否済みまたは未読込ファイルが存在します");
        }
        try {
            writeAtomic(target, MobDefinitionYaml.write(saved).saveToString());
            LinkedHashMap<String, MobDefinition> updated =
                    new LinkedHashMap<>(definitions);
            updated.put(saved.id(), saved);
            definitions = Map.copyOf(updated);
            return SaveResult.success(saved);
        } catch (IOException exception) {
            return SaveResult.failure("保存に失敗しました: " + exception.getMessage());
        }
    }

    public MobDefinition get(String id) {
        return definitions.get(id);
    }

    public List<MobDefinition> all() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(MobDefinition::id)).toList();
    }

    public synchronized void replaceState(List<MobDefinition> restored) {
        LinkedHashMap<String, MobDefinition> updated = new LinkedHashMap<>();
        for (MobDefinition definition : restored) {
            updated.put(definition.id(), definition);
        }
        definitions = Map.copyOf(updated);
    }

    public List<MobDefinition> search(
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
                .sorted(Comparator.comparing(MobDefinition::id))
                .skip((long) Math.max(0, page) * pageSize)
                .limit(pageSize).toList();
    }

    private MobDefinition read(Path path) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(path, StandardCharsets.UTF_8));
        } catch (InvalidConfigurationException exception) {
            throw new IOException("YAMLが不正です", exception);
        }
        for (String key : yaml.getKeys(true)) {
            if (!knownKey(key)) {
                warningSink.accept(path.getFileName()
                        + "の不明なキーを無視します: " + key);
            }
        }
        return MobDefinitionYaml.read(yaml);
    }

    private static boolean knownKey(String key) {
        if (SetHolder.EXACT.contains(key)) return true;
        if (key.startsWith("appearance.variants.")) {
            return key.substring("appearance.variants.".length()).indexOf('.') < 0;
        }
        if (!key.startsWith("appearance.equipment.")) return false;
        String[] parts = key.split("\\.");
        if (parts.length == 3) return SetHolder.SLOTS.contains(parts[2]);
        return parts.length == 4 && SetHolder.SLOTS.contains(parts[2])
                && SetHolder.EQUIPMENT_KEYS.contains(parts[3]);
    }

    private static final class SetHolder {
        private static final java.util.Set<String> SLOTS = java.util.Set.of(
                "head", "chest", "legs", "feet", "main-hand", "off-hand");
        private static final java.util.Set<String> EQUIPMENT_KEYS = java.util.Set.of(
                "source-type", "reference-id", "material", "color", "glint",
                "visible", "visual-only");
        private static final java.util.Set<String> EXACT = java.util.Set.of(
                "schema-version", "revision", "id", "display-name", "entity-type",
                "category", "enabled", "level", "tags", "abilities", "stats",
                "nameplate", "nameplate.mode",
                "stats.max-health", "stats.physical-attack", "stats.magical-attack",
                "stats.physical-defense", "stats.magical-defense", "stats.move-speed",
                "stats.attack-speed", "stats.critical-chance", "stats.critical-damage",
                "stats.damage-reduction", "basic-attack", "basic-attack.damage-type",
                "basic-attack.fixed-damage", "basic-attack.coefficient",
                "basic-attack.interval-seconds", "basic-attack.range",
                "basic-attack.knockback", "basic-attack.critical-allowed", "ai",
                "ai.preset", "ai.target-priority", "ai.aggro-range", "ai.chase-range",
                "ai.leash-range", "ai.attack-range", "ai.target-refresh-seconds",
                "ai.return-home", "ai.reset-health-on-return", "ai.avoid-falls",
                "ai.avoid-water", "appearance", "appearance.scale", "appearance.age",
                "appearance.glowing", "appearance.glowing.enabled",
                "appearance.glowing.color", "appearance.variants",
                "appearance.equipment");
    }

    static void writeAtomic(Path target, String contents) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(
                target.getParent(), target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    public record LoadResult(boolean success, int loaded, int rejected, String message) { }

    public record SaveResult(
            boolean success,
            boolean revisionConflict,
            String message,
            MobDefinition definition
    ) {
        static SaveResult success(MobDefinition definition) {
            return new SaveResult(true, false, "保存しました", definition);
        }

        static SaveResult conflict(String message) {
            return new SaveResult(false, true, message, null);
        }

        static SaveResult failure(String message) {
            return new SaveResult(false, false, message, null);
        }
    }
}
