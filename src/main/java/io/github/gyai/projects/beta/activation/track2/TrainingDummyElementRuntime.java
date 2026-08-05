package io.github.gyai.projects.beta.activation.track2;

import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.beta.activation.BetaActivationTarget;
import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.combat.damage.DamageElement;
import io.github.gyai.projects.combat.damage.ElementProfile;
import io.github.gyai.projects.combat.element.ElementAttackSchool;
import io.github.gyai.projects.combat.element.fire.FireElementEngine;
import io.github.gyai.projects.combat.element.ice.IceElementEngine;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Staging-only Fire/Ice observer and secondary-damage coordinator.
 * It never applies the direct legacy hit and therefore cannot double it.
 */
public final class TrainingDummyElementRuntime implements AutoCloseable {
    public static final double FIRE_INPUT = 25.0;
    public static final double FIRE_ATTRIBUTE = 10.0;
    public static final double ICE_INPUT = 25.0;
    public static final int MAXIMUM_NEARBY_DUMMIES = 64;
    public static final int MAXIMUM_DIAGNOSTICS = 64;

    private final TrainingDummyElementBoundary boundary;
    private final Clock clock;
    private final ElementStateRegistry registry = new ElementStateRegistry();
    private final ArrayList<String> diagnostics = new ArrayList<>();
    private TrainingDummyElementBoundary.Cancellable cleanupTask;
    private BetaActivationPolicy activationPolicy = BetaActivationPolicy.defaults();
    private boolean running;
    private boolean closed;

    public TrainingDummyElementRuntime(TrainingDummyElementBoundary boundary, Clock clock) {
        this.boundary = Objects.requireNonNull(boundary, "boundary");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized boolean start() {
        if (closed) return false;
        if (running) return true;
        if (!activationPolicy.allowsTarget(BetaActivationTarget.TRAINING_DUMMY)) return false;
        cleanupTask = Objects.requireNonNull(
                boundary.scheduleCleanup(this::cleanupExpired, 1_000L), "cleanup task");
        running = true;
        return true;
    }

    public synchronized AttackOutcome observe(AttackInput input) {
        Objects.requireNonNull(input, "input");
        if (!running || closed || input.playerTarget()
                || !activationPolicy.allowsAudience(
                input.attackerId(), input.compatibleClient())
                || !activationPolicy.allowsWorld(input.worldName())
                || !activationPolicy.allowsTarget(BetaActivationTarget.TRAINING_DUMMY)
                || !input.trainingDummyTarget()
                || !boundary.isLiveTrainingDummy(input.targetId())
                || !supported(input)) {
            return AttackOutcome.unchanged(input.metadata());
        }
        long now = input.occurredAtMillis();
        String hitKey = input.hitId() + ":" + input.targetId();
        if (!registry.firstHit(hitKey, now)) {
            return AttackOutcome.duplicate(input.metadata());
        }
        registry.recordParticipation(input.hitId(), input.attackerId(),
                input.targetId(), input.attackId(), now);
        StagingElementProfile profile = registry.playerProfile(input.attackerId());
        if (profile == StagingElementProfile.NONE) {
            return AttackOutcome.unchanged(input.metadata());
        }
        int targetRuntimeId = boundary.targetRuntimeId(input.targetId());
        ElementStateRegistry.TargetState target = registry.targetState(
                input.targetId(), targetRuntimeId, now);
        if (target == null) {
            diagnostic("target capacity reached");
            return AttackOutcome.unchanged(input.metadata());
        }
        return profile == StagingElementProfile.FIRE
                ? applyFire(input, target)
                : applyIce(input, target);
    }

    public synchronized boolean setProfile(UUID playerId, StagingElementProfile profile) {
        return !closed && registry.setProfile(playerId, profile);
    }

    synchronized boolean configure(BetaActivationPolicy policy) {
        if (running || closed || policy == null) return false;
        activationPolicy = policy;
        return true;
    }

    public synchronized void playerLoggedOut(UUID playerId) {
        registry.removePlayer(playerId);
    }

    public synchronized void clearPlayerProfiles() {
        registry.clearPlayerProfiles();
    }

    /** Chunk unload, entity removal, and dummy replacement use the same UUID-only cleanup. */
    public synchronized void targetRemoved(UUID targetId) {
        registry.removeTarget(targetId);
    }

    public ElementRuntimeSnapshotPort snapshots() {
        return registry;
    }

    public TrainingDummyParticipationPort participation() {
        return registry;
    }

    public synchronized List<String> diagnostics() {
        return List.copyOf(diagnostics);
    }

    public synchronized boolean running() {
        return running;
    }

    public synchronized int profileCount() {
        return registry.profileCount();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        running = false;
        if (cleanupTask != null) {
            try {
                cleanupTask.cancel();
            } catch (RuntimeException exception) {
                diagnostic("cleanup cancellation failed");
            }
        }
        cleanupTask = null;
        registry.clear();
    }

    private AttackOutcome applyFire(AttackInput input, ElementStateRegistry.TargetState target) {
        AttackMetadata metadata = compose(input.metadata(), AttackTag.FIRE, false);
        FireElementEngine.HitResult result = target.fire.apply(new FireElementEngine.Hit(
                input.targetId().toString(), ElementStateRegistry.FIRE_DUMMY,
                input.attackerId(), ElementAttackSchool.PHYSICAL,
                FIRE_INPUT, FIRE_ATTRIBUTE, input.occurredAtMillis()));
        boolean detonated = result.detonation().isPresent();
        registry.changed(target, input.occurredAtMillis(), detonated);
        int secondaryApplications = 0;
        if (result.detonation().isPresent()) {
            FireElementEngine.DetonationEvent event = result.detonation().orElseThrow();
            secondaryApplications = applyFireDetonation(input, event, metadata);
        }
        publishVisual(input, StagingElementProfile.FIRE, result.state().stacks(),
                detonated, "Fire " + result.state().stacks() + " / 10");
        return new AttackOutcome(metadata, 1.0, false, false,
                secondaryApplications, result.accepted());
    }

    private AttackOutcome applyIce(AttackInput input, ElementStateRegistry.TargetState target) {
        IceElementEngine.StateSnapshot before = target.ice.snapshot().get(input.targetId().toString());
        boolean shatter = input.attackType() == AttackType.SPIN_SLASH
                && before != null && before.frozen();
        AttackMetadata metadata = compose(input.metadata(), AttackTag.ICE, shatter);
        IceElementEngine.HitResult result = target.ice.apply(new IceElementEngine.Hit(
                input.targetId().toString(), ElementStateRegistry.ICE_DUMMY,
                input.attackerId(), ElementAttackSchool.PHYSICAL,
                input.origin(), ICE_INPUT, 0.0, shatter,
                input.preCriticalDirectDamage(), input.occurredAtMillis()));
        registry.changed(target, input.occurredAtMillis(), false);
        if (result.frozeNow()) target.frozenSinceMillis = input.occurredAtMillis();
        int secondaryApplications = 0;
        if (result.shatter().isPresent()) {
            target.frozenSinceMillis = -1L;
            IceElementEngine.ShatterEvent event = result.shatter().orElseThrow();
            secondaryApplications = applySecondary(new TrainingDummyElementBoundary.SecondaryDamage(
                    input.hitId() + ":shatter", input.attackerId(), input.targetId(),
                    event.totalAdditionalDamage(), IceElementEngine.DamageOrigin.SHATTER_ADDITIONAL,
                    metadata, false)) ? 1 : 0;
        }
        publishVisual(input, StagingElementProfile.ICE, 0, false,
                "stage=" + result.state().stage());
        return new AttackOutcome(metadata, result.directDamageMultiplier(),
                result.frozeNow(), result.shatter().isPresent(),
                secondaryApplications, result.accepted());
    }

    private int applyFireDetonation(
            AttackInput input,
            FireElementEngine.DetonationEvent event,
            AttackMetadata metadata
    ) {
        LinkedHashSet<UUID> targets = new LinkedHashSet<>();
        targets.add(input.targetId());
        try {
            List<UUID> nearby = boundary.nearbyTrainingDummies(
                    input.targetId(), event.radius(), MAXIMUM_NEARBY_DUMMIES);
            if (nearby != null) targets.addAll(nearby);
        } catch (RuntimeException exception) {
            diagnostic("nearby dummy lookup failed");
        }
        int applied = 0;
        int examined = 0;
        for (UUID targetId : targets) {
            if (targetId == null || examined++ >= MAXIMUM_NEARBY_DUMMIES
                    || !boundary.isLiveTrainingDummy(targetId)) continue;
            double amount = targetId.equals(input.targetId())
                    ? event.centerDamage() : event.nearbyDamage();
            if (applySecondary(new TrainingDummyElementBoundary.SecondaryDamage(
                    input.hitId() + ":detonation:" + targetId,
                    input.attackerId(), targetId, amount,
                    IceElementEngine.DamageOrigin.AUTOMATIC_SECONDARY,
                    metadata, false))) applied++;
        }
        return applied;
    }

    private boolean applySecondary(TrainingDummyElementBoundary.SecondaryDamage damage) {
        try {
            boundary.applySecondaryDamage(damage);
            return true;
        } catch (RuntimeException exception) {
            diagnostic("secondary damage boundary failed");
            return false;
        }
    }

    private void publishVisual(AttackInput input, StagingElementProfile profile,
                               int fireStacks, boolean detonationPulse, String state) {
        if (!input.debugViewerAllowedAndNear()) return;
        try {
            boundary.publishVisual(new TrainingDummyElementBoundary.VisualEvent(
                    input.targetId(), profile, fireStacks, input.compatibleClient(),
                    detonationPulse, state, input.occurredAtMillis()));
        } catch (RuntimeException exception) {
            diagnostic("visual fallback failed");
        }
    }

    private synchronized void cleanupExpired() {
        if (!running || closed) return;
        try {
            for (UUID targetId : registry.targets().keySet()) {
                if (!boundary.isLiveTrainingDummy(targetId)) registry.removeTarget(targetId);
            }
            registry.cleanup(clock.millis());
        } catch (RuntimeException exception) {
            diagnostic("element cleanup failed");
        }
    }

    private synchronized void diagnostic(String value) {
        if (diagnostics.size() >= MAXIMUM_DIAGNOSTICS) diagnostics.remove(0);
        diagnostics.add(value.length() <= 160 ? value : value.substring(0, 160));
    }

    private static boolean supported(AttackInput input) {
        if (input.origin() != IceElementEngine.DamageOrigin.NORMAL_ATTACK_DIRECT
                && input.origin() != IceElementEngine.DamageOrigin.SKILL_DIRECT) return false;
        if (!input.metadata().elements().equals(ElementProfile.EMPTY)) return false;
        return switch (input.attackType()) {
            case STARTER_SWORD_NORMAL -> "starter_sword".equals(input.attackId())
                    && input.origin() == IceElementEngine.DamageOrigin.NORMAL_ATTACK_DIRECT
                    && input.metadata().tags().equals(Set.of(
                    AttackTag.NORMAL_ATTACK, AttackTag.MELEE, AttackTag.PHYSICAL));
            case SPIN_SLASH -> "spin_slash".equals(input.attackId())
                    && input.origin() == IceElementEngine.DamageOrigin.SKILL_DIRECT
                    && input.metadata().tags().equals(Set.of(
                    AttackTag.SKILL, AttackTag.MELEE, AttackTag.PHYSICAL));
            case OTHER -> false;
        };
    }

    private static AttackMetadata compose(
            AttackMetadata source, AttackTag elementTag, boolean shatter
    ) {
        EnumSet<AttackTag> tags = source.tags().isEmpty()
                ? EnumSet.noneOf(AttackTag.class) : EnumSet.copyOf(source.tags());
        tags.add(elementTag);
        if (shatter) tags.add(AttackTag.SHATTER);
        EnumMap<DamageElement, Double> values = new EnumMap<>(DamageElement.class);
        values.putAll(source.elements().values());
        if (elementTag == AttackTag.FIRE) values.put(DamageElement.FIRE, FIRE_ATTRIBUTE);
        else values.putIfAbsent(DamageElement.ICE, 0.0);
        ElementProfile elements = new ElementProfile(values, source.elements().scalingRates());
        return new AttackMetadata(tags, elements);
    }

    public enum AttackType {
        STARTER_SWORD_NORMAL,
        SPIN_SLASH,
        OTHER
    }

    public record AttackInput(
            String hitId,
            UUID attackerId,
            UUID targetId,
            String attackId,
            AttackType attackType,
            IceElementEngine.DamageOrigin origin,
            AttackMetadata metadata,
            double preCriticalDirectDamage,
            boolean legacyCritical,
            boolean trainingDummyTarget,
            boolean playerTarget,
            boolean compatibleClient,
            String worldName,
            boolean debugViewerAllowedAndNear,
            long occurredAtMillis
    ) {
        public AttackInput {
            if (hitId == null || hitId.isBlank() || hitId.length() > 128
                    || attackerId == null || targetId == null || attackId == null
                    || attackId.isBlank() || attackId.length() > 64 || attackType == null
                    || origin == null || metadata == null
                    || !Double.isFinite(preCriticalDirectDamage)
                    || preCriticalDirectDamage < 0 || worldName == null
                    || worldName.isBlank() || worldName.length() > 64
                    || occurredAtMillis < 0) {
                throw new IllegalArgumentException("Invalid element attack input");
            }
        }
    }

    public record AttackOutcome(
            AttackMetadata metadata,
            double directDamageMultiplier,
            boolean frozeNow,
            boolean shattered,
            int secondaryApplications,
            boolean observed
    ) {
        public AttackOutcome {
            if (metadata == null || !Double.isFinite(directDamageMultiplier)
                    || directDamageMultiplier < 1 || secondaryApplications < 0) {
                throw new IllegalArgumentException("Invalid attack outcome");
            }
        }

        static AttackOutcome unchanged(AttackMetadata metadata) {
            return new AttackOutcome(metadata, 1.0, false, false, 0, false);
        }

        static AttackOutcome duplicate(AttackMetadata metadata) {
            return unchanged(metadata);
        }
    }
}
