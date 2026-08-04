package io.github.gyai.projects.combat.damage;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public final class SpinSlashDamageShadowRuntimeTest {
    private SpinSlashDamageShadowRuntimeTest() {
    }

    public static void main(String[] args) {
        disabledAndUnsupportedRequestsStayFullyLegacy();
        enabledRouteCalculatesAndAppliesLegacyExactlyOnce();
        shadowStagesFailOpenWithoutRecalculation();
        legacyFailureKeepsExistingExceptionSemantics();
        multipleTargetsUseOneComparisonAndApplicationPerTarget();
        dispatcherNeverRetriesAfterRouteApplicationStarts();
    }

    private static void disabledAndUnsupportedRequestsStayFullyLegacy() {
        Fixture disabled = fixture(false, request -> context(request));
        disabled.dispatcher.apply(request("spin_slash", metadata(), true,
                UUID.randomUUID(), entity(UUID.randomUUID(), true)));
        assert disabled.runtime.calculations.get() == 1;
        assert disabled.runtime.applications.get() == 1;
        assert disabled.runtime.snapshots.get() == 0;
        assert disabled.controller.snapshot().comparisonCount() == 0;

        Fixture mismatch = fixture(true, request -> context(request));
        mismatch.dispatcher.apply(request(
                "spin_slash", AttackMetadata.EMPTY, true,
                UUID.randomUUID(), entity(UUID.randomUUID(), true)));
        mismatch.dispatcher.apply(request(
                "sweeping_slash", metadata(), true,
                UUID.randomUUID(), entity(UUID.randomUUID(), true)));
        mismatch.dispatcher.apply(request(
                "spin_slash", metadata(), false,
                UUID.randomUUID(), entity(UUID.randomUUID(), true)));
        assert mismatch.runtime.calculations.get() == 3;
        assert mismatch.runtime.applications.get() == 3;
        assert mismatch.runtime.snapshots.get() == 0;
        assert mismatch.controller.snapshot().comparisonCount() == 0;
    }

    private static void enabledRouteCalculatesAndAppliesLegacyExactlyOnce() {
        Fixture fixture = fixture(true, request -> context(request));
        DamageRequest request = request(
                "spin_slash", metadata(), true,
                UUID.randomUUID(), entity(UUID.randomUUID(), true));
        DamageApplicationResult result = fixture.dispatcher.apply(request);
        assert result.attempted();
        assert fixture.runtime.calculations.get() == 1;
        assert fixture.runtime.criticalRolls.get() == 1;
        assert fixture.runtime.snapshots.get() == 1;
        assert fixture.runtime.shadowApplications.get() == 0;
        assert fixture.runtime.applications.get() == 1;
        assert fixture.runtime.snapshotCritical == fixture.runtime.legacy.critical();
        DamageShadowValidationSnapshot validation =
                fixture.controller.snapshot();
        assert validation.comparisonCount() == 1;
        assert validation.matchCount() == 1;
        assert validation.mismatchCount() == 0;
        assert validation.criticalCount() == 1;
        assert validation.shieldPresentCount() == 1;
    }

    private static void shadowStagesFailOpenWithoutRecalculation() {
        AtomicInteger contextCalls = new AtomicInteger();
        Fixture contextFailure = fixture(true, request -> {
            contextCalls.incrementAndGet();
            throw new IllegalStateException("context");
        });
        contextFailure.dispatcher.apply(request(
                "spin_slash", metadata(), true, UUID.randomUUID(),
                entity(UUID.randomUUID(), true)));
        assert contextCalls.get() == 1;
        assert contextFailure.runtime.calculations.get() == 1;
        assert contextFailure.runtime.applications.get() == 1;
        assert contextFailure.controller.snapshot().shadowFailureCount() == 1;

        Fixture snapshotFailure = fixture(true, request -> context(request));
        snapshotFailure.runtime.failSnapshot = true;
        snapshotFailure.dispatcher.apply(request(
                "spin_slash", metadata(), true, UUID.randomUUID(),
                entity(UUID.randomUUID(), true)));
        assert snapshotFailure.runtime.calculations.get() == 1;
        assert snapshotFailure.runtime.applications.get() == 1;
        assert snapshotFailure.runtime.snapshots.get() == 1;
        assert snapshotFailure.controller.snapshot().shadowFailureCount() == 1;

        AtomicInteger validityCalls = new AtomicInteger();
        Fixture supportsFailure = fixture(true, request -> context(request));
        DamageRequest broken = request(
                "spin_slash", metadata(), true, UUID.randomUUID(),
                throwingEntity(validityCalls));
        supportsFailure.dispatcher.apply(broken);
        assert validityCalls.get() == 1;
        assert supportsFailure.runtime.calculations.get() == 1;
        assert supportsFailure.runtime.applications.get() == 1;
        assert supportsFailure.controller.snapshot().shadowFailureCount() == 1;
    }

    private static void legacyFailureKeepsExistingExceptionSemantics() {
        Fixture fixture = fixture(true, request -> context(request));
        fixture.runtime.failLegacyCalculation = true;
        boolean failed = false;
        try {
            fixture.dispatcher.apply(request(
                    "spin_slash", metadata(), true, UUID.randomUUID(),
                    entity(UUID.randomUUID(), true)));
        } catch (IllegalStateException expected) {
            failed = true;
        }
        assert failed;
        assert fixture.runtime.calculations.get() == 1;
        assert fixture.runtime.applications.get() == 0;
        assert fixture.controller.snapshot().legacyFailureCount() == 1;
    }

    private static void multipleTargetsUseOneComparisonAndApplicationPerTarget() {
        Fixture fixture = fixture(true, request -> context(request));
        UUID castId = UUID.randomUUID();
        for (int index = 0; index < 3; index++) {
            fixture.dispatcher.apply(request(
                    "spin_slash", metadata(), true, castId,
                    entity(UUID.randomUUID(), true)));
        }
        assert fixture.runtime.calculations.get() == 3;
        assert fixture.runtime.criticalRolls.get() == 1;
        assert fixture.runtime.applications.get() == 3;
        assert fixture.runtime.snapshots.get() == 3;
        assert fixture.controller.snapshot().comparisonCount() == 3;
        assert fixture.runtime.castIds.stream().allMatch(castId::equals);
        assert fixture.runtime.lastAreaDamage;
        assert fixture.runtime.lastLifeStealEfficiency
                == DamageKind.DIRECT_SKILL.lifeStealEfficiency(
                        true, DamageType.PHYSICAL);
    }

    private static void dispatcherNeverRetriesAfterRouteApplicationStarts() {
        Fixture fixture = fixture(true, request -> context(request));
        fixture.runtime.failAfterApplication = true;
        boolean failed = false;
        try {
            fixture.dispatcher.apply(request(
                    "spin_slash", metadata(), true, UUID.randomUUID(),
                    entity(UUID.randomUUID(), true)));
        } catch (IllegalStateException expected) {
            failed = true;
        }
        assert failed;
        assert fixture.runtime.calculations.get() == 1;
        assert fixture.runtime.applications.get() == 1;
    }

    private static Fixture fixture(
            boolean enabled,
            DamageShadowRuntimeContextResolver resolver
    ) {
        DamageShadowValidationController controller = controller(enabled);
        FakeLegacyRuntime runtime = new FakeLegacyRuntime();
        SpinSlashDamageShadow route = new SpinSlashDamageShadow(
                runtime, controller, resolver, false,
                Logger.getAnonymousLogger());
        return new Fixture(runtime, controller,
                new DamageShadowDispatcher(runtime::apply, List.of(route)));
    }

    private static DamageShadowValidationController controller(
            boolean enabled
    ) {
        return new DamageShadowValidationController(
                enabled,
                new DamageShadowValidationTracker(5),
                new DamageShadowValidationExporter("spin-slash-shadow"),
                Path.of("unused-spin-shadow-export"),
                Clock.fixed(
                        Instant.parse("2026-08-05T00:00:00Z"),
                        ZoneOffset.UTC),
                Logger.getAnonymousLogger());
    }

    private static DamageRequest request(
            String skillId,
            AttackMetadata metadata,
            boolean areaDamage,
            UUID castId,
            LivingEntity target
    ) {
        return DamageRequest.builder(player(
                        UUID.fromString(
                                "00000000-0000-0000-0000-000000000001"),
                        true), target)
                .skillId(skillId)
                .castId(castId)
                .damageType(DamageType.PHYSICAL)
                .damageKind(DamageKind.DIRECT_SKILL)
                .mode(DamageMode.PVE)
                .areaDamage(areaDamage)
                .fixedDamage(100)
                .coefficient(0)
                .attackMetadata(metadata)
                .build();
    }

    private static AttackMetadata metadata() {
        return new AttackMetadata(
                SpinSlashDamageShadow.EXPECTATION.exactTags(),
                ElementProfile.EMPTY);
    }

    private static DamageShadowRuntimeContext context(
            DamageRequest request
    ) {
        return new DamageShadowRuntimeContext(
                Instant.parse("2026-08-05T00:00:00Z"),
                request.attacker().getUniqueId(),
                request.target().getUniqueId(),
                DamageShadowTargetType.TRAINING_DUMMY,
                "warrior_blade",
                0);
    }

    private static Player player(UUID id, boolean valid) {
        return proxy(Player.class, id, valid, null);
    }

    private static LivingEntity entity(UUID id, boolean valid) {
        return proxy(LivingEntity.class, id, valid, null);
    }

    private static LivingEntity throwingEntity(AtomicInteger calls) {
        return proxy(LivingEntity.class, UUID.randomUUID(), true, calls);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(
            Class<T> type,
            UUID id,
            boolean valid,
            AtomicInteger validityCalls
    ) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type},
                (value, method, arguments) -> {
                    if (method.getName().equals("getUniqueId")) return id;
                    if (method.getName().equals("isValid")) {
                        if (validityCalls != null) {
                            validityCalls.incrementAndGet();
                            throw new IllegalStateException("isValid");
                        }
                        return valid;
                    }
                    if (method.getName().equals("isDead")) return false;
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }

    private record Fixture(
            FakeLegacyRuntime runtime,
            DamageShadowValidationController controller,
            DamageShadowDispatcher dispatcher
    ) {
    }

    private static final class FakeLegacyRuntime
            implements DamageShadowLegacyRuntime {
        private final AtomicInteger calculations = new AtomicInteger();
        private final AtomicInteger criticalRolls = new AtomicInteger();
        private final AtomicInteger applications = new AtomicInteger();
        private final AtomicInteger shadowApplications = new AtomicInteger();
        private final AtomicInteger snapshots = new AtomicInteger();
        private final java.util.ArrayList<UUID> castIds =
                new java.util.ArrayList<>();
        private final java.util.HashSet<UUID> criticalCasts =
                new java.util.HashSet<>();
        private final DamageCalculationSnapshot snapshot =
                GenericDamageShadowComparatorTest.spinSnapshot(
                        DamageType.PHYSICAL, DamageKind.DIRECT_SKILL,
                        DamageMode.PVE,
                        SpinSlashDamageShadow.EXPECTATION.exactTags(),
                        ElementProfile.EMPTY, true, 25,
                        DamageKind.DIRECT_SKILL.lifeStealEfficiency(
                                true, DamageType.PHYSICAL));
        private final DamageResult legacy = snapshot.calculate();
        private boolean failSnapshot;
        private boolean failLegacyCalculation;
        private boolean failAfterApplication;
        private boolean snapshotCritical;
        private boolean lastAreaDamage;
        private double lastLifeStealEfficiency;

        @Override
        public DamageApplicationResult apply(DamageRequest request) {
            calculate(request);
            return applyLegacy();
        }

        @Override
        public DamageApplicationResult apply(
                DamageRequest request,
                java.util.function.Consumer<DamageResult> observer,
                java.util.function.Consumer<RuntimeException> failureObserver
        ) {
            DamageResult result;
            try {
                result = calculate(request);
            } catch (RuntimeException exception) {
                if (failureObserver != null) failureObserver.accept(exception);
                throw exception;
            }
            return DamageService.observeThenApply(
                    result, observer, ignored -> applyLegacy());
        }

        private DamageResult calculate(DamageRequest request) {
            calculations.incrementAndGet();
            if (criticalCasts.add(request.castId())) {
                criticalRolls.incrementAndGet();
            }
            castIds.add(request.castId());
            lastAreaDamage = request.areaDamage();
            lastLifeStealEfficiency = request.lifeStealEfficiency();
            if (failLegacyCalculation) {
                throw new IllegalStateException("legacy calculation");
            }
            return legacy;
        }

        private DamageApplicationResult applyLegacy() {
            applications.incrementAndGet();
            if (failAfterApplication) {
                throw new IllegalStateException("application boundary");
            }
            return new DamageApplicationResult(
                    legacy, true, legacy.shieldDamage(),
                    legacy.healthDamage(), legacy.lifeStealHealing());
        }

        @Override
        public DamageCalculationSnapshot resolveSnapshot(
                DamageRequest request,
                boolean critical
        ) {
            snapshots.incrementAndGet();
            snapshotCritical = critical;
            if (failSnapshot) {
                throw new IllegalStateException("snapshot");
            }
            return snapshot;
        }
    }
}
