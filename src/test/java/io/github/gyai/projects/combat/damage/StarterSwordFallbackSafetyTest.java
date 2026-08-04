package io.github.gyai.projects.combat.damage;

public final class StarterSwordFallbackSafetyTest {
    private StarterSwordFallbackSafetyTest() {
    }

    public static void main(String[] args) {
        snapshotFailureFallsBackBeforeApplication();
        calculationFailureFallsBackBeforeApplication();
        invalidResultsFallBackBeforeApplication();
    }

    private static void snapshotFailureFallsBackBeforeApplication() {
        var runtime = StarterSwordRouteTestFixtures.runtime(false, 0);
        runtime.snapshotFailure = new IllegalStateException("snapshot");
        assertSafeFallback(
                runtime,
                StarterSwordRouteDecision.LEGACY_INVALID_SNAPSHOT);
    }

    private static void calculationFailureFallsBackBeforeApplication() {
        var runtime = StarterSwordRouteTestFixtures.runtime(false, 0);
        runtime.calculationFailure = new IllegalStateException("calculation");
        assertSafeFallback(
                runtime,
                StarterSwordRouteDecision.LEGACY_CALCULATION_FAILURE);
    }

    private static void invalidResultsFallBackBeforeApplication() {
        assertInvalid(Double.NaN);
        assertInvalid(Double.POSITIVE_INFINITY);
        assertInvalid(-1);
    }

    private static void assertInvalid(double finalDamage) {
        var runtime = StarterSwordRouteTestFixtures.runtime(false, 0);
        runtime.authoritative = DamageShadowTestFixtures.withFinalDamage(
                runtime.authoritative, finalDamage);
        assertSafeFallback(
                runtime,
                StarterSwordRouteDecision.LEGACY_INVALID_RESULT);
    }

    private static void assertSafeFallback(
            StarterSwordRouteTestFixtures.FakeRuntime runtime,
            StarterSwordRouteDecision expected
    ) {
        var shadow = new StarterSwordRouteTestFixtures.FakeShadow();
        StarterSwordRouteController controller =
                StarterSwordLimitedCutoverTest.controller(true);
        DamageApplicationResult result =
                StarterSwordLimitedCutoverTest.router(
                        runtime, shadow, controller)
                        .apply(StarterSwordRouteTestFixtures.validRequest());
        assert result.calculation() == runtime.legacy;
        assert runtime.legacyApplications.get() == 1;
        assert runtime.authoritativeApplications.get() == 0;
        assert runtime.totalApplications() == 1;
        assert runtime.healthEffects.get() == 1;
        assert runtime.shieldEffects.get() == 1;
        assert runtime.lifeStealEffects.get() == 1;
        StarterSwordRouteSnapshot metrics = controller.snapshot();
        assert metrics.decisionCounts().get(expected) == 1;
        assert metrics.newRouteFailureCount() == 1;
        assert metrics.applicationBoundaryCompletedCount() == 1;
    }
}
