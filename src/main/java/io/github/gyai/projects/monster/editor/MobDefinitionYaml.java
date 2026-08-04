package io.github.gyai.projects.monster.editor;

import io.github.gyai.projects.combat.damage.DamageType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MobDefinitionYaml {
    private MobDefinitionYaml() {
    }

    static MobDefinition read(YamlConfiguration yaml) {
        MobStatsDefinition stats = new MobStatsDefinition(
                yaml.getDouble("stats.max-health"),
                yaml.getDouble("stats.physical-attack"),
                yaml.getDouble("stats.magical-attack"),
                yaml.getDouble("stats.physical-defense"),
                yaml.getDouble("stats.magical-defense"),
                yaml.getDouble("stats.move-speed"),
                yaml.getDouble("stats.attack-speed"),
                yaml.getDouble("stats.critical-chance"),
                yaml.getDouble("stats.critical-damage"),
                yaml.getDouble("stats.damage-reduction"));
        MobBasicAttackDefinition attack = new MobBasicAttackDefinition(
                enumValue(DamageType.class,
                        yaml.getString("basic-attack.damage-type")),
                yaml.getDouble("basic-attack.fixed-damage"),
                yaml.getDouble("basic-attack.coefficient"),
                yaml.getDouble("basic-attack.interval-seconds"),
                yaml.getDouble("basic-attack.range"),
                yaml.getDouble("basic-attack.knockback"),
                yaml.getBoolean("basic-attack.critical-allowed"));
        MobAiDefinition ai = new MobAiDefinition(
                enumValue(MobAiDefinition.Preset.class,
                        yaml.getString("ai.preset")),
                enumValue(MobAiDefinition.TargetPriority.class,
                        yaml.getString("ai.target-priority")),
                yaml.getDouble("ai.aggro-range"),
                yaml.getDouble("ai.chase-range"),
                yaml.getDouble("ai.leash-range"),
                yaml.getDouble("ai.attack-range"),
                yaml.getDouble("ai.target-refresh-seconds"),
                yaml.getBoolean("ai.return-home"),
                yaml.getBoolean("ai.reset-health-on-return"),
                yaml.getBoolean("ai.avoid-falls"),
                yaml.getBoolean("ai.avoid-water"));
        Map<String, String> variants = new LinkedHashMap<>();
        ConfigurationSection variantSection =
                yaml.getConfigurationSection("appearance.variants");
        if (variantSection != null) {
            for (String key : variantSection.getKeys(false)) {
                variants.put(key, variantSection.getString(key, ""));
            }
        }
        EnumMap<MobAppearanceDefinition.Slot, MobEquipmentEntry> equipment =
                new EnumMap<>(MobAppearanceDefinition.Slot.class);
        for (MobAppearanceDefinition.Slot slot : MobAppearanceDefinition.Slot.values()) {
            String path = "appearance.equipment." + key(slot) + ".";
            equipment.put(slot, new MobEquipmentEntry(
                    enumValue(MobEquipmentEntry.SourceType.class,
                            yaml.getString(path + "source-type", "NONE")),
                    yaml.getString(path + "reference-id", ""),
                    yaml.getString(path + "material", ""),
                    yaml.getString(path + "color", ""),
                    yaml.getBoolean(path + "glint"),
                    yaml.getBoolean(path + "visible", true),
                    yaml.getBoolean(path + "visual-only", true)));
        }
        MobAppearanceDefinition appearance = new MobAppearanceDefinition(
                yaml.getDouble("appearance.scale"),
                enumValue(MobAppearanceDefinition.Age.class,
                        yaml.getString("appearance.age")),
                yaml.getBoolean("appearance.glowing.enabled"),
                yaml.getString("appearance.glowing.color", "WHITE"),
                variants, equipment);
        return new MobDefinition(
                yaml.getInt("schema-version"), yaml.getLong("revision"),
                yaml.getString("id", ""), yaml.getString("display-name", ""),
                yaml.getString("entity-type", ""),
                enumValue(MobDefinition.Category.class,
                        yaml.getString("category")),
                yaml.getBoolean("enabled"), yaml.getInt("level"),
                enumValue(MobDefinition.NameplateMode.class,
                        yaml.getString("nameplate.mode")),
                yaml.getStringList("tags"), stats, attack, ai, appearance);
    }

    static YamlConfiguration write(MobDefinition definition) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", definition.schemaVersion());
        yaml.set("revision", definition.revision());
        yaml.set("id", definition.id());
        yaml.set("display-name", definition.displayName());
        yaml.set("entity-type", definition.entityType());
        yaml.set("category", definition.category().name());
        yaml.set("enabled", definition.enabled());
        yaml.set("level", definition.level());
        yaml.set("nameplate.mode", definition.nameplateMode().name());
        yaml.set("tags", definition.tags());
        MobStatsDefinition stats = definition.stats();
        yaml.set("stats.max-health", stats.maxHealth());
        yaml.set("stats.physical-attack", stats.physicalAttack());
        yaml.set("stats.magical-attack", stats.magicalAttack());
        yaml.set("stats.physical-defense", stats.physicalDefense());
        yaml.set("stats.magical-defense", stats.magicalDefense());
        yaml.set("stats.move-speed", stats.movementSpeed());
        yaml.set("stats.attack-speed", stats.attackSpeed());
        yaml.set("stats.critical-chance", stats.criticalChance());
        yaml.set("stats.critical-damage", stats.criticalDamage());
        yaml.set("stats.damage-reduction", stats.damageReduction());
        MobBasicAttackDefinition attack = definition.basicAttack();
        yaml.set("basic-attack.damage-type", attack.damageType().name());
        yaml.set("basic-attack.fixed-damage", attack.fixedDamage());
        yaml.set("basic-attack.coefficient", attack.coefficient());
        yaml.set("basic-attack.interval-seconds", attack.intervalSeconds());
        yaml.set("basic-attack.range", attack.range());
        yaml.set("basic-attack.knockback", attack.knockback());
        yaml.set("basic-attack.critical-allowed", attack.criticalAllowed());
        MobAiDefinition ai = definition.ai();
        yaml.set("ai.preset", ai.preset().name());
        yaml.set("ai.target-priority", ai.targetPriority().name());
        yaml.set("ai.aggro-range", ai.aggroRange());
        yaml.set("ai.chase-range", ai.chaseRange());
        yaml.set("ai.leash-range", ai.leashRange());
        yaml.set("ai.attack-range", ai.attackRange());
        yaml.set("ai.target-refresh-seconds", ai.targetRefreshSeconds());
        yaml.set("ai.return-home", ai.returnHome());
        yaml.set("ai.reset-health-on-return", ai.resetHealthOnReturn());
        yaml.set("ai.avoid-falls", ai.avoidFalls());
        yaml.set("ai.avoid-water", ai.avoidWater());
        MobAppearanceDefinition appearance = definition.appearance();
        yaml.set("appearance.scale", appearance.scale());
        yaml.set("appearance.age", appearance.age().name());
        yaml.set("appearance.glowing.enabled", appearance.glowing());
        yaml.set("appearance.glowing.color", appearance.glowingColor());
        appearance.variants().forEach((key, value) ->
                yaml.set("appearance.variants." + key, value));
        appearance.equipment().forEach((slot, entry) -> {
            String path = "appearance.equipment." + key(slot) + ".";
            yaml.set(path + "source-type", entry.sourceType().name());
            yaml.set(path + "reference-id", entry.referenceId());
            yaml.set(path + "material", entry.material());
            yaml.set(path + "color", entry.color());
            yaml.set(path + "glint", entry.glint());
            yaml.set(path + "visible", entry.visible());
            yaml.set(path + "visual-only", true);
        });
        return yaml;
    }

    private static String key(MobAppearanceDefinition.Slot slot) {
        return slot.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
