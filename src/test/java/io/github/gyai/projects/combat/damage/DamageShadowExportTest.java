package io.github.gyai.projects.combat.damage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;

public final class DamageShadowExportTest {
    private DamageShadowExportTest() {
    }

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("damage-shadow-export-");
        try {
            DamageShadowValidationTracker tracker =
                    new DamageShadowValidationTracker(2);
            tracker.reset(Instant.parse("2026-08-04T00:00:00Z"));
            for (int index = 0; index < 4; index++) {
                tracker.recordComparison(
                        DamageShadowTestFixtures.context(
                                DamageShadowTargetType.BOSS, 30),
                        DamageShadowTestFixtures.comparison(
                                index % 2 == 0, 10, index + 1));
            }
            tracker.recordLegacyFailure();
            tracker.recordShadowFailure();
            DamageShadowValidationSnapshot snapshot = tracker.snapshot(false);
            DamageShadowValidationExporter exporter =
                    new DamageShadowValidationExporter();
            Instant exportedAt = Instant.parse("2026-08-04T03:04:05Z");
            Path first = exporter.export(directory, snapshot, exportedAt);
            Path second = exporter.export(directory, snapshot, exportedAt);
            DamageShadowValidationExporter spinExporter =
                    new DamageShadowValidationExporter(
                            "spin-slash-shadow");
            Path spin = spinExporter.export(directory, snapshot, exportedAt);

            assert !first.equals(second);
            assert first.getFileName().toString().startsWith(
                    "starter-sword-shadow-");
            assert spin.getFileName().toString().startsWith(
                    "spin-slash-shadow-");
            String yaml = Files.readString(first);
            assert yaml.contains("schema-version: 1");
            assert yaml.contains("comparisons: 4");
            assert yaml.contains("legacy-failures: 1");
            assert yaml.contains("shadow-failures: 1");
            assert yaml.contains("target-type: BOSS");
            assert yaml.contains("enhancement-level: 30");
            assert yaml.contains("critical-decision:");
            assert yaml.contains("offense-snapshot:");
            assert yaml.contains("defense-snapshot:");
            assert yaml.contains("attack-metadata:");
            assert yaml.contains("legacy-breakdown:");
            assert yaml.contains("shadow-breakdown:");
            assert yaml.contains("numeric-differences:");
            assert count(yaml, "  - timestamp:") == 2;
            assert Files.readString(first, java.nio.charset.StandardCharsets.UTF_8)
                    .equals(yaml);
            for (String invalid : new String[]{
                    "../escape", "spin/slash", "spin\\slash",
                    ".", "Spin-Slash", "spin--slash"}) {
                boolean rejected = false;
                try {
                    new DamageShadowValidationExporter(invalid);
                } catch (IllegalArgumentException expected) {
                    rejected = true;
                }
                assert rejected : invalid;
            }
        } finally {
            deleteRecursively(directory);
        }
    }

    private static long count(String value, String needle) {
        long result = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            result++;
            offset += needle.length();
        }
        return result;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
