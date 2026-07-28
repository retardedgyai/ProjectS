package io.github.gyai.projects.combat.skill;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CrowdControlManager {
    private final JavaPlugin plugin;
    private final Set<UUID> controlled = new HashSet<>();
    public CrowdControlManager(JavaPlugin plugin) { this.plugin = plugin; }

    public void slow(LivingEntity target, int ticks, int amplifier) {
        if (!(target instanceof ArmorStand)) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, amplifier));
    }
    public void root(LivingEntity target, int ticks) {
        if (target instanceof ArmorStand) return;
        controlled.add(target.getUniqueId());
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 9));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> controlled.remove(target.getUniqueId()), ticks);
    }
    public void fear(LivingEntity target, Player source, int ticks) {
        if (target instanceof ArmorStand) return;
        Vector away = target.getLocation().toVector().subtract(source.getLocation().toVector()).setY(.15);
        if (away.lengthSquared() > 0) target.setVelocity(away.normalize().multiply(.8));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0));
        slow(target, ticks, target instanceof Player ? 1 : 2);
    }
    public void pull(LivingEntity target, org.bukkit.Location center, double strength) {
        if (target instanceof ArmorStand) return;
        Vector pull = center.toVector().subtract(target.getLocation().toVector()).setY(.08);
        if (pull.lengthSquared() > 0) target.setVelocity(pull.normalize().multiply(Math.min(strength, target instanceof Mob ? .65 : .4)));
    }
    public boolean isControlled(LivingEntity target) { return controlled.contains(target.getUniqueId())
            || target.hasPotionEffect(PotionEffectType.SLOWNESS) || target.hasPotionEffect(PotionEffectType.DARKNESS); }
    public void clear() { controlled.clear(); }
}
