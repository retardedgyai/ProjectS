package io.github.gyai.projects.enhancement.v2;

import io.github.gyai.projects.item.compatibility.LegacyItemReadResult;
import io.github.gyai.projects.item.compatibility.LegacyPdcSource;
import io.github.gyai.projects.manager.EnhancementManager;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
            var presentation = characterization.describe(level, false, 3.5, -0.2);
            assert presentation.displayPrefix().equals(
                    level == 0 ? "" : "§6[+" + level + "] ");
            assert presentation.loreFacts().getFirst().equals("強化値: +" + level + " / +30");
            assert characterization.materialCost(level) == Math.max(1, (level + 4) / 5);
            assert characterization.repairCost(level) == Math.max(1, (level + 4) / 5);
        }
        assert characterization.successChancePercent(5) == 100.0;
        assert characterization.successChancePercent(30) == 1.0;
        assert characterization.breakChancePercent(14) == 0.0;
        assert characterization.breakChancePercent(30) == 50.0;
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
        var normal = characterization.describe(10, false, 2.0, 0.0);
        assert normal.loreFacts().contains("攻撃倍率: 1.4");
        assert normal.loreFacts().contains("強化攻撃速度: 0.08");
        assert normal.attackSpeedAttributePresent();
        assert !normal.zeroAttackPowerWhileBroken();
        assert normal.failureBehavior()
                == LegacyEnhancementCharacterization.FailureBehavior.NO_CHANGE_OR_BROKEN;
        assert normal.flagDisabledBehavior()
                == LegacyEnhancementCharacterization.FlagDisabledBehavior.LEGACY_MANAGER_AND_LISTENER;

        var broken = characterization.describe(10, true, 2.0, 0.0);
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
}
