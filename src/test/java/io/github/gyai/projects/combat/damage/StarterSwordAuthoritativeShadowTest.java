package io.github.gyai.projects.combat.damage;

public final class StarterSwordAuthoritativeShadowTest {
    private StarterSwordAuthoritativeShadowTest() {
    }

    public static void main(String[] args) {
        authoritativeAndShadowStillApplyOnce();
        authoritativeShadowMismatchIsCountedWithoutDoubleApplication();
        observerFailureDoesNotCancelAuthoritativeApplication();
        commandEnableDisableAndMetricsWork();
    }

    private static void authoritativeShadowMismatchIsCountedWithoutDoubleApplication() {
        var runtime = StarterSwordRouteTestFixtures.runtime(false, 0);
        runtime.authoritative = DamageShadowTestFixtures.withFinalDamage(
                runtime.authoritative,
                runtime.authoritative.finalRoundedDamage() + 1);
        var shadow = new StarterSwordRouteTestFixtures.FakeShadow();
        shadow.enabled = true;
        StarterSwordRouteController controller =
                StarterSwordLimitedCutoverTest.controller(true);
        StarterSwordLimitedCutoverTest.router(
                        runtime, shadow, controller)
                .apply(StarterSwordRouteTestFixtures.validRequest());
        assert runtime.totalApplications() == 1;
        assert runtime.authoritativeApplications.get() == 1;
        assert controller.snapshot().authoritativeShadowMatchCount() == 0;
        assert controller.snapshot().authoritativeShadowMismatchCount() == 1;
    }

    private static void authoritativeAndShadowStillApplyOnce() {
        var runtime = StarterSwordRouteTestFixtures.runtime(false, 0);
        var shadow = new StarterSwordRouteTestFixtures.FakeShadow();
        shadow.enabled = true;
        StarterSwordRouteController controller =
                StarterSwordLimitedCutoverTest.controller(true);
        StarterSwordLimitedCutoverTest.router(
                        runtime, shadow, controller)
                .apply(StarterSwordRouteTestFixtures.validRequest());
        assert shadow.authoritativeObservations == 1;
        assert runtime.authoritativeApplications.get() == 1;
        assert runtime.legacyApplications.get() == 0;
        assert runtime.totalApplications() == 1;
        StarterSwordRouteSnapshot metrics = controller.snapshot();
        assert metrics.authoritativeShadowMatchCount() == 1;
        assert metrics.authoritativeShadowMismatchCount() == 0;
    }

    private static void observerFailureDoesNotCancelAuthoritativeApplication() {
        var runtime = StarterSwordRouteTestFixtures.runtime(false, 0);
        var shadow = new StarterSwordRouteTestFixtures.FakeShadow();
        shadow.enabled = true;
        shadow.throwObserver = true;
        StarterSwordRouteController controller =
                StarterSwordLimitedCutoverTest.controller(true);
        DamageApplicationResult result =
                StarterSwordLimitedCutoverTest.router(
                                runtime, shadow, controller)
                        .apply(StarterSwordRouteTestFixtures.validRequest());
        assert result.attempted();
        assert runtime.authoritativeApplications.get() == 1;
        assert runtime.legacyApplications.get() == 0;
        assert runtime.totalApplications() == 1;
    }

    private static void commandEnableDisableAndMetricsWork() {
        StarterSwordRouteController controller =
                StarterSwordLimitedCutoverTest.controller(false);
        StarterSwordRouteCommandService commands =
                new StarterSwordRouteCommandService(controller);
        assert !controller.enabled();
        assert commands.execute("enable").success();
        assert controller.enabled();
        controller.recordDecision(
                StarterSwordRouteDecision.LEGACY_CRITICAL);
        controller.recordDecision(
                StarterSwordRouteDecision.LEGACY_SHIELD);
        assert commands.execute("summary").messages().stream()
                .anyMatch(value -> value.contains("legacyFallback=2"));
        assert controller.snapshot().criticalFallbackCount() == 1;
        assert controller.snapshot().shieldFallbackCount() == 1;
        assert commands.execute("reset").success();
        assert controller.snapshot().totalHits() == 0;
        assert commands.execute("disable").success();
        assert !controller.enabled();
        assert !commands.execute("unknown").success();
    }
}
