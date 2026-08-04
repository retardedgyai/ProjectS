package io.github.gyai.projects.combat.damage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** Observes starter sword parity without applying the shadow result. */
public final class StarterSwordDamageShadow {
    public static final String ITEM_ID = "starter_sword";
    private static final long LOG_INTERVAL_MILLIS = 30_000L;
    private static final int MAX_LOG_SIGNATURES = 64;

    private final DamageService damageService;
    private final DamageShadowValidationController validationController;
    private final DamageShadowRuntimeContextResolver contextResolver;
    private final DamageShadowComparisonObserver comparisonObserver;
    private final boolean debugEnabled;
    private final Logger logger;
    private final Map<String, Long> lastLogBySignature = new HashMap<>();

    public StarterSwordDamageShadow(
            DamageService damageService,
            DamageShadowValidationController validationController,
            DamageShadowRuntimeContextResolver contextResolver,
            boolean debugEnabled,
            Logger logger
    ) {
        this.damageService = Objects.requireNonNull(
                damageService, "damageService");
        this.validationController = Objects.requireNonNull(
                validationController, "validationController");
        this.contextResolver = Objects.requireNonNull(
                contextResolver, "contextResolver");
        comparisonObserver = new DamageShadowComparisonObserver(
                validationController);
        this.debugEnabled = debugEnabled;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public boolean enabled() {
        return validationController.enabled();
    }

    public DamageApplicationResult apply(DamageRequest request) {
        if (!enabled()) {
            return damageService.apply(request);
        }
        DamageShadowRuntimeContext context;
        try {
            context = contextResolver.resolve(request);
        } catch (RuntimeException exception) {
            validationController.recordShadowFailure();
            logError(request, "context", exception);
            return damageService.apply(
                    request,
                    null,
                    legacyFailureObserver(request));
        }
        return damageService.apply(
                request,
                legacy -> compareSafely(context, request, legacy),
                legacyFailureObserver(request));
    }

    public DamageShadowValidationController validationController() {
        return validationController;
    }

    private void compareSafely(
            DamageShadowRuntimeContext context,
            DamageRequest request,
            DamageResult legacyResult
    ) {
        if (!enabled()) {
            return;
        }
        try {
            comparisonObserver.observeStarterSword(
                    context,
                    legacyResult,
                    () -> damageService.resolveSnapshot(
                            request, legacyResult.critical()),
                    exception -> logError(request, "shadow", exception))
                    .filter(comparison -> !comparison.matches())
                    .ifPresent(comparison -> logMismatch(request, comparison));
        } catch (RuntimeException exception) {
            // Defensive outer boundary for future observer changes.
            validationController.recordShadowFailure();
            logError(request, "shadow", exception);
        }
    }

    private Consumer<RuntimeException> legacyFailureObserver(
            DamageRequest request
    ) {
        return exception -> {
            validationController.recordLegacyFailure();
            logError(request, "legacy", exception);
        };
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
        DamageCalculationSnapshot snapshot = comparison.snapshot();
        logger.warning(("[DamageShadow] attacker=%s target=%s item=%s "
                + "legacy=%s shadow=%s difference={numeric=%s,context=%s} "
                + "penetration={percent=%s,flat=%s} "
                + "type=%s kind=%s mode=%s metadata=%s")
                .formatted(
                        request.attacker().getUniqueId(),
                        request.target().getUniqueId(),
                        ITEM_ID,
                        comparison.legacyResult(),
                        comparison.shadowResult(),
                        comparison.numericDifferences(),
                        comparison.contextDifferences(),
                        snapshot.penetrationPercent(),
                        snapshot.flatPenetration(),
                        snapshot.damageType(),
                        snapshot.damageKind(),
                        snapshot.mode(),
                        snapshot.attackMetadata()));
    }

    private void logError(
            DamageRequest request,
            String stage,
            RuntimeException exception
    ) {
        String signature = "error:" + stage + ":"
                + exception.getClass().getName();
        if (!acquireLogPermit(signature)) {
            return;
        }
        logger.warning(("[DamageShadow] stage=%s failed attacker=%s "
                + "target=%s item=%s error=%s")
                .formatted(
                        stage,
                        request.attacker().getUniqueId(),
                        request.target().getUniqueId(),
                        ITEM_ID,
                        exception));
    }

    private boolean acquireLogPermit(String signature) {
        long now = System.currentTimeMillis();
        long last = lastLogBySignature.getOrDefault(signature, Long.MIN_VALUE);
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

    public void close() {
        validationController.close();
        lastLogBySignature.clear();
    }
}
