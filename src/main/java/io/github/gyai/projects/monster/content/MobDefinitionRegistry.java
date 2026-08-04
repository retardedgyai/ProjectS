package io.github.gyai.projects.monster.content;

import io.github.gyai.projects.monster.definition.v2.MobDefinitionV2;
import io.github.gyai.projects.monster.definition.v2.MobDefinitionValidation;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Bounded registry. It retains definitions only, never Bukkit entity references. */
public final class MobDefinitionRegistry implements AutoCloseable {
    private final int maximumDefinitions;
    private final Clock clock;
    private final LinkedHashMap<String, MobDefinitionSnapshot> current = new LinkedHashMap<>();
    private final LinkedHashMap<String, MobDefinitionSnapshot> lastGood = new LinkedHashMap<>();
    private final LinkedHashMap<String, MobDefinitionRevisionEvent> events = new LinkedHashMap<>();
    private boolean closed;

    public MobDefinitionRegistry(int maximumDefinitions, Clock clock) {
        if (maximumDefinitions < 1) throw new IllegalArgumentException("maximumDefinitions");
        this.maximumDefinitions = maximumDefinitions;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    public synchronized MobDefinitionApplyResult apply(
            MobDefinitionV2 definition,
            MobDefinitionValidation validation
    ) {
        if (closed) return result(MobDefinitionApplyResult.Status.CLOSED, definition, "closed");
        MobDefinitionSnapshot existing = definition == null ? null : current.get(definition.mobId());
        if (validation == null || !validation.valid()) {
            return new MobDefinitionApplyResult(
                    MobDefinitionApplyResult.Status.INVALID_RETAINED, existing,
                    definition == null ? null : lastGood.get(definition.mobId()),
                    "invalid reload retained current");
        }
        if (existing == null && current.size() >= maximumDefinitions) {
            return result(MobDefinitionApplyResult.Status.CAPACITY_REJECTED, definition, "capacity");
        }
        if (existing != null && definition.revision() <= existing.revision()) {
            return result(MobDefinitionApplyResult.Status.STALE_REJECTED, definition, "stale revision");
        }
        Instant now = clock.instant();
        MobDefinitionSnapshot snapshot = new MobDefinitionSnapshot(
                definition, definition.revision(), now);
        if (existing != null) lastGood.put(definition.mobId(), existing);
        else lastGood.put(definition.mobId(), snapshot);
        current.put(definition.mobId(), snapshot);
        events.put(definition.mobId(), new MobDefinitionRevisionEvent(
                definition.mobId(), existing == null ? 0 : existing.revision(),
                snapshot.revision(), now));
        return new MobDefinitionApplyResult(MobDefinitionApplyResult.Status.APPLIED,
                snapshot, lastGood.get(definition.mobId()), "applied");
    }

    /** Pinning means later reloads cannot mutate the definition used by this spawn. */
    public synchronized Optional<MobDefinitionSnapshot> pinForSpawn(String mobId) {
        return closed ? Optional.empty() : Optional.ofNullable(current.get(mobId));
    }

    public synchronized Optional<MobDefinitionSnapshot> current(String mobId) {
        return closed ? Optional.empty() : Optional.ofNullable(current.get(mobId));
    }

    public synchronized Optional<MobDefinitionSnapshot> lastGood(String mobId) {
        return closed ? Optional.empty() : Optional.ofNullable(lastGood.get(mobId));
    }

    public synchronized List<MobDefinitionRevisionEvent> events() {
        return closed ? List.of() : List.copyOf(events.values());
    }

    public synchronized Map<String, MobDefinitionSnapshot> snapshot() {
        return closed ? Map.of() : Map.copyOf(current);
    }

    public synchronized void clear() { current.clear(); lastGood.clear(); events.clear(); }

    @Override public synchronized void close() { if (!closed) { clear(); closed = true; } }

    private MobDefinitionApplyResult result(MobDefinitionApplyResult.Status status,
                                            MobDefinitionV2 definition, String message) {
        String id = definition == null ? "" : definition.mobId();
        return new MobDefinitionApplyResult(status, current.get(id), lastGood.get(id), message);
    }
}
