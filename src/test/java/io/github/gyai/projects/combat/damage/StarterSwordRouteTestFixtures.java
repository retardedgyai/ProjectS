package io.github.gyai.projects.combat.damage;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

final class StarterSwordRouteTestFixtures {
    static final AttackMetadata METADATA = new AttackMetadata(
            Set.of(AttackTag.NORMAL_ATTACK, AttackTag.MELEE, AttackTag.PHYSICAL),
            ElementProfile.EMPTY);

    private StarterSwordRouteTestFixtures() {
    }

    static DamageRequest request(
            DamageType type,
            DamageKind kind,
            DamageMode mode,
            AttackMetadata metadata,
            double currentShield
    ) {
        return DamageRequest.builder(
                        proxy(Player.class, 0),
                        proxy(LivingEntity.class, currentShield))
                .skillId("normal_attack")
                .damageType(type)
                .damageKind(kind)
                .mode(mode)
                .fixedDamage(100)
                .coefficient(0)
                .attackMetadata(metadata)
                .build();
    }

    static DamageRequest validRequest() {
        return request(
                DamageType.PHYSICAL,
                DamageKind.NORMAL_ATTACK,
                DamageMode.PVE,
                METADATA,
                0);
    }

    static DamageShadowRuntimeContext context(String itemId) {
        return new DamageShadowRuntimeContext(
                java.time.Instant.parse("2026-08-04T00:00:00Z"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                DamageShadowTargetType.TRAINING_DUMMY,
                itemId,
                0);
    }

    static FakeRuntime runtime(boolean critical, double snapshotShield) {
        return new FakeRuntime(
                DamageShadowTestFixtures.snapshot(
                        critical, snapshotShield));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, double shield) {
        UUID id = UUID.randomUUID();
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getAbsorptionAmount" -> shield;
                    case "getUniqueId" -> id;
                    case "toString" -> type.getSimpleName();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        throw new AssertionError("Unknown primitive: " + type);
    }

    static final class FakeRuntime implements StarterSwordDamageRuntime {
        DamageCalculationSnapshot snapshot;
        DamageResult legacy;
        DamageResult authoritative;
        RuntimeException snapshotFailure;
        RuntimeException calculationFailure;
        final AtomicInteger criticalDecisions = new AtomicInteger();
        final AtomicInteger snapshotResolutions = new AtomicInteger();
        final AtomicInteger authoritativeCalculations = new AtomicInteger();
        final AtomicInteger legacyApplications = new AtomicInteger();
        final AtomicInteger authoritativeApplications = new AtomicInteger();
        final AtomicInteger healthEffects = new AtomicInteger();
        final AtomicInteger shieldEffects = new AtomicInteger();
        final AtomicInteger lifeStealEffects = new AtomicInteger();

        FakeRuntime(DamageCalculationSnapshot snapshot) {
            this.snapshot = snapshot;
            legacy = snapshot.calculate();
            authoritative = snapshot.calculate();
        }

        @Override
        public DamageResult calculateLegacy(DamageRequest request) {
            criticalDecisions.incrementAndGet();
            return legacy;
        }

        @Override
        public DamageCalculationSnapshot resolveSnapshot(
                DamageRequest request,
                boolean criticalDecision
        ) {
            snapshotResolutions.incrementAndGet();
            if (snapshotFailure != null) throw snapshotFailure;
            assert criticalDecision == legacy.critical();
            return snapshot;
        }

        @Override
        public DamageResult calculateAuthoritative(
                DamageCalculationSnapshot snapshot
        ) {
            authoritativeCalculations.incrementAndGet();
            if (calculationFailure != null) throw calculationFailure;
            return authoritative;
        }

        @Override
        public DamageApplicationResult applyLegacy(
                DamageRequest request,
                DamageResult result
        ) {
            legacyApplications.incrementAndGet();
            sideEffects();
            return new DamageApplicationResult(result, true, 0, 1, 0);
        }

        @Override
        public DamageApplicationResult applyAuthoritative(
                DamageRequest request,
                DamageResult result
        ) {
            authoritativeApplications.incrementAndGet();
            sideEffects();
            return new DamageApplicationResult(result, true, 0, 1, 0);
        }

        int totalApplications() {
            return legacyApplications.get()
                    + authoritativeApplications.get();
        }

        private void sideEffects() {
            healthEffects.incrementAndGet();
            shieldEffects.incrementAndGet();
            lifeStealEffects.incrementAndGet();
        }
    }

    static final class FakeShadow implements StarterSwordShadowRuntime {
        boolean enabled;
        boolean throwObserver;
        String itemId = StarterSwordDamageShadow.ITEM_ID;
        int disabledLegacyApplications;
        int legacyObservations;
        int authoritativeObservations;

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public DamageApplicationResult apply(DamageRequest request) {
            disabledLegacyApplications++;
            DamageResult result =
                    DamageShadowTestFixtures.snapshot(false, 0).calculate();
            return new DamageApplicationResult(result, true, 0, 1, 0);
        }

        @Override
        public DamageShadowRuntimeContext resolveContext(DamageRequest request) {
            return context(itemId);
        }

        @Override
        public void compareLegacySafely(
                DamageShadowRuntimeContext context,
                DamageRequest request,
                DamageResult legacyResult
        ) {
            legacyObservations++;
            if (throwObserver) throw new IllegalStateException("observer");
        }

        @Override
        public Optional<DamageShadowComparison> comparePrecalculatedSafely(
                DamageShadowRuntimeContext context,
                DamageRequest request,
                DamageResult legacyResult,
                DamageResult shadowResult,
                DamageCalculationSnapshot snapshot
        ) {
            authoritativeObservations++;
            if (throwObserver) throw new IllegalStateException("observer");
            return Optional.of(DamageShadowComparator.compareStarterSword(
                    legacyResult, shadowResult, snapshot));
        }
    }
}
