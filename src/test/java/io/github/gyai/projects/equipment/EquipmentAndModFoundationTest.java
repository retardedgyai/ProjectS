package io.github.gyai.projects.equipment;

import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.feature.FeatureFlagService;
import io.github.gyai.projects.feature.FeatureKey;
import io.github.gyai.projects.mod.*;
import io.github.gyai.projects.schema.SchemaVersions;

import java.util.*;

public final class EquipmentAndModFoundationTest {
    private EquipmentAndModFoundationTest() { }

    public static void main(String[] args) {
        slotTierAndRarityContract();
        immutableEquipmentAndIdentity();
        invalidEquipmentIsRejected();
        modDefinitionAndIsolation();
        flagsRemainDisabled();
    }

    private static void slotTierAndRarityContract() {
        assert EquipmentSlot.values().length == 8;
        assert Arrays.stream(EquipmentSlot.values()).map(EquipmentSlot::id).distinct().count() == 8;
        for (int level = 1; level <= 45; level++) {
            int matches = 0;
            for (EquipmentTier tier : EquipmentTier.values()) if (tier.contains(level)) matches++;
            assert matches == 1;
        }
        assert !EquipmentTier.T1.contains(0) && !EquipmentTier.T3.contains(46);
        assert EquipmentRarity.COMMON.modCapacity() == 1;
        assert EquipmentRarity.UNCOMMON.modCapacity() == 2;
        assert EquipmentRarity.RARE.modCapacity() == 3;
        assert EquipmentRarity.EPIC.modCapacity() == 4;
        assert EquipmentQuality.values().length == 1;
        assert EquipmentQuality.UNSPECIFIED.name().equals("UNSPECIFIED");
    }

    private static void immutableEquipmentAndIdentity() {
        ArrayList<BaseStatRoll> rolls = new ArrayList<>(List.of(
                new BaseStatRoll("projects:physical-attack", 12.5)));
        ArrayList<EquipmentModSlot> slots = new ArrayList<>(List.of(
                EquipmentModSlot.empty(0), EquipmentModSlot.empty(1)));
        UUID itemId = UUID.randomUUID();
        EquipmentItemV1 item = item(EquipmentRarity.UNCOMMON, rolls, slots, Optional.of(itemId));
        rolls.clear();
        slots.clear();
        assert item.baseStatRolls().size() == 1;
        assert item.modSlots().size() == 2;
        assert item.instanceId().orElseThrow().equals(itemId);
        assert item.instanceId().orElseThrow().equals(item.instanceId().orElseThrow());
        assertThrows(UnsupportedOperationException.class,
                () -> item.baseStatRolls().add(new BaseStatRoll("projects:defense", 1)));

        EquipmentItemV1 stackableIdentityOmitted = item(
                EquipmentRarity.COMMON,
                List.of(new BaseStatRoll("projects:physical-attack", 1)),
                List.of(EquipmentModSlot.empty(0)), Optional.empty());
        assert stackableIdentityOmitted.instanceId().isEmpty();
    }

    private static void invalidEquipmentIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BaseStatRoll("projects:x", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new BaseStatRoll("projects:x", Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentItemV1(
                SchemaVersions.EQUIPMENT_ITEM, "bad", EquipmentCategory.WEAPON,
                EquipmentSlot.WEAPON, EquipmentTier.T1, 16,
                EquipmentRarity.COMMON, EquipmentQuality.UNSPECIFIED,
                List.of(), List.of(EquipmentModSlot.empty(0)), Optional.empty(),
                0, false, BindingPolicy.UNBOUND, TradePolicy.DENY_ALL, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> item(
                EquipmentRarity.UNCOMMON, List.of(),
                List.of(EquipmentModSlot.empty(0), EquipmentModSlot.empty(0)), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentItemV1(
                SchemaVersions.EQUIPMENT_ITEM, "bad", EquipmentCategory.ARMOR,
                EquipmentSlot.WEAPON, EquipmentTier.T1, 1,
                EquipmentRarity.COMMON, EquipmentQuality.UNSPECIFIED,
                List.of(), List.of(EquipmentModSlot.empty(0)), Optional.empty(),
                31, false, BindingPolicy.UNBOUND, TradePolicy.DENY_ALL, Optional.empty()));
    }

    private static void modDefinitionAndIsolation() {
        ModDefinition definition = definition();
        assert definition.requiredTags().equals(Set.of(AttackTag.MELEE));
        assert definition.acceptsAttackTags(Set.of(AttackTag.MELEE));
        assert !definition.acceptsAttackTags(Set.of(AttackTag.MELEE, AttackTag.PHYSICAL));
        assert !definition.acceptsAttackTags(Set.of(AttackTag.MELEE, AttackTag.MAGIC));
        assertThrows(UnsupportedOperationException.class,
                () -> definition.allowedSlots().add(EquipmentSlot.HEAD));
        ModEntry entry = new ModEntry(SchemaVersions.MOD_DEFINITION,
                "projects:keen-edge", ModRank.RANK_1, 2.5, 7,
                source(), 0);
        ModValidation valid = ModValidation.validate(entry, definition, EquipmentSlot.WEAPON);
        assert valid.valid() && valid.contribution().orElseThrow().value() == 2.5;
        assert !ModValidation.validate(entry, definition, EquipmentSlot.HEAD).valid();
        assert !ModValidation.validate(entry, null, EquipmentSlot.WEAPON).valid();

        byte[] opaque = {1, 2, 3};
        UnknownModEntry unknown = new UnknownModEntry(
                0, "mod-definition", 99, "future:unrecognized", opaque);
        opaque[0] = 9;
        assert unknown.payload()[0] == 1;
        assert !unknown.effectEnabled();
        assert unknown.schemaVersion() == 99;
        assert unknown.modId().equals("future:unrecognized");
        assert ModValidation.validate(unknown, definition, EquipmentSlot.WEAPON)
                .contribution().isEmpty();
        assertThrows(IllegalArgumentException.class, () -> new UnknownModEntry(
                0, 2, new byte[UnknownModEntry.MAXIMUM_PAYLOAD_BYTES + 1]));
        assertThrows(IllegalArgumentException.class, () -> new ModEntry(
                1, "projects:keen-edge", ModRank.RANK_1, Double.NaN, 7, source(), 0));
        assertThrows(IllegalArgumentException.class, () -> new ModDefinition(
                1, "projects:bad", ModRank.RANK_1, Set.of(EquipmentSlot.WEAPON),
                Set.of(AttackTag.MELEE), Set.of(AttackTag.MELEE),
                ModTagMatchPolicy.EXACT,
                "projects:physical-attack", 1, 2, ModStackingLayer.BASE_FLAT,
                source(), display(), 0));
    }

    private static void flagsRemainDisabled() {
        FeatureFlagService flags = new FeatureFlagService();
        assert !flags.isEnabled(FeatureKey.EQUIPMENT_V2);
        assert !flags.isEnabled(FeatureKey.MOD_SYSTEM);
    }

    private static EquipmentItemV1 item(EquipmentRarity rarity,
                                        List<BaseStatRoll> rolls,
                                        List<EquipmentModSlot> slots,
                                        Optional<UUID> instanceId) {
        return new EquipmentItemV1(SchemaVersions.EQUIPMENT_ITEM, "starter_sword",
                EquipmentCategory.WEAPON, EquipmentSlot.WEAPON, EquipmentTier.T1, 1,
                rarity, EquipmentQuality.UNSPECIFIED, rolls, slots, Optional.empty(),
                0, false, BindingPolicy.UNBOUND, TradePolicy.DENY_ALL, instanceId);
    }
    private static ModSource source() { return new ModSource("projects:core", "projects:test"); }
    private static ModDisplayMetadata display() { return new ModDisplayMetadata("mod.projects.keen_edge", "{value}"); }
    private static ModDefinition definition() {
        return new ModDefinition(1, "projects:keen-edge", ModRank.RANK_1,
                Set.of(EquipmentSlot.WEAPON), Set.of(AttackTag.MELEE), Set.of(AttackTag.MAGIC),
                ModTagMatchPolicy.EXACT,
                "projects:physical-attack", 1, 3, ModStackingLayer.BASE_FLAT,
                source(), display(), 7);
    }
    private static void assertThrows(Class<? extends Throwable> expected, Runnable action) {
        try { action.run(); } catch (Throwable value) {
            assert expected.isInstance(value) : value;
            return;
        }
        throw new AssertionError("Expected " + expected.getSimpleName());
    }
}
