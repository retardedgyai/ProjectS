package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.crafting.OutputProposal;
import io.github.gyai.projects.crafting.RecipeDefinitionV1;
import io.github.gyai.projects.equipment.BindingPolicy;
import io.github.gyai.projects.equipment.EquipmentCategory;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentModSlot;
import io.github.gyai.projects.equipment.EquipmentQuality;
import io.github.gyai.projects.equipment.EquipmentRarity;
import io.github.gyai.projects.equipment.EquipmentSlot;
import io.github.gyai.projects.equipment.EquipmentTier;
import io.github.gyai.projects.equipment.TradePolicy;
import io.github.gyai.projects.schema.SchemaVersions;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Fixed Activation-Wave-1 staging fixtures. This is not a production catalog. */
public final class StagingEconomyCatalog {
    public static final String IRON_ORE = "projects:staging/iron-ore";
    public static final String IRON_INGOT = "projects:staging/iron-ingot";
    public static final String TEST_BLADE_T1 = "projects:staging/test-blade";
    public static final String TEST_BLADE_T2 = "projects:staging/test-blade-t2";
    public static final String TEST_TOKEN = "projects:staging/test-token";
    public static final String TEST_BLADE_FAMILY = "projects:staging-test-blade";

    public static final String REFINE_RECIPE_ID = "projects:staging-refine-iron";
    public static final String CRAFT_RECIPE_ID = "projects:staging-craft-test-blade";
    public static final String PROMOTION_RECIPE_ID = "projects:staging-promote-test-blade";
    public static final String ENHANCEMENT_POLICY_ID = "projects:staging-enhancement";
    public static final String REPAIR_POLICY_ID = "projects:staging-repair";
    public static final String IRON_ORE_TRANSACTION_KEY = "projects:staging-iron-ore";
    public static final String IRON_INGOT_TRANSACTION_KEY = "projects:staging-iron-ingot";

    private static final Set<String> IDS = Set.of(
            IRON_ORE, IRON_INGOT, TEST_BLADE_T1, TEST_BLADE_T2, TEST_TOKEN);

    private StagingEconomyCatalog() {
    }

    public static Set<String> itemIds() {
        return IDS;
    }

    public static boolean isStagingItem(String itemId) {
        return itemId != null && IDS.contains(itemId);
    }

    /** Track E resource plans cannot contain '/'; this is an adapter key, not an item ID. */
    public static String transactionResourceKey(String itemId) {
        return switch (itemId) {
            case IRON_ORE -> IRON_ORE_TRANSACTION_KEY;
            case IRON_INGOT -> IRON_INGOT_TRANSACTION_KEY;
            default -> throw new IllegalArgumentException("item is not a staging material");
        };
    }

    public static String itemIdForTransactionResource(String key) {
        return switch (key) {
            case IRON_ORE_TRANSACTION_KEY -> IRON_ORE;
            case IRON_INGOT_TRANSACTION_KEY -> IRON_INGOT;
            default -> key;
        };
    }

    public static RecipeDefinitionV1 refineRecipe() {
        return recipe(REFINE_RECIPE_ID, RecipeDefinitionV1.RecipeType.REFINE_DIRECT,
                List.of(input(IRON_ORE, 2)), new OutputProposal(IRON_INGOT, 1, false));
    }

    public static RecipeDefinitionV1 craftRecipe() {
        return recipe(CRAFT_RECIPE_ID, RecipeDefinitionV1.RecipeType.CRAFT_EQUIPMENT_BASE,
                List.of(input(IRON_INGOT, 3)), new OutputProposal(TEST_BLADE_T1, 1, true));
    }

    public static EquipmentItemV1 previewBlade(EquipmentTier tier) {
        if (tier != EquipmentTier.T1 && tier != EquipmentTier.T2) {
            throw new IllegalArgumentException("staging blade supports T1/T2 only");
        }
        return new EquipmentItemV1(
                SchemaVersions.EQUIPMENT_ITEM,
                tier == EquipmentTier.T1 ? TEST_BLADE_T1 : TEST_BLADE_T2,
                EquipmentCategory.WEAPON,
                EquipmentSlot.WEAPON,
                tier,
                tier == EquipmentTier.T1 ? 1 : 16,
                EquipmentRarity.COMMON,
                EquipmentQuality.UNSPECIFIED,
                List.of(),
                List.of(EquipmentModSlot.empty(0)),
                Optional.empty(),
                0,
                false,
                BindingPolicy.UNBOUND,
                TradePolicy.DENY_ALL,
                Optional.empty());
    }

    private static RecipeDefinitionV1 recipe(
            String id,
            RecipeDefinitionV1.RecipeType type,
            List<RecipeDefinitionV1.Input> inputs,
            OutputProposal output
    ) {
        return new RecipeDefinitionV1(
                SchemaVersions.RECIPE_DEFINITION,
                1,
                id,
                type,
                inputs,
                Optional.empty(),
                output,
                RecipeDefinitionV1.FeePlaceholder.unspecified(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static RecipeDefinitionV1.Input input(String id, long quantity) {
        return new RecipeDefinitionV1.Input(
                id, quantity, RecipeDefinitionV1.InputDisposition.REFUNDABLE);
    }
}
