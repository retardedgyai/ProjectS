package io.github.gyai.projects.beta.activation.track2;

import io.github.gyai.projects.combat.element.ice.IceElementEngine;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Immutable observation port for Track 4 protocol/display adapters. */
public interface ElementRuntimeSnapshotPort {
    Optional<TargetSnapshot> target(UUID targetId);

    Map<UUID, TargetSnapshot> targets();

    StagingElementProfile playerProfile(UUID playerId);

    record TargetSnapshot(
            UUID targetId,
            int targetRuntimeId,
            long stateRevision,
            int fireStacks,
            double fractionalFireGauge,
            double fireThreshold,
            double fractionalFireProgress,
            boolean fireDecayActive,
            long fireDecayStartsInMillis,
            long detonationPulseRevision,
            long snapshotExpiresAtMillis,
            double cold,
            IceElementEngine.Stage iceStage,
            boolean frozen,
            long refreezeImmuneUntilMillis,
            long lastUpdatedAtMillis,
            int contributorCount
    ) {
        public TargetSnapshot {
            if (targetId == null || targetRuntimeId < 0 || stateRevision < 0
                    || fireStacks < 0 || fireStacks > 10
                    || !Double.isFinite(fractionalFireGauge)
                    || fractionalFireGauge < 0 || !Double.isFinite(fireThreshold)
                    || fireThreshold <= 0 || fractionalFireGauge >= fireThreshold
                    || !Double.isFinite(fractionalFireProgress)
                    || fractionalFireProgress < 0 || fractionalFireProgress > 1
                    || fireDecayStartsInMillis < 0 || detonationPulseRevision < 0
                    || snapshotExpiresAtMillis < lastUpdatedAtMillis
                    || !Double.isFinite(cold) || cold < 0
                    || iceStage == null || refreezeImmuneUntilMillis < 0
                    || lastUpdatedAtMillis < 0 || contributorCount < 0) {
                throw new IllegalArgumentException("Invalid element runtime snapshot");
            }
            double expectedProgress = fractionalFireGauge / fireThreshold;
            if (Math.abs(expectedProgress - fractionalFireProgress) > 1.0e-9) {
                throw new IllegalArgumentException("Fire progress does not match gauge");
            }
        }

        /** Existing protocol-v1 display document fields; no wire schema change required. */
        public Map<String, String> fireDisplayFields() {
            return Map.of(
                    "target-network-id", Integer.toString(targetRuntimeId),
                    "state-revision", Long.toString(stateRevision),
                    "fire-stacks", Integer.toString(fireStacks),
                    "fire-fractional-gauge", Double.toString(fractionalFireGauge),
                    "fire-threshold", Double.toString(fireThreshold),
                    "fire-progress-ratio", Double.toString(fractionalFireProgress),
                    "fire-decay-active", Boolean.toString(fireDecayActive),
                    "fire-decay-starts-in-millis", Long.toString(fireDecayStartsInMillis),
                    "fire-detonation-pulse-revision", Long.toString(detonationPulseRevision),
                    "snapshot-expires-at-millis", Long.toString(snapshotExpiresAtMillis));
        }
    }
}
