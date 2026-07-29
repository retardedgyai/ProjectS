package io.github.gyai.projects.manager;

import io.github.gyai.projects.item.Weapon;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class BalanceTuningManager {
    public static final double MAX_ATTACK_POWER = 10_000.0;
    public static final double MIN_ATTACK_SPEED = -0.9;
    public static final double MAX_ATTACK_SPEED = 5.0;
    public static final double MAX_SKILL_DAMAGE = 10_000.0;
    public static final double MAX_SKILL_SCALING = 100.0;

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final File overrideFile;
    private final Map<String, MutableWeapon> weapons = new LinkedHashMap<>();
    private final Map<String, MutableSkill> skills = new LinkedHashMap<>();
    private EnhancementManager enhancementManager;
    private long revision = 1;
    private boolean dirty;

    public BalanceTuningManager(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.overrideFile = new File(plugin.getDataFolder(), "balance-overrides.yml");
        for (Weapon weapon : itemManager.getWeapons()) {
            weapons.put(weapon.getId(), new MutableWeapon(
                    weapon.getId(), weapon.getDisplayName(),
                    weapon.getAttackDamage(), weapon.getAttackSpeedBonus()));
        }
    }

    public void setEnhancementManager(EnhancementManager enhancementManager) {
        this.enhancementManager = enhancementManager;
    }

    public void registerDamageSkill(
            String id,
            String displayName,
            double baseDamage,
            double attackPowerScaling
    ) {
        if (skills.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate balance skill: " + id);
        }
        skills.put(id, new MutableSkill(
                id, displayName, baseDamage, attackPowerScaling));
    }

    public DamageValues damageValues(String skillId) {
        MutableSkill value = skills.get(skillId);
        if (value == null) {
            throw new IllegalArgumentException("Unknown balance skill: " + skillId);
        }
        return new DamageValues(value.currentBaseDamage, value.currentScaling);
    }

    public double weaponAttackPower(String weaponId, double fallback) {
        MutableWeapon value = weapons.get(weaponId);
        return value == null ? fallback : value.currentAttackPower;
    }

    public double weaponAttackSpeed(String weaponId, double fallback) {
        MutableWeapon value = weapons.get(weaponId);
        return value == null ? fallback : value.currentAttackSpeed;
    }

    public Snapshot snapshot() {
        return new Snapshot(
                revision,
                dirty,
                weapons.values().stream().map(MutableWeapon::snapshot).toList(),
                skills.values().stream().map(MutableSkill::snapshot).toList());
    }

    public void loadOnEnable() {
        if (!overrideFile.isFile()) return;
        try {
            applyLoaded(YamlConfiguration.loadConfiguration(overrideFile), true);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning(
                    "balance-overrides.ymlの読み込みに失敗しました: "
                            + exception.getMessage());
        }
    }

    public OperationResult apply(long expectedRevision, List<Edit> edits) {
        if (!BalanceMath.revisionMatches(expectedRevision, revision)) {
            return OperationResult.failure("revision競合: 最新状態を再取得しました");
        }
        if (edits == null || edits.isEmpty() || edits.size() > 32) {
            return OperationResult.failure("編集項目数が不正です");
        }
        List<Runnable> changes = new ArrayList<>();
        List<String> changedWeaponIds = new ArrayList<>();
        for (Edit edit : edits) {
            String error = validateEdit(edit, changes, changedWeaponIds);
            if (error != null) return OperationResult.failure(error);
        }
        changes.forEach(Runnable::run);
        revision++;
        dirty = true;
        refreshWeapons(changedWeaponIds);
        return OperationResult.success("適用しました");
    }

    public OperationResult resetSelected(
            long expectedRevision,
            Target target,
            String id
    ) {
        if (!BalanceMath.revisionMatches(expectedRevision, revision)) {
            return OperationResult.failure("revision競合: 最新状態を再取得しました");
        }
        if (target == Target.WEAPON) {
            MutableWeapon value = weapons.get(id);
            if (value == null) return OperationResult.failure("不明な武器IDです");
            value.currentAttackPower = value.defaultAttackPower;
            value.currentAttackSpeed = value.defaultAttackSpeed;
            refreshWeapons(List.of(id));
        } else {
            MutableSkill value = skills.get(id);
            if (value == null) return OperationResult.failure("不明なスキルIDです");
            value.currentBaseDamage = value.defaultBaseDamage;
            value.currentScaling = value.defaultScaling;
        }
        revision++;
        dirty = true;
        return OperationResult.success("選択項目を初期値へ戻しました");
    }

    public OperationResult resetAll(long expectedRevision) {
        if (!BalanceMath.revisionMatches(expectedRevision, revision)) {
            return OperationResult.failure("revision競合: 最新状態を再取得しました");
        }
        weapons.values().forEach(MutableWeapon::reset);
        skills.values().forEach(MutableSkill::reset);
        revision++;
        dirty = true;
        refreshWeapons(new ArrayList<>(weapons.keySet()));
        return OperationResult.success("全項目を初期値へ戻しました");
    }

    public void saveAsync(Consumer<OperationResult> callback) {
        Snapshot captured = snapshot();
        YamlConfiguration output = createOverrideConfiguration(captured);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OperationResult result;
            try {
                File parent = overrideFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("保存先フォルダを作成できません");
                }
                output.save(overrideFile);
                result = OperationResult.success("保存しました");
            } catch (IOException exception) {
                plugin.getLogger().warning(
                        "バランス設定の保存に失敗しました: " + exception.getMessage());
                result = OperationResult.failure("保存に失敗しました");
            }
            OperationResult completed = result;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (completed.success() && revision == captured.revision()) {
                    dirty = false;
                }
                callback.accept(completed);
            });
        });
    }

    public void reloadAsync(Consumer<OperationResult> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            YamlConfiguration loaded = overrideFile.isFile()
                    ? YamlConfiguration.loadConfiguration(overrideFile)
                    : new YamlConfiguration();
            Bukkit.getScheduler().runTask(plugin, () -> {
                weapons.values().forEach(MutableWeapon::reset);
                skills.values().forEach(MutableSkill::reset);
                applyLoaded(loaded, true);
                revision++;
                dirty = false;
                refreshWeapons(new ArrayList<>(weapons.keySet()));
                callback.accept(OperationResult.success(
                        "保存済み状態を再読み込みしました"));
            });
        });
    }

    private String validateEdit(
            Edit edit,
            List<Runnable> changes,
            List<String> changedWeaponIds
    ) {
        if (edit == null || edit.id() == null || edit.id().length() > 64
                || !Double.isFinite(edit.value())) {
            return "不正な編集値です";
        }
        if (edit.target() == Target.WEAPON) {
            MutableWeapon weapon = weapons.get(edit.id());
            if (weapon == null) return "不明な武器IDです";
            if (edit.field() == Field.ATTACK_POWER) {
                if (!inRange(edit.value(), 0, MAX_ATTACK_POWER)) {
                    return "武器基礎攻撃力が範囲外です";
                }
                changes.add(() -> weapon.currentAttackPower = edit.value());
            } else if (edit.field() == Field.ATTACK_SPEED) {
                if (!inRange(edit.value(), MIN_ATTACK_SPEED, MAX_ATTACK_SPEED)) {
                    return "武器基礎攻撃速度が範囲外です";
                }
                changes.add(() -> weapon.currentAttackSpeed = edit.value());
            } else {
                return "武器の対象外フィールドです";
            }
            changedWeaponIds.add(edit.id());
            return null;
        }
        if (edit.target() == Target.SKILL) {
            MutableSkill skill = skills.get(edit.id());
            if (skill == null) return "不明なスキルIDです";
            if (edit.field() == Field.BASE_DAMAGE) {
                if (!inRange(edit.value(), 0, MAX_SKILL_DAMAGE)) {
                    return "スキル基礎ダメージが範囲外です";
                }
                changes.add(() -> skill.currentBaseDamage = edit.value());
            } else if (edit.field() == Field.ATTACK_POWER_SCALING) {
                if (!inRange(edit.value(), 0, MAX_SKILL_SCALING)) {
                    return "攻撃力反映率が範囲外です";
                }
                changes.add(() -> skill.currentScaling = edit.value());
            } else {
                return "スキルの対象外フィールドです";
            }
            return null;
        }
        return "不明な編集対象です";
    }

    private void applyLoaded(YamlConfiguration input, boolean warn) {
        ConfigurationSection weaponSection = input.getConfigurationSection("weapons");
        if (weaponSection != null) {
            for (String id : weaponSection.getKeys(false)) {
                MutableWeapon weapon = weapons.get(id);
                if (weapon == null) {
                    warning(warn, "不明な武器IDを無視しました: " + id);
                    continue;
                }
                Double attack = finiteNumber(weaponSection, id + ".base-attack-power");
                Double speed = finiteNumber(weaponSection, id + ".base-attack-speed-bonus");
                if (attack != null && inRange(attack, 0, MAX_ATTACK_POWER)) {
                    weapon.currentAttackPower = attack;
                } else if (attack != null) warning(warn, "武器攻撃力が範囲外です: " + id);
                if (speed != null && inRange(speed, MIN_ATTACK_SPEED, MAX_ATTACK_SPEED)) {
                    weapon.currentAttackSpeed = speed;
                } else if (speed != null) warning(warn, "武器攻撃速度が範囲外です: " + id);
            }
        }
        ConfigurationSection skillSection = input.getConfigurationSection("skills");
        if (skillSection != null) {
            for (String id : skillSection.getKeys(false)) {
                MutableSkill skill = skills.get(id);
                if (skill == null) {
                    warning(warn, "不明なスキルIDを無視しました: " + id);
                    continue;
                }
                Double damage = finiteNumber(skillSection, id + ".base-damage");
                Double scaling = finiteNumber(skillSection, id + ".attack-power-scaling");
                if (damage != null && inRange(damage, 0, MAX_SKILL_DAMAGE)) {
                    skill.currentBaseDamage = damage;
                } else if (damage != null) warning(warn, "スキルダメージが範囲外です: " + id);
                if (scaling != null && inRange(scaling, 0, MAX_SKILL_SCALING)) {
                    skill.currentScaling = scaling;
                } else if (scaling != null) warning(warn, "スキル反映率が範囲外です: " + id);
            }
        }
    }

    private Double finiteNumber(ConfigurationSection section, String path) {
        if (!section.contains(path)) return null;
        Object raw = section.get(path);
        if (!(raw instanceof Number number)) {
            plugin.getLogger().warning("非数値のバランス設定を無視しました: " + path);
            return null;
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value)) {
            plugin.getLogger().warning("有限でないバランス設定を無視しました: " + path);
            return null;
        }
        return value;
    }

    private YamlConfiguration createOverrideConfiguration(Snapshot captured) {
        YamlConfiguration output = new YamlConfiguration();
        for (WeaponValue weapon : captured.weapons()) {
            if (Double.compare(weapon.defaultAttackPower(), weapon.currentAttackPower()) != 0) {
                output.set("weapons." + weapon.id() + ".base-attack-power",
                        weapon.currentAttackPower());
            }
            if (Double.compare(weapon.defaultAttackSpeed(), weapon.currentAttackSpeed()) != 0) {
                output.set("weapons." + weapon.id() + ".base-attack-speed-bonus",
                        weapon.currentAttackSpeed());
            }
        }
        for (SkillValue skill : captured.skills()) {
            if (Double.compare(skill.defaultBaseDamage(), skill.currentBaseDamage()) != 0) {
                output.set("skills." + skill.id() + ".base-damage",
                        skill.currentBaseDamage());
            }
            if (Double.compare(skill.defaultScaling(), skill.currentScaling()) != 0) {
                output.set("skills." + skill.id() + ".attack-power-scaling",
                        skill.currentScaling());
            }
        }
        return output;
    }

    private void refreshWeapons(List<String> ids) {
        if (enhancementManager == null || ids.isEmpty()) return;
        Bukkit.getOnlinePlayers().forEach(player -> {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && ids.contains(itemManager.getItemId(item))) {
                    enhancementManager.refreshWeapon(item);
                }
            }
        });
    }

    private void warning(boolean enabled, String message) {
        if (enabled) plugin.getLogger().warning(message);
    }

    private static boolean inRange(double value, double minimum, double maximum) {
        return BalanceMath.finiteInRange(value, minimum, maximum);
    }

    public enum Target { WEAPON, SKILL }
    public enum Field {
        ATTACK_POWER, ATTACK_SPEED, BASE_DAMAGE, ATTACK_POWER_SCALING
    }
    public record Edit(Target target, String id, Field field, double value) { }
    public record DamageValues(double baseDamage, double attackPowerScaling) { }
    public record OperationResult(boolean success, String message) {
        public static OperationResult success(String message) {
            return new OperationResult(true, message);
        }
        public static OperationResult failure(String message) {
            return new OperationResult(false, message);
        }
    }
    public record Snapshot(
            long revision,
            boolean dirty,
            List<WeaponValue> weapons,
            List<SkillValue> skills
    ) { }
    public record WeaponValue(
            String id,
            String displayName,
            double defaultAttackPower,
            double currentAttackPower,
            double defaultAttackSpeed,
            double currentAttackSpeed
    ) { }
    public record SkillValue(
            String id,
            String displayName,
            double defaultBaseDamage,
            double currentBaseDamage,
            double defaultScaling,
            double currentScaling
    ) { }

    private static final class MutableWeapon {
        private final String id;
        private final String displayName;
        private final double defaultAttackPower;
        private final double defaultAttackSpeed;
        private double currentAttackPower;
        private double currentAttackSpeed;

        private MutableWeapon(
                String id, String displayName,
                double attackPower, double attackSpeed
        ) {
            this.id = Objects.requireNonNull(id);
            this.displayName = Objects.requireNonNull(displayName);
            this.defaultAttackPower = attackPower;
            this.defaultAttackSpeed = attackSpeed;
            reset();
        }

        private void reset() {
            currentAttackPower = defaultAttackPower;
            currentAttackSpeed = defaultAttackSpeed;
        }

        private WeaponValue snapshot() {
            return new WeaponValue(id, displayName,
                    defaultAttackPower, currentAttackPower,
                    defaultAttackSpeed, currentAttackSpeed);
        }
    }

    private static final class MutableSkill {
        private final String id;
        private final String displayName;
        private final double defaultBaseDamage;
        private final double defaultScaling;
        private double currentBaseDamage;
        private double currentScaling;

        private MutableSkill(
                String id, String displayName,
                double baseDamage, double scaling
        ) {
            this.id = Objects.requireNonNull(id);
            this.displayName = Objects.requireNonNull(displayName);
            this.defaultBaseDamage = baseDamage;
            this.defaultScaling = scaling;
            reset();
        }

        private void reset() {
            currentBaseDamage = defaultBaseDamage;
            currentScaling = defaultScaling;
        }

        private SkillValue snapshot() {
            return new SkillValue(id, displayName,
                    defaultBaseDamage, currentBaseDamage,
                    defaultScaling, currentScaling);
        }
    }
}
