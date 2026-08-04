package io.github.gyai.projects.combat.damage;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public final class StarterSwordRouteCommandServiceTest {
    private StarterSwordRouteCommandServiceTest() {
    }

    public static void main(String[] args) {
        Instant startedAt = Instant.parse("2026-08-04T00:00:00Z");
        StarterSwordRouteController controller = new StarterSwordRouteController(
                true,
                new StarterSwordRouteTracker(),
                Clock.fixed(startedAt, ZoneOffset.UTC));
        StarterSwordRouteCommandService commands =
                new StarterSwordRouteCommandService(controller);

        controller.recordDecision(StarterSwordRouteDecision.NEW_AUTHORITATIVE);
        controller.recordApplication(true, true);
        controller.recordDecision(StarterSwordRouteDecision.LEGACY_DISABLED);
        controller.recordApplication(false, true);
        controller.recordDecision(StarterSwordRouteDecision.LEGACY_ROUTE_FAILURE);

        List<String> summary = commands.execute("summary").messages();
        assert summary.stream().noneMatch(line -> line.contains("%d"));
        assert summary.contains(
                "hits=3 authoritative=1 legacyFallback=2");
        assert summary.contains(
                "newFailures=1 newApplied=1 legacyApplied=1 "
                        + "applicationBoundaryCompleted=2");
        assert summary.stream().anyMatch(line -> line.startsWith(
                "authoritativeShadowMatches=0 mismatches=0"));
        String fallbackReasons = summary.stream()
                .filter(line -> line.startsWith("fallbackReasons="))
                .findFirst()
                .orElseThrow();
        assert fallbackReasons.contains("NEW_AUTHORITATIVE=1");
        assert fallbackReasons.contains("LEGACY_DISABLED=1");
        assert fallbackReasons.contains("LEGACY_ROUTE_FAILURE=1");

        List<String> status = commands.execute("status").messages();
        assert status.equals(List.of(
                "damage route: 有効",
                "hits=3 authoritative=1 legacyFallback=2",
                "criticalFallback=0 shieldFallback=0 startedAt=" + startedAt));
        assert status.stream().noneMatch(line -> line.contains("%d"));
    }
}
