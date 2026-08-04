package io.github.gyai.projects.item.compatibility;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LegacyItemCompatibilityTest {
    private LegacyItemCompatibilityTest() { }
    public static void main(String[] args) {
        readOnlyPdcFixture();
        malformedValuesAreIsolated();
        inventoryRoundTripDoesNotCreateIdentity();
    }

    private static void readOnlyPdcFixture() {
        Fixture source = new Fixture("minecraft:iron_sword");
        source.values.put(LegacyItemCompatibilityReader.ITEM_ID_KEY, "starter_sword");
        source.values.put(LegacyItemCompatibilityReader.ENHANCEMENT_LEVEL_KEY, 17);
        source.values.put(LegacyItemCompatibilityReader.BROKEN_KEY, (byte) 1);
        source.values.put(LegacyItemCompatibilityReader.ATTACK_POWER_BONUS_KEY, 4.25d);
        source.values.put(LegacyItemCompatibilityReader.ATTACK_SPEED_BONUS_KEY, -0.10d);
        byte[] before = source.serializedPdc();
        LegacyItemReadResult result = new LegacyItemCompatibilityReader().read(source);
        byte[] after = source.serializedPdc();
        assert result.valid();
        LegacyItemView view = result.view().orElseThrow();
        assert view.itemId().equals("starter_sword");
        assert view.enhancementLevel().orElseThrow() == 17;
        assert view.broken();
        assert view.attackPowerBonus().orElseThrow() == 4.25;
        assert view.attackSpeedBonus().orElseThrow() == -0.10;
        assert view.instanceId().isEmpty();
        assert Arrays.equals(before, after) : "read mutated legacy PDC bytes";
        assert source.values.keySet().equals(Set.of("item_id", "enhancement_level",
                "weapon_broken", "weapon_attack_power_bonus", "weapon_attack_speed_bonus"));
    }

    private static void malformedValuesAreIsolated() {
        Fixture malformed = new Fixture("minecraft:iron_sword");
        malformed.values.put("item_id", "x".repeat(129));
        malformed.values.put("enhancement_level", 31);
        malformed.values.put("weapon_attack_power_bonus", Double.NaN);
        malformed.values.put("weapon_attack_speed_bonus", Double.POSITIVE_INFINITY);
        LegacyItemReadResult result = new LegacyItemCompatibilityReader().read(malformed);
        assert !result.valid() && result.view().isEmpty();
        assert result.issues().containsAll(List.of(
                "itemId", "enhancementLevel", "attackPowerBonus", "attackSpeedBonus"));
    }

    private static void inventoryRoundTripDoesNotCreateIdentity() {
        ArrayList<Fixture> inventory = new ArrayList<>();
        for (String itemId : List.of("starter_sword", "starter_bow", "enhancement_stone")) {
            Fixture fixture = new Fixture("minecraft:stone");
            fixture.values.put("item_id", itemId);
            inventory.add(fixture);
        }
        List<byte[]> before = inventory.stream().map(Fixture::serializedPdc).toList();
        List<LegacyItemView> views = inventory.stream()
                .map(new LegacyItemCompatibilityReader()::read)
                .map(value -> value.view().orElseThrow()).toList();
        for (int index = 0; index < inventory.size(); index++) {
            assert Arrays.equals(before.get(index), inventory.get(index).serializedPdc());
            assert views.get(index).instanceId().isEmpty();
        }
    }

    private static final class Fixture implements LegacyPdcSource {
        private final String material;
        private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        private Fixture(String material) { this.material = material; }
        @Override public String materialIdentity() { return material; }
        @Override public Optional<String> stringValue(String key) { return typed(key, String.class); }
        @Override public Optional<Integer> integerValue(String key) { return typed(key, Integer.class); }
        @Override public Optional<Byte> byteValue(String key) { return typed(key, Byte.class); }
        @Override public Optional<Double> doubleValue(String key) { return typed(key, Double.class); }
        private <T> Optional<T> typed(String key, Class<T> type) {
            Object value = values.get(key);
            return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
        }
        private byte[] serializedPdc() {
            StringBuilder result = new StringBuilder(material).append('\n');
            values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> result.append(entry.getKey()).append(':')
                            .append(entry.getValue().getClass().getName()).append(':')
                            .append(entry.getValue()).append('\n'));
            return result.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

}
