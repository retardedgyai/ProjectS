package io.github.gyai.projects.beta.activation.track2;

import io.github.gyai.projects.combat.element.ElementTargetCategory;
import io.github.gyai.projects.combat.element.fire.FireElementEngine;
import io.github.gyai.projects.combat.element.ice.IceElementEngine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bounded UUID-only state. No Bukkit object survives a runtime callback. */
final class ElementStateRegistry implements ElementRuntimeSnapshotPort, TrainingDummyParticipationPort {
    static final int MAXIMUM_PROFILES = 512;
    static final int MAXIMUM_TARGETS = 512;
    static final int MAXIMUM_HIT_KEYS = 2_048;
    static final int MAXIMUM_PARTICIPATION_EVENTS = 256;
    static final long TARGET_TIMEOUT_MILLIS = 300_000L;
    static final long HIT_KEY_TIMEOUT_MILLIS = 10_000L;
    static final long FREEZE_DURATION_MILLIS = 3_000L;
    static final long FIRE_DECAY_HOLD_MILLIS = 5_000L;

    static final FireElementEngine.TargetProfile FIRE_DUMMY =
            new FireElementEngine.TargetProfile(ElementTargetCategory.NORMAL, 25.0);
    static final IceElementEngine.TargetProfile ICE_DUMMY =
            new IceElementEngine.TargetProfile(ElementTargetCategory.NORMAL, 100.0, .25, .5);

    private final LinkedHashMap<UUID, StagingElementProfile> profiles = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, TargetState> targets = new LinkedHashMap<>();
    private final LinkedHashMap<String, Long> hitKeys = new LinkedHashMap<>();
    private final ArrayDeque<ParticipationEvent> participation = new ArrayDeque<>();
    private long nextSequence = 1L;
    private long nextStateRevision = 1L;

    synchronized boolean setProfile(UUID playerId, StagingElementProfile profile) {
        if (playerId == null || profile == null) return false;
        if (profile == StagingElementProfile.NONE) {
            profiles.remove(playerId);
            return true;
        }
        if (!profiles.containsKey(playerId) && profiles.size() >= MAXIMUM_PROFILES) return false;
        profiles.put(playerId, profile);
        return true;
    }

    @Override
    public synchronized StagingElementProfile playerProfile(UUID playerId) {
        return playerId == null ? StagingElementProfile.NONE
                : profiles.getOrDefault(playerId, StagingElementProfile.NONE);
    }

    synchronized TargetState targetState(UUID targetId, int targetRuntimeId, long nowMillis) {
        if (targetId == null || targetRuntimeId < 0) return null;
        TargetState state = targets.get(targetId);
        if (state != null && state.targetRuntimeId != targetRuntimeId) {
            targets.remove(targetId);
            state = null;
        }
        if (state != null && state.frozenSinceMillis >= 0
                && nowMillis - state.frozenSinceMillis >= FREEZE_DURATION_MILLIS) {
            state.resetIce(nowMillis, nextRevision());
        }
        if (state == null && targets.size() < MAXIMUM_TARGETS) {
            state = new TargetState(targetId, targetRuntimeId, nowMillis);
            targets.put(targetId, state);
        }
        return state;
    }

    synchronized boolean firstHit(String key, long nowMillis) {
        cleanupHitKeys(nowMillis);
        if (hitKeys.containsKey(key)) return false;
        if (hitKeys.size() >= MAXIMUM_HIT_KEYS) {
            String oldest = hitKeys.keySet().iterator().next();
            hitKeys.remove(oldest);
        }
        hitKeys.put(key, nowMillis);
        return true;
    }

    synchronized void changed(TargetState state, long nowMillis, boolean detonated) {
        if (state != null) state.changed(nowMillis, detonated, nextRevision());
    }

    synchronized void recordParticipation(
            String hitId, UUID playerId, UUID targetId, String attackId, long nowMillis
    ) {
        if (participation.size() >= MAXIMUM_PARTICIPATION_EVENTS) participation.removeFirst();
        participation.addLast(new ParticipationEvent(
                nextSequence++, hitId, playerId, targetId, attackId, nowMillis));
    }

    @Override
    public synchronized List<ParticipationEvent> after(long sequenceExclusive, int limit) {
        if (sequenceExclusive < 0 || limit < 1 || limit > 128) return List.of();
        ArrayList<ParticipationEvent> result = new ArrayList<>();
        for (ParticipationEvent event : participation) {
            if (event.sequence() > sequenceExclusive) result.add(event);
            if (result.size() == limit) break;
        }
        return List.copyOf(result);
    }

    synchronized void removePlayer(UUID playerId) {
        if (playerId != null) profiles.remove(playerId);
    }

    synchronized void removeTarget(UUID targetId) {
        if (targetId != null) targets.remove(targetId);
    }

    synchronized int cleanup(long nowMillis) {
        cleanupHitKeys(nowMillis);
        for (TargetState state : targets.values()) {
            state.snapshotClockMillis = nowMillis;
            if (state.frozenSinceMillis >= 0
                    && nowMillis - state.frozenSinceMillis >= FREEZE_DURATION_MILLIS) {
                state.resetIce(nowMillis, nextRevision());
            }
            try {
                FireElementEngine.StateSnapshot before = state.fire
                        .state(state.targetId.toString()).orElse(null);
                FireElementEngine.StateSnapshot after = state.fire
                        .advanceDecay(state.targetId.toString(), nowMillis).orElse(null);
                if (!java.util.Objects.equals(before, after)) {
                    state.stateRevision = nextRevision();
                }
            } catch (IllegalArgumentException ignored) {
                // A stale scheduler tick cannot mutate newer callback state.
            }
        }
        int before = targets.size();
        targets.entrySet().removeIf(entry ->
                nowMillis - entry.getValue().lastUpdatedAtMillis >= TARGET_TIMEOUT_MILLIS);
        return before - targets.size();
    }

    synchronized void clear() {
        profiles.clear();
        targets.clear();
        hitKeys.clear();
        participation.clear();
        nextSequence = 1L;
        nextStateRevision = 1L;
    }

    synchronized int profileCount() {
        return profiles.size();
    }

    synchronized int targetCount() {
        return targets.size();
    }

    @Override
    public synchronized Optional<TargetSnapshot> target(UUID targetId) {
        return Optional.ofNullable(targets.get(targetId)).map(TargetState::snapshot);
    }

    @Override
    public synchronized Map<UUID, TargetSnapshot> targets() {
        LinkedHashMap<UUID, TargetSnapshot> result = new LinkedHashMap<>();
        targets.forEach((id, state) -> result.put(id, state.snapshot()));
        return Collections.unmodifiableMap(result);
    }

    private void cleanupHitKeys(long nowMillis) {
        hitKeys.entrySet().removeIf(entry -> nowMillis - entry.getValue() > HIT_KEY_TIMEOUT_MILLIS);
    }

    private long nextRevision() {
        if (nextStateRevision == Long.MAX_VALUE) return Long.MAX_VALUE;
        return nextStateRevision++;
    }

    static final class TargetState {
        final UUID targetId;
        final int targetRuntimeId;
        final FireElementEngine fire = new FireElementEngine(FireElementEngine.Policy.waveOne(1, 64));
        IceElementEngine ice = new IceElementEngine(IceElementEngine.Policy.waveOne(1, 64));
        long frozenSinceMillis = -1L;
        long lastUpdatedAtMillis;
        long snapshotClockMillis;
        long stateRevision;
        long detonationPulseRevision;

        TargetState(UUID targetId, int targetRuntimeId, long nowMillis) {
            this.targetId = targetId;
            this.targetRuntimeId = targetRuntimeId;
            this.lastUpdatedAtMillis = nowMillis;
            this.snapshotClockMillis = nowMillis;
        }

        void resetIce(long nowMillis, long revision) {
            ice.clear();
            frozenSinceMillis = -1L;
            lastUpdatedAtMillis = nowMillis;
            snapshotClockMillis = nowMillis;
            stateRevision = revision;
        }

        void changed(long nowMillis, boolean detonated, long revision) {
            lastUpdatedAtMillis = nowMillis;
            snapshotClockMillis = nowMillis;
            stateRevision = revision;
            if (detonated) detonationPulseRevision++;
        }

        TargetSnapshot snapshot() {
            FireElementEngine.StateSnapshot fireState = fire.state(targetId.toString()).orElse(null);
            IceElementEngine.StateSnapshot iceState = ice.snapshot().get(targetId.toString());
            int contributors = 0;
            if (fireState != null) contributors += fireState.contributions().size();
            if (iceState != null) contributors += iceState.contributions().size();
            double threshold = FIRE_DUMMY.stackThreshold();
            double fractional = fireState == null ? 0.0
                    : fireState.fractionalBurnValue();
            long decayStartsAt = fireState == null ? snapshotClockMillis
                    : safeAdd(fireState.lastFireInputAtMillis(), FIRE_DECAY_HOLD_MILLIS);
            boolean hasFire = fireState != null
                    && (fireState.stacks() > 0 || fractional > 0.0);
            return new TargetSnapshot(
                    targetId,
                    targetRuntimeId,
                    stateRevision,
                    fireState == null ? 0 : fireState.stacks(),
                    fractional,
                    threshold,
                    fractional / threshold,
                    hasFire && snapshotClockMillis >= decayStartsAt,
                    hasFire ? Math.max(0L, decayStartsAt - snapshotClockMillis) : 0L,
                    detonationPulseRevision,
                    safeAdd(lastUpdatedAtMillis, TARGET_TIMEOUT_MILLIS),
                    iceState == null ? 0.0 : iceState.coldValue(),
                    iceState == null ? IceElementEngine.Stage.NONE : iceState.stage(),
                    iceState != null && iceState.frozen(),
                    iceState == null ? 0L : iceState.refreezeImmuneUntilMillis(),
                    lastUpdatedAtMillis,
                    contributors);
        }

        private static long safeAdd(long left, long right) {
            try { return Math.addExact(left, right); }
            catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
        }
    }
}
