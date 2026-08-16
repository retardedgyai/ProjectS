package io.github.gyai.projects.ability;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

@FunctionalInterface
public interface BossAbilityCaster {
    AbilityRuntime.Cast cast(
            LivingEntity source,
            Player target,
            AbilityDefinition definition
    );
}
