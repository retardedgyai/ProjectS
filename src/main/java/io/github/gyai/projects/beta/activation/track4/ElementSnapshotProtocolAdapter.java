package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.beta.activation.track2.ElementRuntimeSnapshotPort;
import io.github.gyai.projects.combat.element.ice.IceElementEngine;
import io.github.gyai.projects.network.beta.BetaCapabilityId;
import io.github.gyai.projects.network.beta.BetaCapabilitySnapshot;
import io.github.gyai.projects.network.beta.ElementDisplaySnapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Pure revision gate and mapper for projects:elements protocol-v1 documents. */
public final class ElementSnapshotProtocolAdapter {
    public static final int MAXIMUM_VIEWERS = 512;
    private final ElementRuntimeSnapshotPort snapshots;
    private final Supplier<BetaRuntimeModuleState> protocolState;
    private final Supplier<BetaRuntimeModuleState> elementState;
    private final CapabilityPort capabilities;
    private final Predicate<Visibility> visibility;
    private final LinkedHashMap<ViewerTarget, Long> lastSent = new LinkedHashMap<>(16, .75f, true);

    public ElementSnapshotProtocolAdapter(
            ElementRuntimeSnapshotPort snapshots,
            Supplier<BetaRuntimeModuleState> protocolState,
            Supplier<BetaRuntimeModuleState> elementState,
            CapabilityPort capabilities,
            Predicate<Visibility> visibility
    ) {
        this.snapshots = java.util.Objects.requireNonNull(snapshots);
        this.protocolState = java.util.Objects.requireNonNull(protocolState);
        this.elementState = java.util.Objects.requireNonNull(elementState);
        this.capabilities = java.util.Objects.requireNonNull(capabilities);
        this.visibility = java.util.Objects.requireNonNull(visibility);
    }

    public synchronized Optional<ElementDisplaySnapshot> next(UUID viewerId, UUID targetId, long nowMillis) {
        if (protocolState.get() != BetaRuntimeModuleState.RUNNING
                || elementState.get() != BetaRuntimeModuleState.RUNNING
                || !visibility.test(new Visibility(viewerId, targetId))) return Optional.empty();
        BetaCapabilitySnapshot capability = capabilities.snapshot(viewerId);
        if (capability == null || !capability.supports(BetaCapabilityId.ELEMENTS, 1)) return Optional.empty();
        Optional<ElementRuntimeSnapshotPort.TargetSnapshot> found = snapshots.target(targetId);
        if (found.isEmpty() || found.orElseThrow().snapshotExpiresAtMillis() <= nowMillis) {
            lastSent.remove(new ViewerTarget(viewerId, targetId));
            return Optional.empty();
        }
        ElementRuntimeSnapshotPort.TargetSnapshot value = found.orElseThrow();
        ViewerTarget key = new ViewerTarget(viewerId, targetId);
        if (value.stateRevision() <= lastSent.getOrDefault(key, -1L)) return Optional.empty();
        remember(key, value.stateRevision());
        return Optional.of(map(value));
    }

    public synchronized void clearViewer(UUID viewerId) {
        lastSent.keySet().removeIf(key -> key.viewerId().equals(viewerId));
    }

    public synchronized void clear() { lastSent.clear(); }

    public synchronized int retainedRevisionCount() { return lastSent.size(); }

    public static ElementDisplaySnapshot map(ElementRuntimeSnapshotPort.TargetSnapshot value) {
        return new ElementDisplaySnapshot(value.targetRuntimeId(), value.stateRevision(),
                value.fractionalFireGauge(), value.fireStacks(), value.fireThreshold(),
                value.fractionalFireProgress(), value.fireDecayActive(),
                value.fireDecayStartsInMillis(), value.detonationPulseRevision(),
                value.snapshotExpiresAtMillis(), value.cold(), map(value.iceStage()),
                value.frozen(), value.refreezeImmuneUntilMillis());
    }

    private void remember(ViewerTarget key, long revision) {
        lastSent.put(key, revision);
        while (lastSent.size() > MAXIMUM_VIEWERS) lastSent.remove(lastSent.keySet().iterator().next());
    }

    private static ElementDisplaySnapshot.ColdStage map(IceElementEngine.Stage value) {
        return switch (value) {
            case NONE -> ElementDisplaySnapshot.ColdStage.NONE;
            case COLD_I -> ElementDisplaySnapshot.ColdStage.CHILLED;
            case COLD_II -> ElementDisplaySnapshot.ColdStage.DEEP_CHILL;
            case FROZEN -> ElementDisplaySnapshot.ColdStage.FROZEN;
        };
    }

    @FunctionalInterface public interface CapabilityPort {
        BetaCapabilitySnapshot snapshot(UUID viewerId);
    }
    public record Visibility(UUID viewerId, UUID targetId) { }
    private record ViewerTarget(UUID viewerId, UUID targetId) { }
}
