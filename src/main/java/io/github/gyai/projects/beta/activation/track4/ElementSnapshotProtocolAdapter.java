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
import java.util.OptionalInt;
import java.util.OptionalLong;
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
        return decide(viewerId, targetId, nowMillis).snapshot();
    }

    /**
     * Purely describes the current delivery gate while retaining the existing
     * revision gate semantics used by {@link #next(UUID, UUID, long)}.
     */
    public synchronized Decision decide(UUID viewerId, UUID targetId, long nowMillis) {
        if (viewerId == null) return Decision.empty(DecisionStatus.VIEWER_MISSING);
        if (targetId == null) return Decision.empty(DecisionStatus.TARGET_MISSING);
        if (protocolState.get() != BetaRuntimeModuleState.RUNNING) {
            return Decision.empty(DecisionStatus.PROTOCOL_NOT_RUNNING);
        }
        if (elementState.get() != BetaRuntimeModuleState.RUNNING) {
            return Decision.empty(DecisionStatus.ELEMENTS_NOT_RUNNING);
        }
        if (!visibility.test(new Visibility(viewerId, targetId))) {
            return Decision.empty(DecisionStatus.VISIBILITY_DENIED);
        }
        BetaCapabilitySnapshot capability = capabilities.snapshot(viewerId);
        if (capability == null || !capability.supports(BetaCapabilityId.ELEMENTS, 1)) {
            return Decision.empty(DecisionStatus.CAPABILITY_UNAVAILABLE);
        }
        Optional<ElementRuntimeSnapshotPort.TargetSnapshot> found = snapshots.target(targetId);
        if (found.isEmpty()) {
            lastSent.remove(new ViewerTarget(viewerId, targetId));
            return Decision.empty(DecisionStatus.SNAPSHOT_MISSING);
        }
        ElementRuntimeSnapshotPort.TargetSnapshot value = found.orElseThrow();
        DecisionMetadata metadata = metadata(value);
        if (value.snapshotExpiresAtMillis() <= nowMillis) {
            lastSent.remove(new ViewerTarget(viewerId, targetId));
            return new Decision(DecisionStatus.SNAPSHOT_EXPIRED, Optional.empty(),
                    metadata.targetRuntimeId(), metadata.stateRevision(), metadata.fireStacks());
        }
        lastSent.keySet().removeIf(key -> key.viewerId().equals(viewerId)
                && !key.targetId().equals(targetId));
        ViewerTarget key = new ViewerTarget(viewerId, targetId);
        if (value.stateRevision() <= lastSent.getOrDefault(key, -1L)) {
            return new Decision(DecisionStatus.REVISION_NOT_ADVANCED, Optional.empty(),
                    metadata.targetRuntimeId(), metadata.stateRevision(), metadata.fireStacks());
        }
        remember(key, value.stateRevision());
        return new Decision(DecisionStatus.READY, Optional.of(map(value)),
                metadata.targetRuntimeId(), metadata.stateRevision(), metadata.fireStacks());
    }

    public synchronized void clearViewer(UUID viewerId) {
        lastSent.keySet().removeIf(key -> key.viewerId().equals(viewerId));
    }

    public synchronized void retainViewers(Iterable<UUID> viewers) {
        java.util.LinkedHashSet<UUID> active = new java.util.LinkedHashSet<>();
        if (viewers != null) for (UUID viewer : viewers) {
            if (viewer != null && active.size() < MAXIMUM_VIEWERS) active.add(viewer);
        }
        lastSent.keySet().removeIf(key -> !active.contains(key.viewerId()));
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

    private static DecisionMetadata metadata(ElementRuntimeSnapshotPort.TargetSnapshot value) {
        return new DecisionMetadata(OptionalInt.of(value.targetRuntimeId()),
                OptionalLong.of(value.stateRevision()), OptionalInt.of(value.fireStacks()));
    }

    @FunctionalInterface public interface CapabilityPort {
        BetaCapabilitySnapshot snapshot(UUID viewerId);
    }

    public enum DecisionStatus {
        READY,
        VIEWER_MISSING,
        TARGET_MISSING,
        PROTOCOL_NOT_RUNNING,
        ELEMENTS_NOT_RUNNING,
        VISIBILITY_DENIED,
        CAPABILITY_UNAVAILABLE,
        SNAPSHOT_MISSING,
        SNAPSHOT_EXPIRED,
        REVISION_NOT_ADVANCED
    }

    public record Decision(
            DecisionStatus status,
            Optional<ElementDisplaySnapshot> snapshot,
            OptionalInt targetRuntimeId,
            OptionalLong stateRevision,
            OptionalInt fireStacks
    ) {
        public Decision {
            if (status == null || snapshot == null || targetRuntimeId == null
                    || stateRevision == null || fireStacks == null) {
                throw new IllegalArgumentException("Decision fields are required");
            }
            if (status == DecisionStatus.READY && snapshot.isEmpty()) {
                throw new IllegalArgumentException("READY decision requires a snapshot");
            }
            if (status != DecisionStatus.READY && snapshot.isPresent()) {
                throw new IllegalArgumentException("Non-ready decision cannot contain a snapshot");
            }
        }

        private static Decision empty(DecisionStatus status) {
            return new Decision(status, Optional.empty(), OptionalInt.empty(),
                    OptionalLong.empty(), OptionalInt.empty());
        }
    }

    private record DecisionMetadata(OptionalInt targetRuntimeId,
                                    OptionalLong stateRevision,
                                    OptionalInt fireStacks) { }

    public record Visibility(UUID viewerId, UUID targetId) { }
    private record ViewerTarget(UUID viewerId, UUID targetId) { }
}
