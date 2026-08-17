package io.github.gyai.projects.data;

import io.github.gyai.projects.player.StatType;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class PlayerProgressionRepository {
    private final Path root;
    public PlayerProgressionRepository(Path root) { this.root = Objects.requireNonNull(root); }
    public Optional<PlayerProgressionSnapshot> load(UUID playerId) throws IOException {
        Path file = file(playerId); if (!Files.isRegularFile(file)) return Optional.empty();
        Properties p = new Properties(); try (var reader = Files.newBufferedReader(file)) { p.load(reader); }
        int schema = Integer.parseInt(required(p, "schema"));
        if (schema != PlayerProgressionSnapshot.CURRENT_SCHEMA) {
            Files.move(file, file.resolveSibling(file.getFileName()+".unknown"), StandardCopyOption.REPLACE_EXISTING); return Optional.empty();
        }
        Map<StatType, Double> stats = new EnumMap<>(StatType.class);
        for (String key : p.stringPropertyNames()) if (key.startsWith("stat.")) stats.put(StatType.valueOf(key.substring(5)), Double.parseDouble(p.getProperty(key)));
        return Optional.of(new PlayerProgressionSnapshot(schema, UUID.fromString(required(p,"uuid")), Integer.parseInt(required(p,"combatLevel")), Integer.parseInt(required(p,"fightingSpirit")), stats));
    }
    public void save(PlayerProgressionSnapshot snapshot) throws IOException {
        Files.createDirectories(root); Path temp = Files.createTempFile(root, snapshot.playerId()+".", ".tmp");
        Properties p = new Properties(); p.setProperty("schema", Integer.toString(snapshot.schemaVersion())); p.setProperty("uuid", snapshot.playerId().toString()); p.setProperty("combatLevel", Integer.toString(snapshot.combatLevel())); p.setProperty("fightingSpirit", Integer.toString(snapshot.fightingSpirit()));
        snapshot.stats().forEach((type,value)->p.setProperty("stat."+type.name(), Double.toString(value)));
        try (var writer = Files.newBufferedWriter(temp)) { p.store(writer, "ProjectS player progression"); }
        try { Files.move(temp, file(snapshot.playerId()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, file(snapshot.playerId()), StandardCopyOption.REPLACE_EXISTING); }
    }
    private Path file(UUID id) { return root.resolve(id+".properties"); }
    private static String required(Properties p,String key){String value=p.getProperty(key);if(value==null||value.isBlank())throw new IllegalArgumentException("Missing "+key);return value;}
}
