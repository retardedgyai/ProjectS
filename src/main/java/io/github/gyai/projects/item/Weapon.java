package io.github.gyai.projects.item;

import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.BiConsumer;

public class Weapon extends CustomItem {

    private final double physicalAttack;
    private final double magicalAttack;
    private final double baseAttacksPerSecond;
    private final double attackSpeedBonus;

    public Weapon(
            String id,
            String displayName,
            Material material,
            double attackDamage,
            BiConsumer<ItemMeta, String> idWriter
    ) {
        this(id, displayName, material, attackDamage, 0.0, 1.0, 0.0, idWriter);
    }

    public Weapon(
            String id,
            String displayName,
            Material material,
            double attackDamage,
            double attackSpeedBonus,
            BiConsumer<ItemMeta, String> idWriter
    ) {
        this(id, displayName, material, attackDamage, 0.0, 1.0,
                attackSpeedBonus, idWriter);
    }

    public Weapon(
            String id,
            String displayName,
            Material material,
            double physicalAttack,
            double magicalAttack,
            double baseAttacksPerSecond,
            double attackSpeedBonus,
            BiConsumer<ItemMeta, String> idWriter
    ) {
        super(id, displayName, material, idWriter);
        this.physicalAttack = requireNonNegative("physicalAttack", physicalAttack);
        this.magicalAttack = requireNonNegative("magicalAttack", magicalAttack);
        this.baseAttacksPerSecond = requireNonNegative(
                "baseAttacksPerSecond", baseAttacksPerSecond);
        if (!Double.isFinite(attackSpeedBonus)) {
            throw new IllegalArgumentException("attackSpeedBonus must be finite");
        }
        this.attackSpeedBonus = attackSpeedBonus;
    }

    /** @deprecated Use {@link #getPhysicalAttack()} or {@link #getMagicalAttack()}. */
    @Deprecated
    public double getAttackDamage() {
        return Math.max(physicalAttack, magicalAttack);
    }

    public double getPhysicalAttack() {
        return physicalAttack;
    }

    public double getMagicalAttack() {
        return magicalAttack;
    }

    public double getBaseAttacksPerSecond() {
        return baseAttacksPerSecond;
    }

    public double getAttackSpeedBonus() {
        return attackSpeedBonus;
    }

    private static double requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }
}
