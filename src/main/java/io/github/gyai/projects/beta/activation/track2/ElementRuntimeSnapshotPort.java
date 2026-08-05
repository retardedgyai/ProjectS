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
            int fireStacks,
            double fractionalFire,
            double cold,
            IceElementEngine.Stage iceStage,
            boolean frozen,
            long refreezeImmuneUntilMillis,
            long lastUpdatedAtMillis,
            int contributorCount
    ) {
        public TargetSnapshot {
            if (targetId == null || fireStacks < 0 || !Double.isFinite(fractionalFire)
                    || fractionalFire < 0 || !Double.isFinite(cold) || cold < 0
                    || iceStage == null || refreezeImmuneUntilMillis < 0
                    || lastUpdatedAtMillis < 0 || contributorCount < 0) {
                throw new IllegalArgumentException("Invalid element runtime snapshot");
            }
        }
    }
}
