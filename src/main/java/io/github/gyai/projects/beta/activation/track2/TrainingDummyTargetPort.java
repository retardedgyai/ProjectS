package io.github.gyai.projects.beta.activation.track2;

import org.bukkit.entity.LivingEntity;

/** UUID-free target classification boundary used by the confirmed-hit observer. */
@FunctionalInterface
public interface TrainingDummyTargetPort {
    boolean isTrainingDummy(LivingEntity target);
}
