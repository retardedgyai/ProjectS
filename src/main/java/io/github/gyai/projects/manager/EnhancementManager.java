package io.github.gyai.projects.manager;

import io.github.gyai.projects.item.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class EnhancementManager {
    public static final int MAX_LEVEL = 30;
    public static final String ENHANCEMENT_MATERIAL_ID = "enhancement_stone";
    public static final String REPAIR_MATERIAL_ID = "repair_crystal";

    private final ItemManager itemManager;
    private final BalanceTuningManager balanceManager;
    private final NamespacedKey levelKey;
    private final NamespacedKey brokenKey;
    private final NamespacedKey attackSpeedModifierKey;
    private final NamespacedKey weaponAttackPowerBonusKey;
    private final NamespacedKey weaponAttackSpeedBonusKey;
    private final Set<UUID> applyingSkillDamage = new HashSet<>();

    public EnhancementManager(
            JavaPlugin plugin,
            ItemManager itemManager,
            BalanceTuningManager balanceManager
    ) {
        this.itemManager = itemManager;
        this.balanceManager = balanceManager;
        levelKey = new NamespacedKey(plugin, "enhancement_level");
        brokenKey = new NamespacedKey(plugin, "weapon_broken");
        attackSpeedModifierKey = new NamespacedKey(plugin, "enhancement_attack_speed");
        weaponAttackPowerBonusKey = new NamespacedKey(plugin, "weapon_attack_power_bonus");
        weaponAttackSpeedBonusKey = new NamespacedKey(plugin, "weapon_attack_speed_bonus");
    }

    public boolean isWeapon(ItemStack item) {
        return itemManager.getItem(itemManager.getItemId(item)) instanceof Weapon;
    }

    public int getLevel(ItemStack item) {
        if (!isWeapon(item) || !item.hasItemMeta()) {
            return 0;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(levelKey, PersistentDataType.INTEGER, 0);
    }

    public boolean isBroken(ItemStack item) {
        return isWeapon(item) && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(brokenKey, PersistentDataType.BYTE);
    }

    public int getMaterialCost(int targetLevel) {
        return Math.max(1, (targetLevel + 4) / 5);
    }

    public int getRepairCost(int level) {
        return Math.max(1, (level + 4) / 5);
    }

    public double getSuccessChance(int targetLevel) {
        if (targetLevel <= 5) return 100.0;
        if (targetLevel <= 10) return 95.0 - (targetLevel - 6) * 7.5;
        if (targetLevel <= 15) return 55.0 - (targetLevel - 11) * 5.0;
        if (targetLevel <= 20) return 30.0 - (targetLevel - 16) * 4.0;
        if (targetLevel <= 25) return 10.0 - (targetLevel - 21) * 1.5;
        return 3.0 - (targetLevel - 26) * 0.5;
    }

    public double getBreakChance(int currentLevel) {
        if (currentLevel < 15) return 0.0;
        return Math.min(50.0, 5.0 + (currentLevel - 15) * 3.0);
    }

    public double getAttackMultiplier(ItemStack item) {
        return 1.0 + getLevel(item) * 0.04;
    }

    public double getAttackPower(Player player, ItemStack item) {
        String itemId = itemManager.getItemId(item);
        if (!(itemManager.getItem(itemId) instanceof Weapon weapon) || isBroken(item)) {
            return 0.0;
        }
        double globalBase = balanceManager.weaponAttackPower(
                itemId, weapon.getAttackDamage());
        return BalanceMath.attackPower(
                globalBase,
                getWeaponAttackPowerBonus(item),
                getAttackMultiplier(item));
    }

    public double getTotalAttackSpeedBonus(Player player, ItemStack item) {
        String itemId = itemManager.getItemId(item);
        double globalBase = itemManager.getItem(itemId) instanceof Weapon weapon
                ? balanceManager.weaponAttackSpeed(
                        itemId, weapon.getAttackSpeedBonus())
                : 0.0;
        return BalanceMath.attackSpeed(
                globalBase,
                getAttackSpeedBonus(getLevel(item)),
                getWeaponAttackSpeedBonus(item));
    }

    public double getWeaponAttackPowerBonus(ItemStack item) {
        if (!isWeapon(item) || !item.hasItemMeta()) return 0.0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(
                weaponAttackPowerBonusKey, PersistentDataType.DOUBLE, 0.0);
    }

    public double getWeaponAttackSpeedBonus(ItemStack item) {
        if (!isWeapon(item) || !item.hasItemMeta()) return 0.0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(
                weaponAttackSpeedBonusKey, PersistentDataType.DOUBLE, 0.0);
    }

    public void addWeaponAttackPowerBonus(ItemStack item, double amount) {
        setWeaponBonus(item, weaponAttackPowerBonusKey,
                Math.clamp(getWeaponAttackPowerBonus(item) + amount, -100.0, 1_000.0));
    }

    public void addWeaponAttackSpeedBonus(ItemStack item, double amount) {
        setWeaponBonus(item, weaponAttackSpeedBonusKey,
                Math.clamp(getWeaponAttackSpeedBonus(item) + amount, -0.9, 5.0));
    }

    public void resetWeaponBonuses(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().remove(weaponAttackPowerBonusKey);
        meta.getPersistentDataContainer().remove(weaponAttackSpeedBonusKey);
        item.setItemMeta(meta);
        refreshWeapon(item);
    }

    private void setWeaponBonus(ItemStack item, NamespacedKey key, double value) {
        if (!isWeapon(item)) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, value);
        item.setItemMeta(meta);
        refreshWeapon(item);
    }

    public void beginSkillDamage(UUID playerId) {
        applyingSkillDamage.add(playerId);
    }

    public void endSkillDamage(UUID playerId) {
        applyingSkillDamage.remove(playerId);
    }

    public boolean isApplyingSkillDamage(UUID playerId) {
        return applyingSkillDamage.contains(playerId);
    }

    public double getAttackSpeedBonus(int level) {
        return level * 0.008;
    }

    public void setLevel(ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(
                levelKey, PersistentDataType.INTEGER, Math.clamp(level, 0, MAX_LEVEL));
        item.setItemMeta(meta);
        refreshWeapon(item);
    }

    public void setBroken(ItemStack item, boolean broken) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (broken) {
            data.set(brokenKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            data.remove(brokenKey);
        }
        item.setItemMeta(meta);
        refreshWeapon(item);
    }

    public void refreshWeapon(ItemStack item) {
        String itemId = itemManager.getItemId(item);
        if (!(itemManager.getItem(itemId) instanceof Weapon weapon)) return;

        int level = getLevel(item);
        boolean broken = isBroken(item);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String name = (level > 0 ? "§6[+" + level + "] " : "") + weapon.getDisplayName();
        if (broken) name = "§4[破損] " + name;
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name));

        double attackPowerBonus = getWeaponAttackPowerBonus(item);
        double attackSpeedBonus = getWeaponAttackSpeedBonus(item);
        double globalAttackPower = balanceManager.weaponAttackPower(
                itemId, weapon.getAttackDamage());
        double globalAttackSpeed = balanceManager.weaponAttackSpeed(
                itemId, weapon.getAttackSpeedBonus());
        double damage = BalanceMath.attackPower(
                globalAttackPower, attackPowerBonus,
                1.0 + level * 0.04);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("強化値: +" + level + " / +" + MAX_LEVEL,
                level >= 20 ? NamedTextColor.GOLD : NamedTextColor.AQUA));
        lore.add(Component.text("攻撃力: %.1f  (+%.0f%%)".formatted(damage, level * 4.0),
                NamedTextColor.RED));
        double totalItemAttackSpeed = BalanceMath.attackSpeed(
                globalAttackSpeed,
                getAttackSpeedBonus(level),
                attackSpeedBonus);
        lore.add(Component.text("攻撃速度: %+.1f%%".formatted(totalItemAttackSpeed * 100.0),
                NamedTextColor.YELLOW));
        if (attackPowerBonus != 0.0 || attackSpeedBonus != 0.0) {
            lore.add(Component.text("武器調整: 攻撃力 %+.1f / 速度 %+.1f%%".formatted(
                    attackPowerBonus, attackSpeedBonus * 100.0), NamedTextColor.LIGHT_PURPLE));
        }
        if (broken) {
            lore.add(Component.empty());
            lore.add(Component.text("破損中: 修復するまで使用できません", NamedTextColor.DARK_RED));
        }
        meta.lore(lore);

        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        if (!broken && totalItemAttackSpeed != 0.0) {
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    attackSpeedModifierKey,
                    totalItemAttackSpeed,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                    EquipmentSlotGroup.MAINHAND));
        }
        item.setItemMeta(meta);
    }

    public void refreshInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) refreshWeapon(item);
        }
    }
}
