package io.github.gyai.projects.item;

import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.BiConsumer;

public class Armor extends CustomItem {

    private final double physicalDefense;
    private final double magicalDefense;

    public Armor(
            String id,
            String displayName,
            Material material,
            int defense,
            BiConsumer<ItemMeta, String> idWriter
    ) {
        this(id, displayName, material, defense, defense, idWriter);
    }

    public Armor(
            String id,
            String displayName,
            Material material,
            double physicalDefense,
            double magicalDefense,
            BiConsumer<ItemMeta, String> idWriter
    ) {
        super(id, displayName, material, idWriter);
        this.physicalDefense = requireNonNegative("physicalDefense", physicalDefense);
        this.magicalDefense = requireNonNegative("magicalDefense", magicalDefense);
    }

    /** @deprecated Use the separated physical and magical defense values. */
    @Deprecated
    public int getDefense() {
        return (int) Math.round(physicalDefense);
    }

    public double getPhysicalDefense() {
        return physicalDefense;
    }

    public double getMagicalDefense() {
        return magicalDefense;
    }

    private static double requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }
}
