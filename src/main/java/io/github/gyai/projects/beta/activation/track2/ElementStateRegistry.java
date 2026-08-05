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

    static final FireElementEngine.TargetProfile FIRE_DUMMY =
            new FireElementEngine.TargetProfile(ElementTargetCategory.NORMAL, 25.0);
    static final IceElementEngine.TargetProfile ICE_DUMMY =
            new IceElementEngine.TargetProfile(ElementTargetCategory.NORMAL, 100.0, .25, .5);

    private final LinkedHashMap<UUID, StagingElementProfile> profiles = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, TargetState> targets = new LinkedHashMap<>();
    private final LinkedHashMap<String, Long> hitKeys = new LinkedHashMap<>();
    private final ArrayDeque<ParticipationEvent> participation = new ArrayDeque<>();
    private long nextSequence = 1L;

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

    synchronized TargetState targetState(UUID targetId, long nowMillis) {
        TargetState state = targets.get(targetId);
        if (state != null && state.frozenSinceMillis >= 0
                && nowMillis - state.frozenSinceMillis >= FREEZE_DURATION_MILLIS) {
            state.resetIce(nowMillis);
        }
        if (state == null && targets.size() < MAXIMUM_TARGETS) {
            state = new TargetState(targetId, nowMillis);
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
            if (state.frozenSinceMillis >= 0
                    && nowMillis - state.frozenSinceMillis >= FREEZE_DURATION_MILLIS) {
                state.resetIce(nowMillis);
            }
            try {
                state.fire.advanceDecay(state.targetId.toString(), nowMillis);
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

    static final class TargetState {
        final UUID targetId;
        final FireElementEngine fire = new FireElementEngine(FireElementEngine.Policy.waveOne(1, 64));
        IceElementEngine ice = new IceElementEngine(IceElementEngine.Policy.waveOne(1, 64));
        long frozenSinceMillis = -1L;
        long lastUpdatedAtMillis;

        TargetState(UUID targetId, long nowMillis) {
            this.targetId = targetId;
            this.lastUpdatedAtMillis = nowMillis;
        }

        void resetIce(long nowMillis) {
            ice.clear();
            frozenSinceMillis = -1L;
            lastUpdatedAtMillis = nowMillis;
        }

        TargetSnapshot snapshot() {
            FireElementEngine.StateSnapshot fireState = fire.state(targetId.toString()).orElse(null);
            IceElementEngine.StateSnapshot iceState = ice.snapshot().get(targetId.toString());
            int contributors = 0;
            if (fireState != null) contributors += fireState.contributions().size();
            if (iceState != null) contributors += iceState.contributions().size();
            return new TargetSnapshot(
                    targetId,
                    fireState == null ? 0 : fireState.stacks(),
                    fireState == null ? 0.0 : fireState.fractionalBurnValue(),
                    iceState == null ? 0.0 : iceState.coldValue(),
                    iceState == null ? IceElementEngine.Stage.NONE : iceState.stage(),
                    iceState != null && iceState.frozen(),
                    iceState == null ? 0L : iceState.refreezeImmuneUntilMillis(),
                    lastUpdatedAtMillis,
                    contributors);
        }
    }
}
