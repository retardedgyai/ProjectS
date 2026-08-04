package io.github.gyai.projects.combat.damage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Dependency-free, bounded YAML export of an immutable session snapshot. */
public final class DamageShadowValidationExporter {
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .withZone(ZoneId.systemDefault());
    private final String fileStem;

    public DamageShadowValidationExporter() {
        this("starter-sword-shadow");
    }

    public DamageShadowValidationExporter(String fileStem) {
        if (fileStem == null
                || fileStem.length() > 64
                || !fileStem.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException(
                    "fileStem must be a lowercase safe slug");
        }
        this.fileStem = fileStem;
    }

    public Path export(
            Path directory,
            DamageShadowValidationSnapshot snapshot,
            Instant exportedAt
    ) throws IOException {
        Files.createDirectories(directory);
        Path target = uniqueTarget(directory, exportedAt);
        Path temporary = Files.createTempFile(
                directory, "." + fileStem + "-", ".tmp");
        try {
            Files.writeString(
                    temporary,
                    format(snapshot, exportedAt),
                    StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public String format(
            DamageShadowValidationSnapshot snapshot,
            Instant exportedAt
    ) {
        StringBuilder yaml = new StringBuilder(8_192);
        line(yaml, 0, "schema-version", "1");
        line(yaml, 0, "exported-at", quote(exportedAt.toString()));
        line(yaml, 0, "session-started-at",
                quote(snapshot.sessionStartedAt().toString()));
        line(yaml, 0, "enabled", Boolean.toString(snapshot.enabled()));
        yaml.append("metrics:\n");
        line(yaml, 2, "comparisons", snapshot.comparisonCount());
        line(yaml, 2, "matches", snapshot.matchCount());
        line(yaml, 2, "mismatches", snapshot.mismatchCount());
        line(yaml, 2, "legacy-failures", snapshot.legacyFailureCount());
        line(yaml, 2, "shadow-failures", snapshot.shadowFailureCount());
        line(yaml, 2, "maximum-absolute-error",
                snapshot.maximumAbsoluteError());
        line(yaml, 2, "maximum-relative-error",
                snapshot.maximumRelativeError());
        line(yaml, 2, "average-absolute-error",
                snapshot.averageAbsoluteError());
        line(yaml, 2, "critical", snapshot.criticalCount());
        line(yaml, 2, "non-critical", snapshot.nonCriticalCount());
        line(yaml, 2, "shield-present", snapshot.shieldPresentCount());
        line(yaml, 2, "shield-absent", snapshot.shieldAbsentCount());
        yaml.append("counts:\n");
        enumCounts(yaml, "damage-type", snapshot.damageTypeCounts());
        enumCounts(yaml, "damage-kind", snapshot.damageKindCounts());
        enumCounts(yaml, "damage-mode", snapshot.damageModeCounts());
        enumCounts(yaml, "target-type", snapshot.targetTypeCounts());
        yaml.append("  enhancement-level:\n");
        for (Map.Entry<Integer, Long> entry
                : snapshot.enhancementLevelCounts().entrySet()) {
            line(yaml, 4, Integer.toString(entry.getKey()), entry.getValue());
        }
        line(yaml, 0, "mismatch-detail-limit",
                snapshot.mismatchDetailLimit());
        yaml.append("mismatch-details:");
        if (snapshot.mismatchDetails().isEmpty()) {
            yaml.append(" []\n");
            return yaml.toString();
        }
        yaml.append('\n');
        for (DamageShadowMismatchDetail detail
                : snapshot.mismatchDetails()) {
            DamageShadowRuntimeContext context = detail.context();
            yaml.append("  - timestamp: ")
                    .append(quote(context.timestamp().toString())).append('\n');
            line(yaml, 4, "attacker-uuid", quote(context.attackerId().toString()));
            line(yaml, 4, "target-uuid", quote(context.targetId().toString()));
            line(yaml, 4, "target-type", context.targetType().name());
            line(yaml, 4, "item-id", quote(context.itemId()));
            line(yaml, 4, "enhancement-level", context.enhancementLevel());
            line(yaml, 4, "critical-decision", detail.criticalDecision());
            DamageCalculationSnapshot calculation =
                    detail.calculationSnapshot();
            line(yaml, 4, "damage-type", calculation.damageType().name());
            line(yaml, 4, "damage-kind", calculation.damageKind().name());
            line(yaml, 4, "damage-mode", calculation.mode().name());
            line(yaml, 4, "offense-snapshot",
                    quote(calculation.offenseSnapshot().toString()));
            line(yaml, 4, "defense-snapshot",
                    quote(calculation.defenseSnapshot().toString()));
            line(yaml, 4, "attack-metadata",
                    quote(detail.attackMetadata().toString()));
            line(yaml, 4, "legacy-breakdown",
                    quote(detail.legacyResult().toString()));
            line(yaml, 4, "shadow-breakdown",
                    quote(detail.shadowResult().toString()));
            line(yaml, 4, "context-differences",
                    quote(detail.contextDifferences().toString()));
            yaml.append("    numeric-differences:\n");
            for (Map.Entry<String, DamageShadowNumericReport.NumericDelta> entry
                    : detail.numericReport().deltas().entrySet()) {
                DamageShadowNumericReport.NumericDelta delta = entry.getValue();
                yaml.append("      ").append(entry.getKey()).append(":\n");
                line(yaml, 8, "legacy", delta.legacyValue());
                line(yaml, 8, "shadow", delta.shadowValue());
                line(yaml, 8, "absolute-error", delta.absoluteError());
                line(yaml, 8, "relative-error", delta.relativeError());
            }
        }
        return yaml.toString();
    }

    private Path uniqueTarget(Path directory, Instant exportedAt)
            throws IOException {
        String base = fileStem + "-"
                + FILE_TIMESTAMP.format(exportedAt);
        for (int suffix = 0; suffix <= 999; suffix++) {
            String name = base + (suffix == 0 ? "" : "-" + suffix) + ".yml";
            Path candidate = directory.resolve(name);
            if (!candidate.normalize().getParent().equals(
                    directory.normalize())) {
                throw new IOException("Export target escaped directory");
            }
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IOException("Too many damage shadow exports for one second");
    }

    private static <E extends Enum<E>> void enumCounts(
            StringBuilder yaml,
            String name,
            Map<E, Long> values
    ) {
        yaml.append("  ").append(name).append(":\n");
        for (Map.Entry<E, Long> entry : values.entrySet()) {
            line(yaml, 4, entry.getKey().name(), entry.getValue());
        }
    }

    private static void line(
            StringBuilder yaml,
            int spaces,
            String key,
            Object value
    ) {
        yaml.append(" ".repeat(spaces)).append(key).append(": ")
                .append(value).append('\n');
    }

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + "\"";
    }
}
