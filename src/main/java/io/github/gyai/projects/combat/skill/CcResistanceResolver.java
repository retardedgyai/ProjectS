package io.github.gyai.projects.combat.skill;

import org.bukkit.entity.LivingEntity;

@FunctionalInterface
public interface CcResistanceResolver {
    CcResistanceProfile resolve(LivingEntity entity);
}
