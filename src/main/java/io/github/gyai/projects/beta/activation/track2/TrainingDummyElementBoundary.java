package io.github.gyai.projects.beta.activation.track2;

import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.element.ice.IceElementEngine;

import java.util.List;
import java.util.UUID;

/** Bukkit/DamageService boundary implemented only when the Integration Gate wires this Track. */
public interface TrainingDummyElementBoundary {
    boolean isLiveTrainingDummy(UUID targetId);

    List<UUID> nearbyTrainingDummies(UUID centerId, double radius, int limit);

    void applySecondaryDamage(SecondaryDamage damage);

    void publishVisual(VisualEvent event);

    Cancellable scheduleCleanup(Runnable task, long periodMillis);

    record SecondaryDamage(
            String hitId,
            UUID attackerId,
            UUID targetId,
            double amount,
            IceElementEngine.DamageOrigin origin,
            AttackMetadata metadata,
            boolean criticalAllowed
    ) {
        public SecondaryDamage {
            if (hitId == null || hitId.isBlank() || hitId.length() > 128
                    || attackerId == null || targetId == null || !Double.isFinite(amount)
                    || amount < 0 || origin == null || metadata == null || criticalAllowed) {
                throw new IllegalArgumentException("Invalid non-critical secondary damage");
            }
        }
    }

    record VisualEvent(UUID targetId, StagingElementProfile profile, String state, long occurredAtMillis) {
        public VisualEvent {
            if (targetId == null || profile == null || state == null || state.isBlank()
                    || state.length() > 64 || occurredAtMillis < 0) {
                throw new IllegalArgumentException("Invalid visual event");
            }
        }
    }

    interface Cancellable {
        void cancel();

        boolean cancelled();
    }
}
