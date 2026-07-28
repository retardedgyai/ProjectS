package io.github.gyai.projects.manager;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class ComboEffectPlayer {
    public void play(Player attacker, LivingEntity target, int comboStep) {
        Location center = target.getLocation().add(0, target.getHeight() * 0.55, 0);
        Vector direction = attacker.getLocation().getDirection().setY(0);
        Vector side = direction.lengthSquared() > 0.0
                ? direction.normalize().crossProduct(new Vector(0, 1, 0)).multiply(0.35)
                : new Vector();

        switch (comboStep) {
            case 1 -> {
                target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(side), 1, 0.08, 0.08, 0.08, 0.0);
                target.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_WEAK, 0.65f, 0.9f);
            }
            case 2 -> {
                target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center.clone().subtract(side), 1, 0.12, 0.12, 0.12, 0.0);
                target.getWorld().spawnParticle(Particle.CRIT, center, 4, 0.2, 0.25, 0.2, 0.05);
                target.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.15f);
            }
            case 3 -> {
                target.getWorld().spawnParticle(Particle.CRIT, center, 8, 0.4, 0.35, 0.4, 0.1);
                target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 2, 0.35, 0.2, 0.35, 0.0);
                target.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.85f, 1.0f);
            }
            case 4 -> {
                target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 4, 0.65, 0.35, 0.65, 0.0);
                target.getWorld().spawnParticle(Particle.CRIT, center, 14, 0.65, 0.55, 0.65, 0.16);
                target.getWorld().spawnParticle(Particle.ENCHANTED_HIT, center, 6, 0.45, 0.4, 0.45, 0.1);
                target.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.75f);
                attacker.getWorld().spawnParticle(Particle.SWEEP_ATTACK, attacker.getLocation().add(0, 1, 0), 2, 0.45, 0.2, 0.45, 0.0);
            }
            default -> throw new IllegalArgumentException("Unknown combo step: " + comboStep);
        }
    }
}
