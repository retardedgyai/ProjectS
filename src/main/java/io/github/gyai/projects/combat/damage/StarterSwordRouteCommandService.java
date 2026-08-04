package io.github.gyai.projects.combat.damage;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class StarterSwordRouteCommandService {
    private static final String USAGE =
            "使用法: /projects damage-route "
                    + "<status|enable|disable|summary|reset>";
    private final StarterSwordRouteController controller;

    public StarterSwordRouteCommandService(
            StarterSwordRouteController controller
    ) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    public Response execute(String action) {
        String normalized = action == null
                ? "status" : action.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "status" -> status();
            case "enable" -> {
                controller.enable();
                yield new Response(true, List.of(
                        "starter_sword限定authoritative経路を有効化しました。",
                        "非critical・shieldなしの検証済み通常物理攻撃だけが対象です。",
                        "critical・shield・未対応条件はlegacyへ戻ります。"));
            }
            case "disable" -> {
                controller.disable();
                yield new Response(true, List.of(
                        "starter_sword限定authoritative経路を無効化しました。",
                        "以後は即時に完全legacyへ戻ります。"));
            }
            case "summary" -> summary();
            case "reset" -> {
                controller.reset();
                yield new Response(true, List.of(
                        "damage route集計を初期化しました。"));
            }
            default -> new Response(false, List.of(USAGE));
        };
    }

    private Response status() {
        StarterSwordRouteSnapshot snapshot = controller.snapshot();
        return new Response(true, List.of(
                "damage route: " + (snapshot.enabled() ? "有効" : "無効"),
                compact(snapshot),
                "criticalFallback=%d shieldFallback=%d startedAt=%s"
                        .formatted(
                                snapshot.criticalFallbackCount(),
                                snapshot.shieldFallbackCount(),
                                snapshot.sessionStartedAt())));
    }

    private Response summary() {
        StarterSwordRouteSnapshot snapshot = controller.snapshot();
        return new Response(true, List.of(
                compact(snapshot),
                "newFailures=%d newApplied=%d legacyApplied=%d "
                        + "applicationBoundaryCompleted=%d"
                        .formatted(
                                snapshot.newRouteFailureCount(),
                                snapshot.newRouteAppliedCount(),
                                snapshot.legacyAppliedCount(),
                                snapshot.applicationBoundaryCompletedCount()),
                "authoritativeShadowMatches=%d mismatches=%d"
                        .formatted(
                                snapshot.authoritativeShadowMatchCount(),
                                snapshot.authoritativeShadowMismatchCount()),
                "fallbackReasons=" + snapshot.decisionCounts()));
    }

    private static String compact(StarterSwordRouteSnapshot snapshot) {
        return "hits=%d authoritative=%d legacyFallback=%d"
                .formatted(
                        snapshot.totalHits(),
                        snapshot.newAuthoritativeCount(),
                        snapshot.legacyFallbackCount());
    }

    public record Response(boolean success, List<String> messages) {
        public Response {
            messages = List.copyOf(messages);
        }
    }
}
