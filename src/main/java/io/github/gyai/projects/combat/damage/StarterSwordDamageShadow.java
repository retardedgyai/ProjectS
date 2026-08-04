package io.github.gyai.projects.combat.damage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/** Observes starter sword parity without applying the shadow result. */
public final class StarterSwordDamageShadow {
    public static final String ITEM_ID = "starter_sword";
    private static final long LOG_INTERVAL_MILLIS = 30_000L;

    private final DamageService damageService;
    private final DamageShadowEvaluator evaluator;
    private final boolean debugEnabled;
    private final Logger logger;
    private final Map<String, Long> lastLogBySignature = new HashMap<>();

    public StarterSwordDamageShadow(
            DamageService damageService,
            boolean enabled,
            boolean debugEnabled,
            Logger logger
    ) {
        this.damageService = Objects.requireNonNull(
                damageService, "damageService");
        evaluator = new DamageShadowEvaluator(enabled);
        this.debugEnabled = debugEnabled;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public boolean enabled() {
        return evaluator.enabled();
    }

    public void compareSafely(
            DamageRequest request,
            DamageResult legacyResult
    ) {
        if (!enabled()) {
            return;
        }
        try {
            evaluator.evaluateStarterSword(
                    legacyResult,
                    () -> damageService.resolveSnapshot(
                            request, legacyResult.critical()))
                    .filter(comparison -> !comparison.matches())
                    .ifPresent(comparison -> logMismatch(request, comparison));
        } catch (RuntimeException exception) {
            logError(request, exception);
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
            RuntimeException exception
    ) {
        if (!debugEnabled) {
            return;
        }
        String signature = "error:" + exception.getClass().getName();
        if (!acquireLogPermit(signature)) {
            return;
        }
        logger.warning(("[DamageShadow] comparison failed attacker=%s "
                + "target=%s item=%s error=%s")
                .formatted(
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
        lastLogBySignature.put(signature, now);
        return true;
    }
}
