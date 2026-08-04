package io.github.gyai.projects.combat.damage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Pure command decision layer; Bukkit permission and message delivery stay outside. */
public final class DamageShadowCommandService {
    private static final String STARTER_USAGE =
            "使用法: /projects damage-shadow "
                    + "<status|enable|disable|reset|summary|export>";
    private final DamageShadowValidationController controller;
    private final String subjectLabel;
    private final String usage;
    private final String messagePrefix;

    public DamageShadowCommandService(
            DamageShadowValidationController controller
    ) {
        this.controller = Objects.requireNonNull(controller, "controller");
        subjectLabel = "starter_sword";
        usage = STARTER_USAGE;
        messagePrefix = "";
    }

    public DamageShadowCommandService(
            DamageShadowValidationController controller,
            String subjectLabel,
            String subjectArgument
    ) {
        this.controller = Objects.requireNonNull(controller, "controller");
        if (subjectLabel == null || subjectLabel.isBlank()
                || subjectArgument == null || subjectArgument.isBlank()) {
            throw new IllegalArgumentException(
                    "subject label and argument must not be blank");
        }
        this.subjectLabel = subjectLabel;
        usage = "使用法: /projects damage-shadow " + subjectArgument
                + " <status|enable|disable|reset|summary|export>";
        messagePrefix = subjectLabel + " ";
    }

    public Response execute(String action) {
        String normalized = action == null
                ? "status" : action.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "status" -> status();
            case "enable" -> {
                controller.enable();
                yield new Response(true, List.of(
                        subjectLabel + " shadow検証を有効化し、新しいセッションを開始しました。",
                        "実ダメージはlegacy経路のままです。"));
            }
            case "disable" -> {
                DamageShadowValidationSnapshot snapshot = controller.disable();
                yield new Response(true, List.of(
                        subjectLabel + " shadow検証を無効化しました。",
                        compactSummary(snapshot)));
            }
            case "reset" -> {
                controller.reset();
                yield new Response(true, List.of(
                        messagePrefix
                                + "damage shadow検証の集計を初期化しました。"));
            }
            case "summary" -> summary();
            case "export" -> export();
            default -> new Response(false, List.of(usage));
        };
    }

    private Response status() {
        DamageShadowValidationSnapshot snapshot = controller.snapshot();
        return new Response(true, List.of(
                messagePrefix + "damage shadow: "
                        + (snapshot.enabled() ? "有効" : "無効"),
                compactSummary(snapshot),
                "maxAbs=%s maxRel=%s startedAt=%s".formatted(
                        snapshot.maximumAbsoluteError(),
                        snapshot.maximumRelativeError(),
                        snapshot.sessionStartedAt())));
    }

    private Response summary() {
        DamageShadowValidationSnapshot snapshot = controller.snapshot();
        return new Response(true, List.of(
                compactSummary(snapshot),
                "critical=%d nonCritical=%d shield=%d noShield=%d "
                        .formatted(
                                snapshot.criticalCount(),
                                snapshot.nonCriticalCount(),
                                snapshot.shieldPresentCount(),
                                snapshot.shieldAbsentCount())
                        + "avgAbs=" + snapshot.averageAbsoluteError(),
                "詳細はサーバーログまたはexportファイルで確認してください。"));
    }

    private Response export() {
        try {
            Path exported = controller.export();
            return new Response(true, List.of(
                    messagePrefix + "damage shadow検証結果を保存しました: "
                            + exported.toAbsolutePath()));
        } catch (IOException exception) {
            return new Response(false, List.of(
                    messagePrefix + "damage shadow検証結果の保存に失敗しました: "
                            + exception.getClass().getSimpleName()));
        }
    }

    private static String compactSummary(
            DamageShadowValidationSnapshot snapshot
    ) {
        return "comparisons=%d matches=%d mismatches=%d legacyFailures=%d shadowFailures=%d"
                .formatted(
                        snapshot.comparisonCount(),
                        snapshot.matchCount(),
                        snapshot.mismatchCount(),
                        snapshot.legacyFailureCount(),
                        snapshot.shadowFailureCount());
    }

    public record Response(boolean success, List<String> messages) {
        public Response {
            messages = List.copyOf(messages);
        }
    }
}
