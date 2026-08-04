package io.github.gyai.projects.participation;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ParticipationLedger implements ParticipationEventPort, AutoCloseable {
    private final int maximumEncounters;
    private final int maximumRecordsPerEncounter;
    private final ParticipationPolicy policy;
    private final Clock clock;
    private final LinkedHashMap<EncounterId, EncounterState> encounters = new LinkedHashMap<>();
    private boolean closed;

    public ParticipationLedger(
            int maximumEncounters,
            int maximumRecordsPerEncounter,
            ParticipationPolicy policy,
            Clock clock
    ) {
        if (maximumEncounters <= 0 || maximumRecordsPerEncounter <= 0) {
            throw new IllegalArgumentException("Participation bounds must be positive");
        }
        this.maximumEncounters = maximumEncounters;
        this.maximumRecordsPerEncounter = maximumRecordsPerEncounter;
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized ParticipationResult record(ParticipationEvent event) {
        Objects.requireNonNull(event, "event");
        if (closed) return result(ParticipationResult.Status.CLOSED, null, "ledger-closed");
        EncounterState encounter = encounters.get(event.key().encounterId());
        if (encounter == null) {
            if (encounters.size() >= maximumEncounters) {
                return result(ParticipationResult.Status.CAPACITY_REACHED, null,
                        "encounter-capacity");
            }
            encounter = new EncounterState();
            encounters.put(event.key().encounterId(), encounter);
        }
        if (encounter.closed) {
            return result(ParticipationResult.Status.CLOSED, null, "encounter-closed");
        }
        ParticipationRecord duplicate = encounter.records.get(event.key());
        if (duplicate != null) {
            return result(ParticipationResult.Status.DUPLICATE, duplicate, "duplicate-event");
        }
        SourceKey sourceKey = new SourceKey(
                event.key().playerId(), event.key().participationSourceId());
        Long currentRevision = encounter.latestRevision.get(sourceKey);
        if (currentRevision != null && event.key().contributionRevision() <= currentRevision) {
            return result(ParticipationResult.Status.STALE, null, "stale-revision");
        }
        if (encounter.records.size() >= maximumRecordsPerEncounter) {
            return result(ParticipationResult.Status.CAPACITY_REACHED, null, "record-capacity");
        }
        ParticipationPolicy.Decision decision = Objects.requireNonNull(
                policy.evaluate(event), "policy decision");
        if (!decision.eligible()) {
            return result(ParticipationResult.Status.INELIGIBLE, null, decision.reason());
        }
        ParticipationRecord record = new ParticipationRecord(
                event, decision.creditedContribution(), clock.instant());
        encounter.records.put(event.key(), record);
        encounter.latestRevision.put(sourceKey, event.key().contributionRevision());
        return result(ParticipationResult.Status.RECORDED, record, "");
    }

    public synchronized boolean closeEncounter(EncounterId encounterId) {
        EncounterState state = encounters.get(encounterId);
        if (state == null || state.closed) return false;
        state.closed = true;
        return true;
    }

    public synchronized int encounterCount() { return encounters.size(); }

    public synchronized int recordCount(EncounterId encounterId) {
        EncounterState state = encounters.get(encounterId);
        return state == null ? 0 : state.records.size();
    }

    public synchronized void clear() { encounters.clear(); }

    @Override
    public synchronized void close() {
        if (closed) return;
        clear();
        closed = true;
    }

    private static ParticipationResult result(
            ParticipationResult.Status status, ParticipationRecord record, String reason
    ) {
        return new ParticipationResult(status, Optional.ofNullable(record), reason);
    }

    private static final class EncounterState {
        private final Map<ParticipationKey, ParticipationRecord> records = new LinkedHashMap<>();
        private final Map<SourceKey, Long> latestRevision = new LinkedHashMap<>();
        private boolean closed;
    }

    private record SourceKey(java.util.UUID playerId, String sourceId) { }
}
