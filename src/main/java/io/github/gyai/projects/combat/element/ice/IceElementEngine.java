package io.github.gyai.projects.combat.element.ice;

import io.github.gyai.projects.combat.element.ElementAttackSchool;
import io.github.gyai.projects.combat.element.ElementTargetCategory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded pure state machine for shared cold, freeze, and one-shot shatter. */
public final class IceElementEngine {
    private static final double EPSILON = 1.0e-9;

    private final Policy policy;
    private final LinkedHashMap<String, StateSnapshot> states = new LinkedHashMap<>();

    public IceElementEngine(Policy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public synchronized HitResult apply(Hit hit) {
        Objects.requireNonNull(hit, "hit");
        StateSnapshot current = states.get(hit.targetId());
        if (current == null && states.size() >= policy.maximumTargets()) {
            return HitResult.capacityRejected(empty(hit), CapacityReason.TARGETS);
        }
        if (current == null) current = empty(hit);
        else {
            requireCompatible(current.profile(), hit.profile());
            requireMonotonic(hit.occurredAtMillis(), current.lastUpdatedAtMillis());
        }

        boolean frozenBeforeHit = current.frozen();
        double directMultiplier = frozenBeforeHit
                && hit.origin().receivesFrozenDirectBonus()
                ? policy.frozenDirectDamageMultiplier() : 1.0;
        boolean validIceHit = hit.origin().acceptsColdInput();
        double acceptedCold = validIceHit
                ? Math.min(hit.coldValue(),
                Math.max(0.0, hit.profile().freezeThreshold() - current.coldValue()))
                : 0.0;

        LinkedHashMap<UUID, Contribution> contributions =
                new LinkedHashMap<>(current.contributions());
        if (acceptedCold > EPSILON
                && !contributions.containsKey(hit.contributorId())
                && contributions.size() >= policy.maximumContributorsPerTarget()) {
            return HitResult.capacityRejected(current, CapacityReason.CONTRIBUTORS);
        }
        if (acceptedCold > EPSILON) {
            Contribution prior = contributions.getOrDefault(
                    hit.contributorId(), Contribution.ZERO);
            contributions.put(hit.contributorId(), prior.add(
                    hit.school(), acceptedCold, hit.iceAttributeValue()));
        }

        double cold = current.coldValue() + acceptedCold;
        boolean frozen = current.frozen();
        long freezeGeneration = current.freezeGeneration();
        long lastShatteredGeneration = current.lastShatteredGeneration();
        IceCoreSnapshot core = current.iceCore();
        boolean frozeNow = false;
        Optional<ShatterEvent> shatter = Optional.empty();

        if (!frozenBeforeHit
                && validIceHit
                && cold + EPSILON >= hit.profile().freezeThreshold()
                && hit.occurredAtMillis() >= current.refreezeImmuneUntilMillis()) {
            frozen = true;
            frozeNow = true;
            freezeGeneration = Math.addExact(freezeGeneration, 1L);
            core = IceCoreSnapshot.from(contributions, cold);
        }

        if (frozenBeforeHit
                && hit.shatterTagged()
                && hit.origin().canTriggerShatter()
                && current.freezeGeneration() > current.lastShatteredGeneration()) {
            shatter = Optional.of(shatterEvent(hit, current.iceCore()));
            frozen = false;
            lastShatteredGeneration = current.freezeGeneration();
            double beforeCold = cold;
            cold = hit.profile().freezeThreshold() * policy.residualColdFraction();
            contributions = scaled(contributions, ratio(cold, beforeCold));
            core = IceCoreSnapshot.EMPTY;
        }

        long immunityUntil = shatter.isPresent()
                ? safeAdd(hit.occurredAtMillis(),
                policy.refreezeImmunityMillis(hit.profile().category()))
                : current.refreezeImmuneUntilMillis();
        StateSnapshot updated = new StateSnapshot(
                hit.targetId(), hit.profile(), cold, frozen,
                freezeGeneration, lastShatteredGeneration,
                immunityUntil, hit.occurredAtMillis(), core, contributions,
                stageFor(cold, frozen, hit.profile()));
        states.put(hit.targetId(), updated);
        return new HitResult(
                true, CapacityReason.NONE, updated, directMultiplier,
                frozeNow, shatter);
    }

    /** Time alone never freezes a full gauge after immunity expires. */
    public synchronized Optional<StateSnapshot> state(
            String targetId,
            long nowMillis
    ) {
        requireTargetId(targetId);
        requireTime(nowMillis, "nowMillis");
        StateSnapshot state = states.get(targetId);
        if (state == null) return Optional.empty();
        requireMonotonic(nowMillis, state.lastUpdatedAtMillis());
        return Optional.of(state);
    }

    public synchronized int removeInactiveBefore(long cutoffMillis) {
        requireTime(cutoffMillis, "cutoffMillis");
        int before = states.size();
        states.entrySet().removeIf(entry ->
                entry.getValue().lastUpdatedAtMillis() < cutoffMillis);
        return before - states.size();
    }

    public synchronized Map<String, StateSnapshot> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }

    public synchronized void clear() {
        states.clear();
    }

    public double damageMultiplier(boolean targetFrozen, DamageOrigin origin) {
        Objects.requireNonNull(origin, "origin");
        return targetFrozen && origin.receivesFrozenDirectBonus()
                ? policy.frozenDirectDamageMultiplier() : 1.0;
    }

    private ShatterEvent shatterEvent(Hit hit, IceCoreSnapshot core) {
        double rawImpact = saturatedMultiply(
                hit.preCriticalDirectDamage(), policy.shatterImpactMultiplier());
        double rawCore = saturatedMultiply(
                core.effectiveIceValue(), policy.iceCoreMultiplier());
        double frozenMultiplier = policy.frozenDirectDamageMultiplier();
        double physicalCore = rawCore * core.physicalFraction();
        double magicalCore = rawCore - physicalCore;
        double physicalImpact = hit.school() == ElementAttackSchool.PHYSICAL
                ? rawImpact : 0.0;
        double magicalImpact = hit.school() == ElementAttackSchool.MAGICAL
                ? rawImpact : 0.0;
        return new ShatterEvent(
                hit.targetId(), hit.contributorId(),
                saturatedMultiply(rawImpact, frozenMultiplier),
                saturatedMultiply(rawCore, frozenMultiplier),
                saturatedMultiply(saturatedAdd(
                        physicalImpact, physicalCore), frozenMultiplier),
                saturatedMultiply(saturatedAdd(
                        magicalImpact, magicalCore), frozenMultiplier),
                false, true, frozenMultiplier,
                core.contributionShares());
    }

    private static StateSnapshot empty(Hit hit) {
        return new StateSnapshot(
                hit.targetId(), hit.profile(), 0.0, false,
                0L, 0L, 0L, hit.occurredAtMillis(),
                IceCoreSnapshot.EMPTY, Map.of(), Stage.NONE);
    }

    private static Stage stageFor(
            double cold,
            boolean frozen,
            TargetProfile profile
    ) {
        if (frozen) return Stage.FROZEN;
        double fraction = cold / profile.freezeThreshold();
        if (fraction + EPSILON >= profile.coldIIThresholdFraction()) {
            return Stage.COLD_II;
        }
        if (fraction + EPSILON >= profile.coldIThresholdFraction()) {
            return Stage.COLD_I;
        }
        return Stage.NONE;
    }

    private static LinkedHashMap<UUID, Contribution> scaled(
            Map<UUID, Contribution> source,
            double factor
    ) {
        LinkedHashMap<UUID, Contribution> result = new LinkedHashMap<>();
        if (factor <= EPSILON) return result;
        source.forEach((key, value) -> {
            Contribution scaled = value.scale(factor);
            if (scaled.totalColdValue() > EPSILON) result.put(key, scaled);
        });
        return result;
    }

    private static double ratio(double numerator, double denominator) {
        return denominator <= EPSILON ? 0.0
                : Math.clamp(numerator / denominator, 0.0, 1.0);
    }

    private static void requireCompatible(TargetProfile left, TargetProfile right) {
        if (!left.equals(right)) {
            throw new IllegalArgumentException("target profile changed while cold state is active");
        }
    }

    private static void requireMonotonic(long current, long previous) {
        if (current < previous) {
            throw new IllegalArgumentException("time must be monotonic per target");
        }
    }

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static double saturatedAdd(double left, double right) {
        double result = left + right;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    private static double saturatedMultiply(double left, double right) {
        double result = left * right;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    private static double saturatedDivide(double numerator, double denominator) {
        double result = numerator / denominator;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    private static void requireTargetId(String targetId) {
        if (targetId == null || targetId.isBlank() || targetId.length() > 128) {
            throw new IllegalArgumentException("targetId must be 1..128 non-blank characters");
        }
    }

    private static void requireTime(long value, String name) {
        if (value < 0L) throw new IllegalArgumentException(name + " must be non-negative");
    }

    private static double finiteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    public record Policy(
            double frozenDirectDamageMultiplier,
            double shatterImpactMultiplier,
            double iceCoreMultiplier,
            double residualColdFraction,
            long normalRefreezeImmunityMillis,
            long eliteRefreezeImmunityMillis,
            long minibossRefreezeImmunityMillis,
            long bossRefreezeImmunityMillis,
            int maximumTargets,
            int maximumContributorsPerTarget
    ) {
        public Policy {
            if (!Double.isFinite(frozenDirectDamageMultiplier)
                    || frozenDirectDamageMultiplier < 1.0
                    || !Double.isFinite(shatterImpactMultiplier)
                    || shatterImpactMultiplier < 0.0
                    || !Double.isFinite(iceCoreMultiplier)
                    || iceCoreMultiplier < 0.0
                    || !Double.isFinite(residualColdFraction)
                    || residualColdFraction < 0.0
                    || residualColdFraction > 1.0) {
                throw new IllegalArgumentException("invalid ice multiplier policy");
            }
            if (normalRefreezeImmunityMillis < 0L
                    || eliteRefreezeImmunityMillis < 0L
                    || minibossRefreezeImmunityMillis < 0L
                    || bossRefreezeImmunityMillis < 0L) {
                throw new IllegalArgumentException("immunity durations must be non-negative");
            }
            if (maximumTargets < 1 || maximumContributorsPerTarget < 1) {
                throw new IllegalArgumentException("ice capacity limits must be positive");
            }
        }

        public static Policy waveOne(int maximumTargets, int maximumContributors) {
            return new Policy(
                    1.08, 1.25, .5, .4,
                    3_000L, 4_000L, 5_000L, 8_000L,
                    maximumTargets, maximumContributors);
        }

        public long refreezeImmunityMillis(ElementTargetCategory category) {
            return switch (category) {
                case NORMAL -> normalRefreezeImmunityMillis;
                case ELITE -> eliteRefreezeImmunityMillis;
                case MINIBOSS -> minibossRefreezeImmunityMillis;
                case BOSS -> bossRefreezeImmunityMillis;
            };
        }
    }

    /** Stage fractions are required inputs because their Beta values are undecided. */
    public record TargetProfile(
            ElementTargetCategory category,
            double freezeThreshold,
            double coldIThresholdFraction,
            double coldIIThresholdFraction
    ) {
        public TargetProfile {
            category = Objects.requireNonNull(category, "category");
            if (!Double.isFinite(freezeThreshold) || freezeThreshold <= 0.0) {
                throw new IllegalArgumentException("freezeThreshold must be positive and finite");
            }
            if (!Double.isFinite(coldIThresholdFraction)
                    || !Double.isFinite(coldIIThresholdFraction)
                    || coldIThresholdFraction <= 0.0
                    || coldIThresholdFraction >= coldIIThresholdFraction
                    || coldIIThresholdFraction >= 1.0) {
                throw new IllegalArgumentException(
                        "stage fractions must satisfy 0 < cold I < cold II < 1");
            }
        }
    }

    public record Hit(
            String targetId,
            TargetProfile profile,
            UUID contributorId,
            ElementAttackSchool school,
            DamageOrigin origin,
            double coldValue,
            double iceAttributeValue,
            boolean shatterTagged,
            double preCriticalDirectDamage,
            long occurredAtMillis
    ) {
        public Hit {
            requireTargetId(targetId);
            profile = Objects.requireNonNull(profile, "profile");
            contributorId = Objects.requireNonNull(contributorId, "contributorId");
            school = Objects.requireNonNull(school, "school");
            origin = Objects.requireNonNull(origin, "origin");
            coldValue = finiteNonNegative(coldValue, "coldValue");
            iceAttributeValue = finiteNonNegative(iceAttributeValue, "iceAttributeValue");
            preCriticalDirectDamage = finiteNonNegative(
                    preCriticalDirectDamage, "preCriticalDirectDamage");
            requireTime(occurredAtMillis, "occurredAtMillis");
        }
    }

    public enum DamageOrigin {
        NORMAL_ATTACK_DIRECT(true, true, true),
        SKILL_DIRECT(true, true, true),
        SHATTER_ADDITIONAL(true, false, false),
        DAMAGE_OVER_TIME(false, false, false),
        PERIODIC(false, false, false),
        AUTOMATIC_SECONDARY(false, false, false),
        REFLECTED(false, false, false);

        private final boolean receivesFrozenDirectBonus;
        private final boolean acceptsColdInput;
        private final boolean canTriggerShatter;

        DamageOrigin(
                boolean receivesFrozenDirectBonus,
                boolean acceptsColdInput,
                boolean canTriggerShatter
        ) {
            this.receivesFrozenDirectBonus = receivesFrozenDirectBonus;
            this.acceptsColdInput = acceptsColdInput;
            this.canTriggerShatter = canTriggerShatter;
        }

        public boolean receivesFrozenDirectBonus() {
            return receivesFrozenDirectBonus;
        }

        public boolean acceptsColdInput() {
            return acceptsColdInput;
        }

        public boolean canTriggerShatter() {
            return canTriggerShatter;
        }
    }

    public enum Stage {
        NONE,
        COLD_I,
        COLD_II,
        FROZEN
    }

    public record Contribution(
            double physicalColdValue,
            double magicalColdValue,
            double weightedIceValue
    ) {
        private static final Contribution ZERO = new Contribution(0, 0, 0);

        public Contribution {
            physicalColdValue = finiteNonNegative(physicalColdValue, "physicalColdValue");
            magicalColdValue = finiteNonNegative(magicalColdValue, "magicalColdValue");
            weightedIceValue = finiteNonNegative(weightedIceValue, "weightedIceValue");
        }

        public double totalColdValue() {
            return saturatedAdd(physicalColdValue, magicalColdValue);
        }

        private Contribution add(
                ElementAttackSchool school,
                double cold,
                double iceAttribute
        ) {
            return new Contribution(
                    saturatedAdd(physicalColdValue,
                            school == ElementAttackSchool.PHYSICAL ? cold : 0.0),
                    saturatedAdd(magicalColdValue,
                            school == ElementAttackSchool.MAGICAL ? cold : 0.0),
                    saturatedAdd(weightedIceValue,
                            saturatedMultiply(cold, iceAttribute)));
        }

        private Contribution scale(double factor) {
            return new Contribution(
                    saturatedMultiply(physicalColdValue, factor),
                    saturatedMultiply(magicalColdValue, factor),
                    saturatedMultiply(weightedIceValue, factor));
        }
    }

    public record ContributionShare(
            double totalFraction,
            double physicalFraction,
            double magicalFraction
    ) {
        public ContributionShare {
            totalFraction = Math.clamp(finiteNonNegative(totalFraction, "totalFraction"), 0, 1);
            physicalFraction = Math.clamp(finiteNonNegative(physicalFraction, "physicalFraction"), 0, 1);
            magicalFraction = Math.clamp(finiteNonNegative(magicalFraction, "magicalFraction"), 0, 1);
        }
    }

    public record IceCoreSnapshot(
            double effectiveIceValue,
            double physicalFraction,
            Map<UUID, ContributionShare> contributionShares
    ) {
        private static final IceCoreSnapshot EMPTY =
                new IceCoreSnapshot(0.0, 0.0, Map.of());

        public IceCoreSnapshot {
            effectiveIceValue = finiteNonNegative(effectiveIceValue, "effectiveIceValue");
            physicalFraction = Math.clamp(
                    finiteNonNegative(physicalFraction, "physicalFraction"), 0, 1);
            contributionShares = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(contributionShares, "contributionShares")));
            contributionShares.forEach((playerId, share) -> {
                Objects.requireNonNull(playerId, "contribution share playerId");
                Objects.requireNonNull(share, "contribution share");
            });
        }

        private static IceCoreSnapshot from(
                Map<UUID, Contribution> contributions,
                double totalCold
        ) {
            if (totalCold <= EPSILON) return EMPTY;
            double weightedIce = contributions.values().stream()
                    .mapToDouble(Contribution::weightedIceValue).sum();
            double physicalCold = contributions.values().stream()
                    .mapToDouble(Contribution::physicalColdValue).sum();
            LinkedHashMap<UUID, ContributionShare> shares = new LinkedHashMap<>();
            contributions.forEach((playerId, value) -> shares.put(playerId,
                    new ContributionShare(
                            value.totalColdValue() / totalCold,
                            value.physicalColdValue() / totalCold,
                            value.magicalColdValue() / totalCold)));
            return new IceCoreSnapshot(
                    saturatedDivide(weightedIce, totalCold),
                    physicalCold / totalCold,
                    shares);
        }
    }

    public record StateSnapshot(
            String targetId,
            TargetProfile profile,
            double coldValue,
            boolean frozen,
            long freezeGeneration,
            long lastShatteredGeneration,
            long refreezeImmuneUntilMillis,
            long lastUpdatedAtMillis,
            IceCoreSnapshot iceCore,
            Map<UUID, Contribution> contributions,
            Stage stage
    ) {
        public StateSnapshot {
            requireTargetId(targetId);
            profile = Objects.requireNonNull(profile, "profile");
            coldValue = finiteNonNegative(coldValue, "coldValue");
            if (coldValue > profile.freezeThreshold() + EPSILON) {
                throw new IllegalArgumentException("coldValue exceeds freeze threshold");
            }
            if (freezeGeneration < 0 || lastShatteredGeneration < 0
                    || lastShatteredGeneration > freezeGeneration) {
                throw new IllegalArgumentException("invalid freeze generation");
            }
            requireTime(refreezeImmuneUntilMillis, "refreezeImmuneUntilMillis");
            requireTime(lastUpdatedAtMillis, "lastUpdatedAtMillis");
            iceCore = Objects.requireNonNull(iceCore, "iceCore");
            contributions = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(contributions, "contributions")));
            contributions.forEach((playerId, contribution) -> {
                Objects.requireNonNull(playerId, "contribution playerId");
                Objects.requireNonNull(contribution, "contribution");
            });
            double contributed = contributions.values().stream()
                    .mapToDouble(Contribution::totalColdValue).sum();
            if (!approximatelyEqual(coldValue, contributed)) {
                throw new IllegalArgumentException(
                        "contributions must equal represented cold state");
            }
            if (frozen && coldValue + EPSILON < profile.freezeThreshold()) {
                throw new IllegalArgumentException(
                        "frozen state requires a full cold gauge");
            }
            stage = Objects.requireNonNull(stage, "stage");
        }
    }

    public record ShatterEvent(
            String targetId,
            UUID triggeringPlayerId,
            double impactDamage,
            double coreDamage,
            double physicalAdditionalDamage,
            double magicalAdditionalDamage,
            boolean criticalAllowed,
            boolean singleTarget,
            double frozenDamageMultiplier,
            Map<UUID, ContributionShare> coreContributionShares
    ) {
        public ShatterEvent {
            requireTargetId(targetId);
            triggeringPlayerId = Objects.requireNonNull(
                    triggeringPlayerId, "triggeringPlayerId");
            impactDamage = finiteNonNegative(impactDamage, "impactDamage");
            coreDamage = finiteNonNegative(coreDamage, "coreDamage");
            physicalAdditionalDamage = finiteNonNegative(
                    physicalAdditionalDamage, "physicalAdditionalDamage");
            magicalAdditionalDamage = finiteNonNegative(
                    magicalAdditionalDamage, "magicalAdditionalDamage");
            frozenDamageMultiplier = finiteNonNegative(
                    frozenDamageMultiplier, "frozenDamageMultiplier");
            coreContributionShares = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(coreContributionShares, "coreContributionShares")));
            coreContributionShares.forEach((playerId, share) -> {
                Objects.requireNonNull(playerId, "core contribution playerId");
                Objects.requireNonNull(share, "core contribution share");
            });
        }

        public double totalAdditionalDamage() {
            return saturatedAdd(
                    physicalAdditionalDamage, magicalAdditionalDamage);
        }
    }

    public enum CapacityReason {
        NONE,
        TARGETS,
        CONTRIBUTORS
    }

    public record HitResult(
            boolean accepted,
            CapacityReason capacityReason,
            StateSnapshot state,
            double directDamageMultiplier,
            boolean frozeNow,
            Optional<ShatterEvent> shatter
    ) {
        public HitResult {
            capacityReason = Objects.requireNonNull(capacityReason, "capacityReason");
            state = Objects.requireNonNull(state, "state");
            if (!Double.isFinite(directDamageMultiplier)
                    || directDamageMultiplier < 1.0) {
                throw new IllegalArgumentException(
                        "directDamageMultiplier must be finite and at least 1");
            }
            shatter = Objects.requireNonNull(shatter, "shatter");
        }

        private static HitResult capacityRejected(
                StateSnapshot state,
                CapacityReason reason
        ) {
            return new HitResult(
                    false, reason, state, 1.0, false, Optional.empty());
        }
    }

    private static boolean approximatelyEqual(double left, double right) {
        if (!Double.isFinite(left) || !Double.isFinite(right)) return left == right;
        double scale = Math.max(1.0, Math.max(Math.abs(left), Math.abs(right)));
        return Math.abs(left - right) <= EPSILON * scale;
    }
}
