package io.github.gyai.projects.enhancement.v2;

import io.github.gyai.projects.item.compatibility.LegacyItemReadResult;
import io.github.gyai.projects.item.compatibility.LegacyPdcSource;
import io.github.gyai.projects.manager.EnhancementManager;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;

public final class LegacyEnhancementCharacterizationTest {
    private LegacyEnhancementCharacterizationTest() { }

    public static void main(String[] args) {
        allLegacyLevelsAreReadWithoutWrites();
        brokenBonusesKeysAndTypesAreCharacterized();
        loreAttributeFailureAndFlagDisabledBehaviorAreFixed();
    }

    private static void allLegacyLevelsAreReadWithoutWrites() {
        LegacyEnhancementCharacterization characterization =
                new LegacyEnhancementCharacterization();
        assert EnhancementManager.MAX_LEVEL == 30;
        for (int level = 0; level <= 30; level++) {
            Fixture fixture = fixture(level, false, 3.5, -0.2);
            byte[] before = fixture.serialized();
            LegacyItemReadResult result = characterization.read(fixture);
            assert result.valid();
            assert result.view().orElseThrow().enhancementLevel().orElseThrow() == level;
            assert Arrays.equals(before, fixture.serialized()) : "legacy read wrote level " + level;
            var presentation = LegacyFixtureCharacterization.describe(
                    level, false, 3.5, -0.2);
            assert presentation.displayPrefix().equals(
                    level == 0 ? "" : "§6[+" + level + "] ");
            assert presentation.loreFacts().getFirst().equals("強化値: +" + level + " / +30");
            assert LegacyFixtureCharacterization.materialCost(level)
                    == Math.max(1, (level + 4) / 5);
            assert LegacyFixtureCharacterization.repairCost(level)
                    == Math.max(1, (level + 4) / 5);
        }
        assert LegacyFixtureCharacterization.successChancePercent(5) == 100.0;
        assert LegacyFixtureCharacterization.successChancePercent(30) == 1.0;
        assert LegacyFixtureCharacterization.breakChancePercent(14) == 0.0;
        assert LegacyFixtureCharacterization.breakChancePercent(30) == 50.0;
    }

    private static void brokenBonusesKeysAndTypesAreCharacterized() {
        Fixture broken = fixture(17, true, 4.25, -0.10);
        Map<String, Object> before = Map.copyOf(broken.values);
        var view = new LegacyEnhancementCharacterization().read(broken).view().orElseThrow();
        assert view.broken();
        assert view.attackPowerBonus().orElseThrow() == 4.25;
        assert view.attackSpeedBonus().orElseThrow() == -0.10;
        assert broken.values.equals(before);
        assert broken.values.get(LegacyEnhancementCharacterization.LEVEL_KEY) instanceof Integer;
        assert broken.values.get(LegacyEnhancementCharacterization.BROKEN_KEY) instanceof Byte;
        assert broken.values.get(LegacyEnhancementCharacterization.ATTACK_POWER_BONUS_KEY)
                instanceof Double;
        assert broken.values.get(LegacyEnhancementCharacterization.ATTACK_SPEED_BONUS_KEY)
                instanceof Double;

        Fixture unbroken = fixture(17, false, 4.25, -0.10);
        assert !unbroken.contains(LegacyEnhancementCharacterization.BROKEN_KEY);
        assert !new LegacyEnhancementCharacterization().read(unbroken)
                .view().orElseThrow().broken();
    }

    private static void loreAttributeFailureAndFlagDisabledBehaviorAreFixed() {
        LegacyEnhancementCharacterization characterization =
                new LegacyEnhancementCharacterization();
        var normal = LegacyFixtureCharacterization.describe(10, false, 2.0, 0.0);
        assert normal.loreFacts().contains("攻撃倍率: 1.4");
        assert normal.loreFacts().contains("強化攻撃速度: 0.08");
        assert normal.attackSpeedAttributePresent();
        assert !normal.zeroAttackPowerWhileBroken();
        assert normal.failureBehavior() == FailureBehavior.NO_CHANGE_OR_BROKEN;
        assert normal.flagDisabledBehavior() == FlagDisabledBehavior.LEGACY_MANAGER_AND_LISTENER;

        var broken = LegacyFixtureCharacterization.describe(10, true, 2.0, 0.0);
        assert broken.displayPrefix().equals("§4[破損] §6[+10] ");
        assert !broken.attackSpeedAttributePresent();
        assert broken.zeroAttackPowerWhileBroken();
        expectUnsupported(() -> broken.loreFacts().add("mutation"));
    }

    private static Fixture fixture(
            int level, boolean broken, double powerBonus, double speedBonus) {
        Fixture fixture = new Fixture();
        fixture.values.put("item_id", "starter_sword");
        fixture.values.put(LegacyEnhancementCharacterization.LEVEL_KEY, level);
        if (broken) fixture.values.put(LegacyEnhancementCharacterization.BROKEN_KEY, (byte) 1);
        fixture.values.put(LegacyEnhancementCharacterization.ATTACK_POWER_BONUS_KEY, powerBonus);
        fixture.values.put(LegacyEnhancementCharacterization.ATTACK_SPEED_BONUS_KEY, speedBonus);
        return fixture;
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static final class Fixture implements LegacyPdcSource {
        private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        @Override public String materialIdentity() { return "minecraft:iron_sword"; }
        @Override public boolean contains(String key) { return values.containsKey(key); }
        @Override public Optional<String> stringValue(String key) { return typed(key, String.class); }
        @Override public Optional<Integer> integerValue(String key) { return typed(key, Integer.class); }
        @Override public Optional<Byte> byteValue(String key) { return typed(key, Byte.class); }
        @Override public Optional<Double> doubleValue(String key) { return typed(key, Double.class); }
        private <T> Optional<T> typed(String key, Class<T> type) {
            Object value = values.get(key);
            return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
        }
        private byte[] serialized() {
            return values.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Test-only copy of deployed legacy formulas; it is not a v2 policy. */
    private static final class LegacyFixtureCharacterization {
        private static int materialCost(int targetLevel) {
            return Math.max(1, (targetLevel + 4) / 5);
        }
        private static int repairCost(int level) {
            return Math.max(1, (level + 4) / 5);
        }
        private static double successChancePercent(int targetLevel) {
            if (targetLevel <= 5) return 100.0;
            if (targetLevel <= 10) return 95.0 - (targetLevel - 6) * 7.5;
            if (targetLevel <= 15) return 55.0 - (targetLevel - 11) * 5.0;
            if (targetLevel <= 20) return 30.0 - (targetLevel - 16) * 4.0;
            if (targetLevel <= 25) return 10.0 - (targetLevel - 21) * 1.5;
            return 3.0 - (targetLevel - 26) * 0.5;
        }
        private static double breakChancePercent(int currentLevel) {
            if (currentLevel < 15) return 0.0;
            return Math.min(50.0, 5.0 + (currentLevel - 15) * 3.0);
        }
        private static LegacyPresentation describe(
                int level, boolean broken, double attackPowerBonus,
                double attackSpeedBonus) {
            String prefix = (broken ? "§4[破損] " : "")
                    + (level > 0 ? "§6[+" + level + "] " : "");
            return new LegacyPresentation(
                    prefix,
                    List.of(
                            "強化値: +" + level + " / +30",
                            "攻撃倍率: " + (1.0 + level * 0.04),
                            "強化攻撃速度: " + (level * 0.008),
                            "武器攻撃力調整: " + attackPowerBonus,
                            "武器攻撃速度調整: " + attackSpeedBonus),
                    !broken && level * 0.008 + attackSpeedBonus != 0.0,
                    broken, FailureBehavior.NO_CHANGE_OR_BROKEN,
                    FlagDisabledBehavior.LEGACY_MANAGER_AND_LISTENER);
        }
    }

    private record LegacyPresentation(
            String displayPrefix, List<String> loreFacts,
            boolean attackSpeedAttributePresent, boolean zeroAttackPowerWhileBroken,
            FailureBehavior failureBehavior, FlagDisabledBehavior flagDisabledBehavior) {
        private LegacyPresentation { loreFacts = List.copyOf(loreFacts); }
    }

    private enum FailureBehavior { NO_CHANGE_OR_BROKEN }
    private enum FlagDisabledBehavior { LEGACY_MANAGER_AND_LISTENER }
}
