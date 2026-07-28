package io.github.gyai.projects.dummy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class DamageNumberDisplay {
    private final JavaPlugin plugin;
    private final Set<UUID> activeDisplays = new HashSet<>();

    public DamageNumberDisplay(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(Location origin, double damage, NamedTextColor color, boolean emphasized) {
        double offsetX = ThreadLocalRandom.current().nextDouble(-0.35, 0.35);
        double offsetZ = ThreadLocalRandom.current().nextDouble(-0.35, 0.35);
        Location location = origin.clone().add(offsetX, 2.15, offsetZ);
        String number = damage == Math.rint(damage) ? "%.0f".formatted(damage) : "%.1f".formatted(damage);
        String value = emphasized ? number + "!" : number;

        TextDisplay display = origin.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.text(Component.text(value, color));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(true);
            entity.setShadowed(true);
            entity.setPersistent(false);
        });
        activeDisplays.add(display.getUniqueId());

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.teleport(display.getLocation().add(0, 0.25, 0));
            }
        }, 5L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            activeDisplays.remove(display.getUniqueId());
            display.remove();
        }, 14L);
    }

    public void clear() {
        for (UUID displayId : Set.copyOf(activeDisplays)) {
            var entity = plugin.getServer().getEntity(displayId);
            if (entity != null) {
                entity.remove();
            }
        }
        activeDisplays.clear();
    }
}
