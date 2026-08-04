package io.github.gyai.projects.item.compatibility;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LegacyItemCompatibilityTest {
    private LegacyItemCompatibilityTest() { }
    public static void main(String[] args) {
        readOnlyPdcFixture();
        malformedValuesAreIsolated();
        wrongKnownPdcTypesAreIsolated();
        missingItemIdentityIsNotMalformed();
        inventoryRoundTripDoesNotCreateIdentity();
        bukkitItemStackReadDoesNotMutateBytesOrPdc();
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
        assert result.status() == LegacyItemReadResult.Status.MALFORMED;
        assert result.issues().containsAll(List.of(
                "itemId", "enhancementLevel", "attackPowerBonus", "attackSpeedBonus"));
    }

    private static void wrongKnownPdcTypesAreIsolated() {
        Fixture wrongTypes = new Fixture("minecraft:iron_sword");
        wrongTypes.values.put("item_id", 17);
        wrongTypes.values.put("enhancement_level", "9");
        wrongTypes.values.put("weapon_broken", true);
        wrongTypes.values.put("weapon_attack_power_bonus", "3.0");
        wrongTypes.values.put("weapon_attack_speed_bonus", 2);
        LegacyItemReadResult result = new LegacyItemCompatibilityReader().read(wrongTypes);
        assert result.status() == LegacyItemReadResult.Status.MALFORMED;
        assert result.view().isEmpty();
        assert result.issues().containsAll(List.of(
                "itemIdType", "enhancementLevelType", "brokenType",
                "attackPowerBonusType", "attackSpeedBonusType"));
    }

    private static void missingItemIdentityIsNotMalformed() {
        LegacyItemReadResult result = new LegacyItemCompatibilityReader().read(
                new Fixture("minecraft:stone"));
        assert result.status() == LegacyItemReadResult.Status.MISSING;
        assert result.view().isEmpty() && result.issues().isEmpty();
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

    private static void bukkitItemStackReadDoesNotMutateBytesOrPdc() {
        LinkedHashMap<String, Object> pdc = new LinkedHashMap<>();
        pdc.put("projects:item_id", "starter_sword");
        pdc.put("projects:enhancement_level", 9);
        pdc.put("projects:weapon_broken", (byte) 1);
        pdc.put("projects:weapon_attack_power_bonus", 3.0d);
        pdc.put("projects:weapon_attack_speed_bonus", 0.2d);
        FakeItemStack item = new FakeItemStack(pdc);
        byte[] before = item.serializeAsBytes();
        Map<String, Object> pdcBefore = Map.copyOf(pdc);
        LegacyPdcSource source = new BukkitLegacyPdcSource(
                item, "projects", ignored -> "minecraft:iron_sword");
        LegacyItemReadResult result = new LegacyItemCompatibilityReader().read(source);
        assert result.valid();
        assert result.view().orElseThrow().enhancementLevel().orElseThrow() == 9;
        assert Arrays.equals(before, item.serializeAsBytes());
        assert pdc.equals(pdcBefore) : "Bukkit PDC changed during read";
    }

    private static final class Fixture implements LegacyPdcSource {
        private final String material;
        private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        private Fixture(String material) { this.material = material; }
        @Override public String materialIdentity() { return material; }
        @Override public boolean contains(String key) { return values.containsKey(key); }
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

    private static final class FakeItemStack extends ItemStack {
        private final LinkedHashMap<String, Object> values;
        private final ItemMeta meta;
        private FakeItemStack(LinkedHashMap<String, Object> values) {
            super();
            this.values = values;
            PersistentDataContainer data = (PersistentDataContainer) Proxy.newProxyInstance(
                    PersistentDataContainer.class.getClassLoader(),
                    new Class<?>[]{PersistentDataContainer.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("get") && args != null && args.length == 2) {
                            return values.get(((NamespacedKey) args[0]).toString());
                        }
                        if (method.getName().equals("has") && args != null && args.length == 1) {
                            return values.containsKey(((NamespacedKey) args[0]).toString());
                        }
                        if (method.getName().equals("toString")) return values.toString();
                        return primitiveDefault(method.getReturnType());
                    });
            meta = (ItemMeta) Proxy.newProxyInstance(
                    ItemMeta.class.getClassLoader(), new Class<?>[]{ItemMeta.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getPersistentDataContainer")) return data;
                        if (method.getName().equals("clone")) return proxy;
                        if (method.getName().equals("toString")) return values.toString();
                        return primitiveDefault(method.getReturnType());
                    });
        }
        @Override public ItemMeta getItemMeta() { return meta; }
        @Override public byte[] serializeAsBytes() {
            StringBuilder result = new StringBuilder();
            values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                    result.append(entry.getKey()).append('=').append(entry.getValue()).append('\n'));
            return result.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
