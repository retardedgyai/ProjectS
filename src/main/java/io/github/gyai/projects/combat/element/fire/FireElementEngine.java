package io.github.gyai.projects.combat.element.fire;

import io.github.gyai.projects.combat.element.ElementAttackSchool;
import io.github.gyai.projects.combat.element.ElementTargetCategory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded pure state machine for shared fire accumulation and detonation. */
public final class FireElementEngine {
    private static final double EPSILON = 1.0e-9;

    private final Policy policy;
    private final LinkedHashMap<String, StateSnapshot> states = new LinkedHashMap<>();

    public FireElementEngine(Policy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public synchronized HitResult apply(Hit hit) {
        Objects.requireNonNull(hit, "hit");
        StateSnapshot current = states.get(hit.targetId());
        if (current == null && states.size() >= policy.maximumTargets()) {
            return HitResult.capacityRejected(empty(hit), CapacityReason.TARGETS);
        }
        if (current == null) {
            current = empty(hit);
        } else {
            requireCompatible(current.profile(), hit.profile());
            requireMonotonic(hit.occurredAtMillis(), current.lastUpdatedAtMillis());
        }

        LinkedHashMap<UUID, Contribution> contributions =
                new LinkedHashMap<>(current.contributions());
        if (hit.burnValue() > 0.0
                && !contributions.containsKey(hit.contributorId())
                && contributions.size() >= policy.maximumContributorsPerTarget()) {
            return HitResult.capacityRejected(current, CapacityReason.CONTRIBUTORS);
        }

        double threshold = hit.profile().stackThreshold();
        if (threshold > Double.MAX_VALUE / policy.maximumStacks()) {
            throw new IllegalArgumentException(
                    "stackThreshold is too large for the configured maximum stacks");
        }
        double beforeTotal = representedBurn(current, threshold);
        double rawTotal = saturatedAdd(beforeTotal, hit.burnValue());
        double stackQuotient = rawTotal / threshold;
        int rawStacks = finiteFloor(stackQuotient);
        double fractional = stackQuotient >= Integer.MAX_VALUE
                ? 0.0 : rawTotal - rawStacks * threshold;
        if (!Double.isFinite(fractional)) fractional = 0.0;
        fractional = Math.clamp(fractional, 0.0, Math.nextDown(threshold));
        int boundedStacks = Math.min(policy.maximumStacks(), rawStacks);
        double acceptedTotal = saturatedAdd(
                boundedStacks * threshold, fractional);
        double acceptedBurn = Math.max(0.0, acceptedTotal - beforeTotal);

        if (acceptedBurn > EPSILON) {
            Contribution prior = contributions.getOrDefault(
                    hit.contributorId(), Contribution.ZERO);
            contributions.put(hit.contributorId(), prior.add(
                    hit.school(), acceptedBurn, hit.fireAttributeValue()));
        }

        Optional<DetonationEvent> detonation = Optional.empty();
        int finalStacks = boundedStacks;
        double preConsumptionTotal = acceptedTotal;
        if (current.stacks() < policy.detonationStackThreshold()
                && boundedStacks >= policy.detonationStackThreshold()) {
            detonation = Optional.of(detonationEvent(
                    hit.targetId(), contributions, preConsumptionTotal));
            finalStacks = Math.max(0,
                    boundedStacks - policy.stacksConsumedPerDetonation());
            double retainedTotal = finalStacks * threshold + fractional;
            contributions = scaled(contributions,
                    ratio(retainedTotal, preConsumptionTotal));
        }

        long nextDecayAt = safeAdd(
                safeAdd(hit.occurredAtMillis(), policy.decayHoldMillis()),
                fractional > EPSILON
                        ? policy.fractionalDecayIntervalMillis()
                        : policy.stackDecayIntervalMillis());
        StateSnapshot updated = new StateSnapshot(
                hit.targetId(), hit.profile(), finalStacks, fractional,
                hit.occurredAtMillis(), hit.occurredAtMillis(), nextDecayAt,
                contributions);
        states.put(hit.targetId(), updated);
        return new HitResult(true, CapacityReason.NONE, updated, detonation);
    }

    public synchronized Optional<StateSnapshot> advanceDecay(
            String targetId,
            long nowMillis
    ) {
        requireTargetId(targetId);
        requireTime(nowMillis, "nowMillis");
        StateSnapshot current = states.get(targetId);
        if (current == null) return Optional.empty();
        requireMonotonic(nowMillis, current.lastUpdatedAtMillis());

        int stacks = current.stacks();
        double fractional = current.fractionalBurnValue();
        long nextDecayAt = current.nextDecayAtMillis();
        LinkedHashMap<UUID, Contribution> contributions =
                new LinkedHashMap<>(current.contributions());
        double threshold = current.profile().stackThreshold();

        while (nextDecayAt <= nowMillis && (stacks > 0 || fractional > EPSILON)) {
            double beforeTotal = stacks * threshold + fractional;
            if (fractional > EPSILON) {
                fractional = Math.max(0.0, fractional
                        - threshold * policy.fractionalDecayFraction());
                long appliedAt = nextDecayAt;
                nextDecayAt = fractional > EPSILON
                        ? appliedAt + policy.fractionalDecayIntervalMillis()
                        : appliedAt + policy.stackDecayIntervalMillis();
            } else {
                stacks--;
                nextDecayAt += policy.stackDecayIntervalMillis();
            }
            double afterTotal = stacks * threshold + fractional;
            contributions = scaled(contributions, ratio(afterTotal, beforeTotal));
        }

        if (stacks == 0 && fractional <= EPSILON) {
            states.remove(targetId);
            return Optional.empty();
        }
        StateSnapshot updated = new StateSnapshot(
                current.targetId(), current.profile(), stacks, fractional,
                current.lastFireInputAtMillis(), nowMillis, nextDecayAt,
                contributions);
        states.put(targetId, updated);
        return Optional.of(updated);
    }

    public synchronized int removeInactiveBefore(long cutoffMillis) {
        requireTime(cutoffMillis, "cutoffMillis");
        int before = states.size();
        states.entrySet().removeIf(entry ->
                entry.getValue().lastUpdatedAtMillis() < cutoffMillis);
        return before - states.size();
    }

    public synchronized Optional<StateSnapshot> state(String targetId) {
        requireTargetId(targetId);
        return Optional.ofNullable(states.get(targetId));
    }

    public synchronized Map<String, StateSnapshot> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }

    public synchronized void clear() {
        states.clear();
    }

    private DetonationEvent detonationEvent(
            String targetId,
            Map<UUID, Contribution> contributions,
            double totalBurn
    ) {
        double weightedFire = contributions.values().stream()
                .mapToDouble(Contribution::weightedFireValue).sum();
        double effectiveFire = totalBurn <= EPSILON
                ? 0.0 : saturatedDivide(weightedFire, totalBurn);
        double baseDamage = saturatedMultiply(
                effectiveFire, policy.detonationFireMultiplier());
        double physicalBurn = contributions.values().stream()
                .mapToDouble(Contribution::physicalBurnValue).sum();
        double physicalFraction = totalBurn <= EPSILON ? 0.0 : physicalBurn / totalBurn;
        physicalFraction = Math.clamp(physicalFraction, 0.0, 1.0);
        LinkedHashMap<UUID, ContributionShare> shares = new LinkedHashMap<>();
        contributions.forEach((playerId, contribution) -> shares.put(
                playerId, ContributionShare.from(contribution, totalBurn)));
        return new DetonationEvent(
                targetId,
                effectiveFire,
                saturatedMultiply(baseDamage, policy.centerDamageMultiplier()),
                saturatedMultiply(baseDamage, policy.nearbyDamageMultiplier()),
                saturatedMultiply(baseDamage, physicalFraction),
                saturatedMultiply(baseDamage, 1.0 - physicalFraction),
                policy.detonationRadius(),
                policy.centerDamageMultiplier(),
                policy.nearbyDamageMultiplier(),
                false,
                shares);
    }

    private static StateSnapshot empty(Hit hit) {
        long nextDecayAt = safeAdd(hit.occurredAtMillis(), 1L);
        return new StateSnapshot(
                hit.targetId(), hit.profile(), 0, 0.0,
                hit.occurredAtMillis(), hit.occurredAtMillis(), nextDecayAt,
                Map.of());
    }

    private static double representedBurn(StateSnapshot state, double threshold) {
        return state.stacks() * threshold + state.fractionalBurnValue();
    }

    private static LinkedHashMap<UUID, Contribution> scaled(
            Map<UUID, Contribution> source,
            double factor
    ) {
        LinkedHashMap<UUID, Contribution> result = new LinkedHashMap<>();
        if (factor <= EPSILON) return result;
        source.forEach((key, value) -> {
            Contribution scaled = value.scale(factor);
            if (scaled.totalBurnValue() > EPSILON) result.put(key, scaled);
        });
        return result;
    }

    private static double ratio(double numerator, double denominator) {
        return denominator <= EPSILON ? 0.0 : Math.clamp(numerator / denominator, 0.0, 1.0);
    }

    private static int finiteFloor(double value) {
        if (!Double.isFinite(value) || value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, (int) Math.floor(value));
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

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static void requireCompatible(TargetProfile left, TargetProfile right) {
        if (!left.equals(right)) {
            throw new IllegalArgumentException("target profile changed while fire state is active");
        }
    }

    private static void requireMonotonic(long current, long previous) {
        if (current < previous) {
            throw new IllegalArgumentException("time must be monotonic per target");
        }
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
            int maximumStacks,
            int detonationStackThreshold,
            int stacksConsumedPerDetonation,
            int maximumDetonationsPerHit,
            double detonationFireMultiplier,
            double detonationRadius,
            double centerDamageMultiplier,
            double nearbyDamageMultiplier,
            long decayHoldMillis,
            double fractionalDecayFraction,
            long fractionalDecayIntervalMillis,
            long stackDecayIntervalMillis,
            int maximumTargets,
            int maximumContributorsPerTarget
    ) {
        public Policy {
            if (maximumStacks < 1
                    || detonationStackThreshold < 1
                    || detonationStackThreshold > maximumStacks
                    || stacksConsumedPerDetonation < 1
                    || stacksConsumedPerDetonation > detonationStackThreshold
                    || maximumDetonationsPerHit != 1) {
                throw new IllegalArgumentException("invalid fire stack policy");
            }
            finiteNonNegative(detonationFireMultiplier, "detonationFireMultiplier");
            finiteNonNegative(detonationRadius, "detonationRadius");
            finiteNonNegative(centerDamageMultiplier, "centerDamageMultiplier");
            finiteNonNegative(nearbyDamageMultiplier, "nearbyDamageMultiplier");
            if (decayHoldMillis < 0L
                    || !Double.isFinite(fractionalDecayFraction)
                    || fractionalDecayFraction <= 0.0
                    || fractionalDecayFraction > 1.0
                    || fractionalDecayIntervalMillis < 1L
                    || stackDecayIntervalMillis < 1L) {
                throw new IllegalArgumentException("invalid fire decay policy");
            }
            if (maximumTargets < 1 || maximumContributorsPerTarget < 1) {
                throw new IllegalArgumentException("fire capacity limits must be positive");
            }
        }

        public static Policy waveOne(int maximumTargets, int maximumContributors) {
            return new Policy(
                    10, 10, 7, 1, 2.5, 4.0, 1.0, .6,
                    5_000L, .25, 1_000L, 2_000L,
                    maximumTargets, maximumContributors);
        }
    }

    public record TargetProfile(
            ElementTargetCategory category,
            double stackThreshold
    ) {
        public TargetProfile {
            category = Objects.requireNonNull(category, "category");
            if (!Double.isFinite(stackThreshold) || stackThreshold <= 0.0) {
                throw new IllegalArgumentException("stackThreshold must be positive and finite");
            }
        }
    }

    public record Hit(
            String targetId,
            TargetProfile profile,
            UUID contributorId,
            ElementAttackSchool school,
            double burnValue,
            double fireAttributeValue,
            long occurredAtMillis
    ) {
        public Hit {
            requireTargetId(targetId);
            profile = Objects.requireNonNull(profile, "profile");
            contributorId = Objects.requireNonNull(contributorId, "contributorId");
            school = Objects.requireNonNull(school, "school");
            burnValue = finiteNonNegative(burnValue, "burnValue");
            fireAttributeValue = finiteNonNegative(
                    fireAttributeValue, "fireAttributeValue");
            requireTime(occurredAtMillis, "occurredAtMillis");
        }
    }

    public record Contribution(
            double physicalBurnValue,
            double magicalBurnValue,
            double weightedFireValue
    ) {
        private static final Contribution ZERO = new Contribution(0, 0, 0);

        public Contribution {
            physicalBurnValue = finiteNonNegative(physicalBurnValue, "physicalBurnValue");
            magicalBurnValue = finiteNonNegative(magicalBurnValue, "magicalBurnValue");
            weightedFireValue = finiteNonNegative(weightedFireValue, "weightedFireValue");
        }

        public double totalBurnValue() {
            return saturatedAdd(physicalBurnValue, magicalBurnValue);
        }

        private Contribution add(
                ElementAttackSchool school,
                double burn,
                double fireAttribute
        ) {
            return new Contribution(
                    saturatedAdd(physicalBurnValue,
                            school == ElementAttackSchool.PHYSICAL ? burn : 0.0),
                    saturatedAdd(magicalBurnValue,
                            school == ElementAttackSchool.MAGICAL ? burn : 0.0),
                    saturatedAdd(weightedFireValue,
                            saturatedMultiply(burn, fireAttribute)));
        }

        private Contribution scale(double factor) {
            return new Contribution(
                    saturatedMultiply(physicalBurnValue, factor),
                    saturatedMultiply(magicalBurnValue, factor),
                    saturatedMultiply(weightedFireValue, factor));
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

        private static ContributionShare from(Contribution contribution, double total) {
            if (total <= EPSILON) return new ContributionShare(0, 0, 0);
            return new ContributionShare(
                    contribution.totalBurnValue() / total,
                    contribution.physicalBurnValue() / total,
                    contribution.magicalBurnValue() / total);
        }
    }

    public record StateSnapshot(
            String targetId,
            TargetProfile profile,
            int stacks,
            double fractionalBurnValue,
            long lastFireInputAtMillis,
            long lastUpdatedAtMillis,
            long nextDecayAtMillis,
            Map<UUID, Contribution> contributions
    ) {
        public StateSnapshot {
            requireTargetId(targetId);
            profile = Objects.requireNonNull(profile, "profile");
            if (stacks < 0) {
                throw new IllegalArgumentException("stacks must be non-negative");
            }
            fractionalBurnValue = finiteNonNegative(
                    fractionalBurnValue, "fractionalBurnValue");
            if (fractionalBurnValue >= profile.stackThreshold()) {
                throw new IllegalArgumentException("fractional burn must be below threshold");
            }
            requireTime(lastFireInputAtMillis, "lastFireInputAtMillis");
            requireTime(lastUpdatedAtMillis, "lastUpdatedAtMillis");
            requireTime(nextDecayAtMillis, "nextDecayAtMillis");
            contributions = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(contributions, "contributions")));
            contributions.forEach((playerId, contribution) -> {
                Objects.requireNonNull(playerId, "contribution playerId");
                Objects.requireNonNull(contribution, "contribution");
            });
            double represented = stacks * profile.stackThreshold()
                    + fractionalBurnValue;
            double contributed = contributions.values().stream()
                    .mapToDouble(Contribution::totalBurnValue).sum();
            if (!approximatelyEqual(represented, contributed)) {
                throw new IllegalArgumentException(
                        "contributions must equal represented fire state");
            }
        }
    }

    public record DetonationEvent(
            String targetId,
            double effectiveFireValue,
            double centerDamage,
            double nearbyDamage,
            double physicalBaseDamage,
            double magicalBaseDamage,
            double radius,
            double centerMultiplier,
            double nearbyMultiplier,
            boolean spreadsBurn,
            Map<UUID, ContributionShare> contributionShares
    ) {
        public DetonationEvent {
            requireTargetId(targetId);
            effectiveFireValue = finiteNonNegative(effectiveFireValue, "effectiveFireValue");
            centerDamage = finiteNonNegative(centerDamage, "centerDamage");
            nearbyDamage = finiteNonNegative(nearbyDamage, "nearbyDamage");
            physicalBaseDamage = finiteNonNegative(physicalBaseDamage, "physicalBaseDamage");
            magicalBaseDamage = finiteNonNegative(magicalBaseDamage, "magicalBaseDamage");
            radius = finiteNonNegative(radius, "radius");
            centerMultiplier = finiteNonNegative(centerMultiplier, "centerMultiplier");
            nearbyMultiplier = finiteNonNegative(nearbyMultiplier, "nearbyMultiplier");
            contributionShares = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(contributionShares, "contributionShares")));
            contributionShares.forEach((playerId, share) -> {
                Objects.requireNonNull(playerId, "contribution share playerId");
                Objects.requireNonNull(share, "contribution share");
            });
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
            Optional<DetonationEvent> detonation
    ) {
        public HitResult {
            capacityReason = Objects.requireNonNull(capacityReason, "capacityReason");
            state = Objects.requireNonNull(state, "state");
            detonation = Objects.requireNonNull(detonation, "detonation");
        }

        private static HitResult capacityRejected(
                StateSnapshot state,
                CapacityReason reason
        ) {
            return new HitResult(false, reason, state, Optional.empty());
        }
    }

    private static boolean approximatelyEqual(double left, double right) {
        if (!Double.isFinite(left) || !Double.isFinite(right)) return left == right;
        double scale = Math.max(1.0, Math.max(Math.abs(left), Math.abs(right)));
        return Math.abs(left - right) <= EPSILON * scale;
    }
}
