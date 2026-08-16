package io.github.gyai.projects.beta.activation;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BetaRuntimeHealthService {
    public static final int MAXIMUM_DIAGNOSTICS = 64;
    public static final int MAXIMUM_HEALTH_HISTORY = 32;

    private final Clock clock;
    private final ArrayDeque<BetaRuntimeDiagnostic> diagnostics = new ArrayDeque<>();
    private final ArrayDeque<BetaRuntimeHealthSnapshot> history = new ArrayDeque<>();
    private final EnumMap<BetaRuntimeModuleId, BetaRuntimeModuleState> states =
            new EnumMap<>(BetaRuntimeModuleId.class);
    private final EnumMap<BetaRuntimeModuleId, Set<BetaRuntimeModuleId>> blocked =
            new EnumMap<>(BetaRuntimeModuleId.class);
    private BetaRuntimeHealthStatus status = BetaRuntimeHealthStatus.DISABLED;
    private long startCount;
    private long stopCount;
    private String lastFailure = "";

    public BetaRuntimeHealthService(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        for (BetaRuntimeModuleId id : BetaRuntimeModuleId.values()) {
            states.put(id, BetaRuntimeModuleState.NOT_INSTALLED);
        }
    }

    public synchronized void state(BetaRuntimeModuleId id, BetaRuntimeModuleState state) {
        states.put(java.util.Objects.requireNonNull(id), java.util.Objects.requireNonNull(state));
    }

    public synchronized void blocked(
            BetaRuntimeModuleId id,
            Set<BetaRuntimeModuleId> dependencies
    ) {
        Set<BetaRuntimeModuleId> value = Set.copyOf(
                dependencies == null ? Set.of() : dependencies);
        if (value.isEmpty()) blocked.remove(id); else blocked.put(id, value);
    }

    public synchronized void diagnostic(
            BetaRuntimeModuleId moduleId,
            BetaRuntimeDiagnosticCode code,
            String detail,
            boolean failure
    ) {
        BetaRuntimeDiagnostic value = new BetaRuntimeDiagnostic(
                Instant.now(clock), moduleId, code, detail);
        diagnostics.addLast(value);
        while (diagnostics.size() > MAXIMUM_DIAGNOSTICS) diagnostics.removeFirst();
        if (failure) lastFailure = value.detail();
    }

    public synchronized void status(BetaRuntimeHealthStatus value) {
        status = java.util.Objects.requireNonNull(value);
        recordHistory(true);
    }

    public synchronized void started() {
        startCount++;
    }

    public synchronized void stopped() {
        stopCount++;
    }

    public synchronized BetaRuntimeHealthSnapshot snapshot(boolean restartRequired) {
        return currentSnapshot(restartRequired);
    }

    public synchronized List<BetaRuntimeHealthSnapshot> history() {
        return List.copyOf(history);
    }

    private BetaRuntimeHealthSnapshot currentSnapshot(boolean restartRequired) {
        return new BetaRuntimeHealthSnapshot(
                Instant.now(clock), status, new EnumMap<>(states), new EnumMap<>(blocked),
                List.copyOf(new ArrayList<>(diagnostics)), startCount, stopCount,
                lastFailure, restartRequired);
    }

    private void recordHistory(boolean restartRequired) {
        history.addLast(currentSnapshot(restartRequired));
        while (history.size() > MAXIMUM_HEALTH_HISTORY) history.removeFirst();
    }
}
