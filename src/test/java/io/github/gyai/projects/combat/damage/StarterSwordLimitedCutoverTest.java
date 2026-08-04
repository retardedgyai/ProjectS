package io.github.gyai.projects.combat.damage;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class StarterSwordLimitedCutoverTest {
    private StarterSwordLimitedCutoverTest() {
    }

    public static void main(String[] args) {
        authoritativeDisabledIsCompletelyLegacy();
        validatedConditionsUseAuthoritativeOnly();
        unvalidatedConditionsUseLegacyOnly();
    }

    private static void authoritativeDisabledIsCompletelyLegacy() {
        var runtime = StarterSwordRouteTestFixtures.runtime(false, 0);
        var shadow = new StarterSwordRouteTestFixtures.FakeShadow();
        StarterSwordRouteController controller = controller(false);
        DamageApplicationResult result = router(
                runtime, shadow, controller).apply(
                StarterSwordRouteTestFixtures.validRequest());
        assert result.attempted();
        assert shadow.disabledLegacyApplications == 1;
        assert runtime.totalApplications() == 0;
        StarterSwordRouteSnapshot metrics = controller.snapshot();
        assert metrics.totalHits() == 1;
        assert metrics.legacyFallbackCount() == 1;
        assert metrics.decisionCounts().get(
                StarterSwordRouteDecision.LEGACY_DISABLED) == 1;
    }

    private static void validatedConditionsUseAuthoritativeOnly() {
        var runtime = StarterSwordRouteTestFixtures.runtime(false, 0);
        var shadow = new StarterSwordRouteTestFixtures.FakeShadow();
        StarterSwordRouteController controller = controller(true);
        DamageApplicationResult result = router(
                runtime, shadow, controller).apply(
                StarterSwordRouteTestFixtures.validRequest());
        assert result.calculation() == runtime.authoritative;
        assert runtime.criticalDecisions.get() == 1;
        assert runtime.snapshotResolutions.get() == 1;
        assert runtime.authoritativeCalculations.get() == 1;
        assert runtime.authoritativeApplications.get() == 1;
        assert runtime.legacyApplications.get() == 0;
        assert runtime.totalApplications() == 1;
        assert runtime.healthEffects.get() == 1;
        assert runtime.shieldEffects.get() == 1;
        assert runtime.lifeStealEffects.get() == 1;
        StarterSwordRouteSnapshot metrics = controller.snapshot();
        assert metrics.newAuthoritativeCount() == 1;
        assert metrics.newRouteAppliedCount() == 1;
        assert metrics.legacyAppliedCount() == 0;
        assert metrics.applicationBoundaryCompletedCount() == 1;
    }

    private static void unvalidatedConditionsUseLegacyOnly() {
        assertFallback(
                StarterSwordRouteTestFixtures.runtime(true, 0),
                StarterSwordRouteTestFixtures.validRequest(),
                StarterSwordDamageShadow.ITEM_ID,
                StarterSwordRouteDecision.LEGACY_CRITICAL);
        assertFallback(
                StarterSwordRouteTestFixtures.runtime(false, 10),
                StarterSwordRouteTestFixtures.request(
                        DamageType.PHYSICAL, DamageKind.NORMAL_ATTACK,
                        DamageMode.PVE,
                        StarterSwordRouteTestFixtures.METADATA, 10),
                StarterSwordDamageShadow.ITEM_ID,
                StarterSwordRouteDecision.LEGACY_SHIELD);
        assertFallback(
                StarterSwordRouteTestFixtures.runtime(false, 0),
                StarterSwordRouteTestFixtures.validRequest(),
                "other_item",
                StarterSwordRouteDecision.LEGACY_UNSUPPORTED_ITEM);
        assertFallback(
                StarterSwordRouteTestFixtures.runtime(false, 0),
                StarterSwordRouteTestFixtures.request(
                        DamageType.PHYSICAL, DamageKind.DIRECT_SKILL,
                        DamageMode.PVE,
                        StarterSwordRouteTestFixtures.METADATA, 0),
                StarterSwordDamageShadow.ITEM_ID,
                StarterSwordRouteDecision.LEGACY_UNSUPPORTED_KIND);
        assertFallback(
                StarterSwordRouteTestFixtures.runtime(false, 0),
                StarterSwordRouteTestFixtures.request(
                        DamageType.MAGICAL, DamageKind.NORMAL_ATTACK,
                        DamageMode.PVE,
                        StarterSwordRouteTestFixtures.METADATA, 0),
                StarterSwordDamageShadow.ITEM_ID,
                StarterSwordRouteDecision.LEGACY_UNSUPPORTED_TYPE);
        assertFallback(
                StarterSwordRouteTestFixtures.runtime(false, 0),
                StarterSwordRouteTestFixtures.request(
                        DamageType.TRUE, DamageKind.NORMAL_ATTACK,
                        DamageMode.PVE,
                        StarterSwordRouteTestFixtures.METADATA, 0),
                StarterSwordDamageShadow.ITEM_ID,
                StarterSwordRouteDecision.LEGACY_UNSUPPORTED_TYPE);
    }

    private static void assertFallback(
            StarterSwordRouteTestFixtures.FakeRuntime runtime,
            DamageRequest request,
            String itemId,
            StarterSwordRouteDecision expected
    ) {
        var shadow = new StarterSwordRouteTestFixtures.FakeShadow();
        shadow.itemId = itemId;
        StarterSwordRouteController controller = controller(true);
        DamageApplicationResult result =
                router(runtime, shadow, controller).apply(request);
        assert result.calculation() == runtime.legacy;
        assert runtime.legacyApplications.get() == 1;
        assert runtime.authoritativeApplications.get() == 0;
        assert runtime.totalApplications() == 1;
        assert controller.snapshot().decisionCounts().get(expected) == 1;
    }

    static StarterSwordDamageRouter router(
            StarterSwordDamageRuntime runtime,
            StarterSwordShadowRuntime shadow,
            StarterSwordRouteController controller
    ) {
        return new StarterSwordDamageRouter(
                runtime,
                shadow,
                controller,
                new StarterSwordDamageRoutePolicy());
    }

    static StarterSwordRouteController controller(boolean enabled) {
        return new StarterSwordRouteController(
                enabled,
                new StarterSwordRouteTracker(),
                Clock.fixed(
                        Instant.parse("2026-08-04T00:00:00Z"),
                        ZoneOffset.UTC));
    }
}
