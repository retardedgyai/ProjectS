package io.github.gyai.projects.monster.editor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class MobDefinitionValidator {
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,64}");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Set<String> DYE_COLORS = Set.of(
            "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME",
            "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE",
            "BROWN", "GREEN", "RED", "BLACK");
    private static final Set<String> GLOW_COLORS = Set.of(
            "BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_AQUA", "DARK_RED",
            "DARK_PURPLE", "GOLD", "GRAY", "DARK_GRAY", "BLUE", "GREEN",
            "AQUA", "RED", "LIGHT_PURPLE", "YELLOW", "WHITE");
    private static final Set<String> WOLF_VARIANTS = Set.of(
            "PALE", "SPOTTED", "SNOWY", "BLACK", "ASHEN", "RUSTY",
            "WOODS", "CHESTNUT", "STRIPED");
    private static final Set<String> CAT_VARIANTS = Set.of(
            "TABBY", "BLACK", "RED", "SIAMESE", "BRITISH_SHORTHAIR",
            "CALICO", "PERSIAN", "RAGDOLL", "WHITE", "JELLIE", "ALL_BLACK");
    private static final Set<String> HORSE_COLORS = Set.of(
            "WHITE", "CREAMY", "CHESTNUT", "BROWN", "BLACK", "GRAY", "DARK_BROWN");
    private static final Set<String> VILLAGER_PROFESSIONS = Set.of(
            "NONE", "ARMORER", "BUTCHER", "CARTOGRAPHER", "CLERIC", "FARMER",
            "FISHERMAN", "FLETCHER", "LEATHERWORKER", "LIBRARIAN", "MASON",
            "NITWIT", "SHEPHERD", "TOOLSMITH", "WEAPONSMITH");
    private static final Set<String> VILLAGER_TYPES = Set.of(
            "DESERT", "JUNGLE", "PLAINS", "SAVANNA", "SNOW", "SWAMP", "TAIGA");

    private final Set<String> livingEntityTypes;
    private final Predicate<String> materialExists;
    private final Predicate<String> projectsItemExists;
    private final Function<String, String> projectsItemMaterial;
    private final Predicate<String> headExists;

    public MobDefinitionValidator(
            Set<String> livingEntityTypes,
            Predicate<String> materialExists,
            Predicate<String> projectsItemExists,
            Predicate<String> headExists
    ) {
        this(livingEntityTypes, materialExists, projectsItemExists,
                ignored -> "", headExists);
    }

    public MobDefinitionValidator(
            Set<String> livingEntityTypes,
            Predicate<String> materialExists,
            Predicate<String> projectsItemExists,
            Function<String, String> projectsItemMaterial,
            Predicate<String> headExists
    ) {
        this.livingEntityTypes = Set.copyOf(livingEntityTypes);
        this.materialExists = materialExists;
        this.projectsItemExists = projectsItemExists;
        this.projectsItemMaterial = projectsItemMaterial;
        this.headExists = headExists;
    }

    public ValidationResult validate(MobDefinition definition) {
        ArrayList<String> errors = new ArrayList<>();
        if (definition == null) return new ValidationResult(
                java.util.List.of("モブ定義がありません"));
        if (definition.schemaVersion() != MobDefinition.SCHEMA_VERSION) {
            errors.add("schema-versionが未対応です");
        }
        if (!ID.matcher(safe(definition.id())).matches()) {
            errors.add("内部IDは英小文字、数字、_、-のみ使用できます");
        }
        if (safe(definition.displayName()).isBlank()
                || utf8Length(definition.displayName()) > 128) {
            errors.add("表示名が空か長すぎます");
        }
        String entityType = upper(definition.entityType());
        if (!livingEntityTypes.contains(entityType)) {
            errors.add("LivingEntityとして利用できないEntityTypeです");
        }
        if (definition.category() == null || definition.nameplateMode() == null) {
            errors.add("カテゴリまたはネームプレート設定が不正です");
        }
        if (definition.level() < 1 || definition.level() > 999) {
            errors.add("レベルは1～999で指定してください");
        }
        validateTags(definition.tags(), errors);
        validateStats(definition.stats(), errors);
        validateAttack(definition.basicAttack(), errors);
        validateAi(definition.ai(), errors);
        validateAppearance(entityType, definition.appearance(), errors);
        return new ValidationResult(errors);
    }

    private void validateStats(MobStatsDefinition stats, ArrayList<String> errors) {
        if (stats == null) {
            errors.add("Statsがありません");
            return;
        }
        finiteRange("最大HP", stats.maxHealth(), 1, 1_024, errors);
        finiteRange("物理攻撃力", stats.physicalAttack(), 0, 1_000_000_000, errors);
        finiteRange("魔法攻撃力", stats.magicalAttack(), 0, 1_000_000_000, errors);
        finiteRange("物理防御力", stats.physicalDefense(), 0, 1_000_000_000, errors);
        finiteRange("魔法防御力", stats.magicalDefense(), 0, 1_000_000_000, errors);
        finiteRange("移動速度", stats.movementSpeed(), .01, 10, errors);
        finiteRange("攻撃速度", stats.attackSpeed(), .05, 20, errors);
        finiteRange("クリティカル率", stats.criticalChance(), 0, 1, errors);
        finiteRange("クリティカル倍率", stats.criticalDamage(), 1, 10, errors);
        finiteRange("被ダメージ軽減", stats.damageReduction(), 0, .8, errors);
    }

    private void validateAttack(
            MobBasicAttackDefinition attack,
            ArrayList<String> errors
    ) {
        if (attack == null || attack.damageType() == null) {
            errors.add("通常攻撃設定がありません");
            return;
        }
        finiteRange("固定ダメージ", attack.fixedDamage(), 0, 1_000_000_000, errors);
        finiteRange("攻撃力係数", attack.coefficient(), 0, 100, errors);
        finiteRange("攻撃間隔", attack.intervalSeconds(), .05, 600, errors);
        finiteRange("攻撃距離", attack.range(), .1, 128, errors);
        finiteRange("ノックバック", attack.knockback(), 0, 10, errors);
    }

    private void validateAi(MobAiDefinition ai, ArrayList<String> errors) {
        if (ai == null || ai.preset() == null || ai.targetPriority() == null) {
            errors.add("AI設定がありません");
            return;
        }
        finiteRange("索敵距離", ai.aggroRange(), 0, 128, errors);
        finiteRange("追跡距離", ai.chaseRange(), 0, 256, errors);
        finiteRange("帰還距離", ai.leashRange(), 0, 512, errors);
        finiteRange("AI攻撃距離", ai.attackRange(), .1, 128, errors);
        finiteRange("再検索間隔", ai.targetRefreshSeconds(), .05, 60, errors);
        if (Double.isFinite(ai.aggroRange()) && Double.isFinite(ai.chaseRange())
                && Double.isFinite(ai.leashRange())
                && (ai.aggroRange() > ai.chaseRange()
                || ai.chaseRange() > ai.leashRange())) {
            errors.add("AI距離は索敵≦追跡≦帰還にしてください");
        }
    }

    private void validateAppearance(
            String entityType,
            MobAppearanceDefinition appearance,
            ArrayList<String> errors
    ) {
        if (appearance == null || appearance.age() == null) {
            errors.add("外見設定がありません");
            return;
        }
        finiteRange("モデルスケール", appearance.scale(), .25, 4, errors);
        if (!GLOW_COLORS.contains(upper(appearance.glowingColor()))) {
            errors.add("発光色が不正です");
        }
        if (appearance.age() == MobAppearanceDefinition.Age.BABY
                && !AppearanceCapabilityRegistry.supportsBaby(entityType)) {
            errors.add("このEntityTypeではBABYを利用できません");
        }
        Set<String> allowedVariants =
                AppearanceCapabilityRegistry.supportedVariants(entityType);
        for (var entry : appearance.variants().entrySet()) {
            if (!allowedVariants.contains(entry.getKey())) {
                errors.add("このEntityTypeでは外見項目「"
                        + entry.getKey() + "」を利用できません");
            } else if (utf8Length(entry.getValue()) > 64 || hasControl(entry.getValue())) {
                errors.add("外見項目の値が不正です");
            } else if (!validVariantValue(entityType, entry.getKey(), entry.getValue())) {
                errors.add("外見項目「" + entry.getKey() + "」の値が不正です");
            }
        }
        boolean supportsEquipment =
                AppearanceCapabilityRegistry.supportsEquipment(entityType);
        for (var entry : appearance.equipment().entrySet()) {
            validateEquipment(
                    supportsEquipment, entry.getKey(), entry.getValue(), errors);
        }
    }

    private static boolean validVariantValue(
            String entityType,
            String key,
            String value
    ) {
        String normalized = upper(value);
        return switch (entityType) {
            case "SLIME" -> key.equals("size") && integerRange(value, 1, 127);
            case "SHEEP" -> key.equals("color") ? DYE_COLORS.contains(normalized)
                    : key.equals("sheared") && booleanValue(value);
            case "WOLF" -> switch (key) {
                case "variant" -> WOLF_VARIANTS.contains(normalized);
                case "collar-color" -> DYE_COLORS.contains(normalized);
                case "angry" -> booleanValue(value);
                default -> false;
            };
            case "CAT" -> key.equals("variant")
                    ? CAT_VARIANTS.contains(normalized)
                    : key.equals("collar-color") && DYE_COLORS.contains(normalized);
            case "HORSE" -> key.equals("color") && HORSE_COLORS.contains(normalized);
            case "VILLAGER" -> key.equals("profession")
                    ? VILLAGER_PROFESSIONS.contains(normalized)
                    : key.equals("villager-type") && VILLAGER_TYPES.contains(normalized);
            default -> false;
        };
    }

    private static boolean integerRange(String value, int minimum, int maximum) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= minimum && parsed <= maximum;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean booleanValue(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    private void validateEquipment(
            boolean supportsEquipment,
            MobAppearanceDefinition.Slot slot,
            MobEquipmentEntry equipment,
            ArrayList<String> errors
    ) {
        if (equipment == null || equipment.sourceType() == null) {
            errors.add("装備スロットが不正です");
            return;
        }
        if (equipment.sourceType() != MobEquipmentEntry.SourceType.NONE
                && !supportsEquipment) {
            errors.add("このEntityTypeでは装備を利用できません");
        }
        if (!equipment.visualOnly()) {
            errors.add("MVPの装備はvisual-onlyである必要があります");
        }
        switch (equipment.sourceType()) {
            case NONE -> {
                if (!safe(equipment.referenceId()).isBlank()
                        || !safe(equipment.material()).isBlank()
                        || !safe(equipment.color()).isBlank()
                        || equipment.glint()) {
                    errors.add("NONE装備に未使用の値は設定できません");
                }
            }
            case VANILLA_ITEM -> {
                String material = upper(equipment.material());
                if (!safe(equipment.referenceId()).isBlank()) {
                    errors.add("VANILLA_ITEMにreference-idは設定できません");
                }
                if (!materialExists.test(material)) {
                    errors.add("存在しないMaterialです: " + material);
                } else if (!compatible(slot, material)) {
                    errors.add("装備スロットとMaterialが一致しません");
                }
                validateLeatherColor(material, equipment.color(), errors);
            }
            case PROJECTS_ITEM -> {
                String itemId = safe(equipment.referenceId());
                if (!safe(equipment.material()).isBlank()) {
                    errors.add("PROJECTS_ITEMにmaterialは設定できません");
                }
                if (!projectsItemExists.test(itemId)) {
                    errors.add("存在しないProjectS Item IDです");
                } else {
                    String material = upper(projectsItemMaterial.apply(itemId));
                    if (!material.isBlank() && !compatible(slot, material)) {
                        errors.add("装備スロットとProjectS Itemが一致しません");
                    }
                    validateLeatherColor(material, equipment.color(), errors);
                }
            }
            case CUSTOM_HEAD -> {
                if (!safe(equipment.material()).isBlank()
                        || !safe(equipment.color()).isBlank()) {
                    errors.add("CUSTOM_HEADにmaterialやcolorは設定できません");
                }
                if (slot != MobAppearanceDefinition.Slot.HEAD) {
                    errors.add("カスタムヘッドはHEADにのみ設定できます");
                }
                if (!headExists.test(safe(equipment.referenceId()))) {
                    errors.add("存在しないHead IDです");
                }
            }
        }
        if (!safe(equipment.color()).isBlank()
                && !COLOR.matcher(equipment.color()).matches()) {
            errors.add("革防具色は#RRGGBBで指定してください");
        }
    }

    private static void validateLeatherColor(
            String material,
            String color,
            ArrayList<String> errors
    ) {
        if (!safe(color).isBlank() && !material.startsWith("LEATHER_")) {
            errors.add("革防具以外には染色を設定できません");
        }
    }

    private static boolean compatible(
            MobAppearanceDefinition.Slot slot,
            String material
    ) {
        return switch (slot) {
            case HEAD -> material.endsWith("_HELMET")
                    || material.endsWith("_HEAD") || material.endsWith("_SKULL")
                    || material.equals("CARVED_PUMPKIN");
            case CHEST -> material.endsWith("_CHESTPLATE") || material.equals("ELYTRA");
            case LEGS -> material.endsWith("_LEGGINGS");
            case FEET -> material.endsWith("_BOOTS");
            case MAIN_HAND, OFF_HAND -> true;
        };
    }

    private static void validateTags(java.util.List<String> tags, ArrayList<String> errors) {
        if (tags == null || tags.size() > 32) {
            errors.add("タグは32件以下にしてください");
            return;
        }
        HashSet<String> unique = new HashSet<>();
        for (String tag : tags) {
            if (safe(tag).isBlank() || utf8Length(tag) > 32
                    || hasControl(tag) || !unique.add(tag)) {
                errors.add("タグが空、重複、または長すぎます");
                return;
            }
        }
    }

    private static void finiteRange(
            String name,
            double value,
            double minimum,
            double maximum,
            ArrayList<String> errors
    ) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            errors.add(name + "が範囲外です");
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String upper(String value) {
        return safe(value).trim().toUpperCase(Locale.ROOT);
    }

    private static int utf8Length(String value) {
        return safe(value).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private static boolean hasControl(String value) {
        return safe(value).chars().anyMatch(character ->
                Character.isISOControl(character) && !Character.isWhitespace(character));
    }
}
