package io.github.gyai.projects.combat.damage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** Observes SpinSlash parity while applying only the calculated legacy result. */
public final class SpinSlashDamageShadow implements DamageShadowRoute {
    public static final String SKILL_ID = "spin_slash";
    public static final DamageShadowExpectation EXPECTATION =
            new DamageShadowExpectation(
                    "warrior-spin-slash",
                    DamageType.PHYSICAL,
                    DamageKind.DIRECT_SKILL,
                    DamageMode.PVE,
                    Set.of(
                            AttackTag.SKILL,
                            AttackTag.MELEE,
                            AttackTag.PHYSICAL),
                    ElementProfile.EMPTY);
    private static final long LOG_INTERVAL_MILLIS = 30_000L;
    private static final int MAX_LOG_SIGNATURES = 64;

    private final DamageShadowLegacyRuntime legacyRuntime;
    private final DamageShadowValidationController validationController;
    private final DamageShadowRuntimeContextResolver contextResolver;
    private final DamageShadowComparisonObserver comparisonObserver;
    private final boolean debugEnabled;
    private final Logger logger;
    private final Map<String, Long> lastLogBySignature = new HashMap<>();

    public SpinSlashDamageShadow(
            DamageService damageService,
            DamageShadowValidationController validationController,
            DamageShadowRuntimeContextResolver contextResolver,
            boolean debugEnabled,
            Logger logger
    ) {
        this(new DamageServiceShadowRuntime(damageService),
                validationController, contextResolver,
                debugEnabled, logger);
    }

    public SpinSlashDamageShadow(
            DamageShadowLegacyRuntime legacyRuntime,
            DamageShadowValidationController validationController,
            DamageShadowRuntimeContextResolver contextResolver,
            boolean debugEnabled,
            Logger logger
    ) {
        this.legacyRuntime = Objects.requireNonNull(
                legacyRuntime, "legacyRuntime");
        this.validationController = Objects.requireNonNull(
                validationController, "validationController");
        this.contextResolver = Objects.requireNonNull(
                contextResolver, "contextResolver");
        comparisonObserver = new DamageShadowComparisonObserver(
                validationController);
        this.debugEnabled = debugEnabled;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public boolean supports(DamageRequest request) {
        return validationController.enabled()
                && request != null
                && SKILL_ID.equals(request.skillId())
                && EXPECTATION.matches(request)
                && request.areaDamage()
                && request.offenseSnapshot() == null
                && request.attacker().isValid()
                && request.target().isValid()
                && Double.isFinite(request.fixedDamage())
                && Double.isFinite(request.coefficient());
    }

    @Override
    public DamageApplicationResult apply(DamageRequest request) {
        DamageShadowRuntimeContext context;
        try {
            context = contextResolver.resolve(request);
        } catch (RuntimeException exception) {
            recordShadowFailureSafely();
            logError(request, "context", exception);
            return legacyRuntime.apply(
                    request, null, legacyFailureObserver(request));
        }
        return legacyRuntime.apply(
                request,
                legacy -> compareLegacySafely(context, request, legacy),
                legacyFailureObserver(request));
    }

    private void compareLegacySafely(
            DamageShadowRuntimeContext context,
            DamageRequest request,
            DamageResult legacyResult
    ) {
        try {
            comparisonObserver.observe(
                    context,
                    legacyResult,
                    () -> legacyRuntime.resolveSnapshot(
                            request, legacyResult.critical()),
                    EXPECTATION,
                    exception -> logError(request, "shadow", exception))
                    .filter(comparison -> !comparison.matches())
                    .ifPresent(comparison -> logMismatch(request, comparison));
        } catch (RuntimeException exception) {
            recordShadowFailureSafely();
            logError(request, "shadow", exception);
        }
    }

    @Override
    public void recordDispatchFailure(
            DamageRequest request,
            RuntimeException exception
    ) {
        recordShadowFailureSafely();
        logError(request, "supports", exception);
    }

    private Consumer<RuntimeException> legacyFailureObserver(
            DamageRequest request
    ) {
        return exception -> {
            try {
                validationController.recordLegacyFailure();
            } catch (RuntimeException ignored) {
                // Metrics remain observational.
            }
            logError(request, "legacy", exception);
        };
    }

    private void recordShadowFailureSafely() {
        try {
            validationController.recordShadowFailure();
        } catch (RuntimeException ignored) {
            // Metrics remain observational.
        }
    }

    private void logMismatch(
            DamageRequest request,
            DamageShadowComparison comparison
    ) {
        if (!debugEnabled) {
            return;
        }
        String signature = "mismatch:"
                + comparison.numericDifferences().keySet()
                + comparison.contextDifferences();
        if (!acquireLogPermit(signature)) {
            return;
        }
        try {
            logger.warning(("[DamageShadow:spin_slash] attacker=%s target=%s "
                    + "legacy=%s shadow=%s difference={numeric=%s,context=%s}")
                    .formatted(
                            safeAttackerId(request),
                            safeTargetId(request),
                            comparison.legacyResult(),
                            comparison.shadowResult(),
                            comparison.numericDifferences(),
                            comparison.contextDifferences()));
        } catch (RuntimeException ignored) {
            // Logging must remain fail-open.
        }
    }

    private void logError(
            DamageRequest request,
            String stage,
            RuntimeException exception
    ) {
        if (!debugEnabled) {
            return;
        }
        String signature = "error:" + stage + ":"
                + exception.getClass().getName();
        if (!acquireLogPermit(signature)) {
            return;
        }
        try {
            logger.warning(("[DamageShadow:spin_slash] stage=%s failed "
                    + "attacker=%s target=%s error=%s")
                    .formatted(stage, safeAttackerId(request),
                            safeTargetId(request), exception));
        } catch (RuntimeException ignored) {
            // Logging must remain fail-open.
        }
    }

    private static String safeAttackerId(DamageRequest request) {
        try {
            return request == null ? "unknown"
                    : request.attacker().getUniqueId().toString();
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }

    private static String safeTargetId(DamageRequest request) {
        try {
            return request == null ? "unknown"
                    : request.target().getUniqueId().toString();
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }

    private boolean acquireLogPermit(String signature) {
        long now = System.currentTimeMillis();
        long last = lastLogBySignature.getOrDefault(
                signature, Long.MIN_VALUE);
        if (last != Long.MIN_VALUE && now - last < LOG_INTERVAL_MILLIS) {
            return false;
        }
        if (!lastLogBySignature.containsKey(signature)
                && lastLogBySignature.size() >= MAX_LOG_SIGNATURES) {
            lastLogBySignature.clear();
        }
        lastLogBySignature.put(signature, now);
        return true;
    }

    public DamageShadowValidationController validationController() {
        return validationController;
    }

    public void close() {
        validationController.close();
        lastLogBySignature.clear();
    }
}
