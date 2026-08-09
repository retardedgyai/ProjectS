package io.github.gyai.projects.combat.shape.bukkit;

import io.github.gyai.projects.combat.shape.Aabb;
import org.bukkit.util.BoundingBox;
import java.util.Objects;

/** Explicit Paper/Bukkit boundary for pure combat geometry. */
public final class BukkitAabbAdapter {
    private BukkitAabbAdapter() { }
    public static Aabb fromBukkit(BoundingBox box) { Objects.requireNonNull(box,"box"); return new Aabb(box.getMinX(),box.getMinY(),box.getMinZ(),box.getMaxX(),box.getMaxY(),box.getMaxZ()); }
    public static BoundingBox toBukkit(Aabb box) { Objects.requireNonNull(box,"box"); return new BoundingBox(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()); }
}
