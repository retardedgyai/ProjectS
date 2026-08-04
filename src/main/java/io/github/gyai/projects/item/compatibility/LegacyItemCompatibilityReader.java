package io.github.gyai.projects.item.compatibility;

import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class LegacyItemCompatibilityReader {
    public static final String ITEM_ID_KEY = "item_id";
    public static final String ENHANCEMENT_LEVEL_KEY = "enhancement_level";
    public static final String BROKEN_KEY = "weapon_broken";
    public static final String ATTACK_POWER_BONUS_KEY = "weapon_attack_power_bonus";
    public static final String ATTACK_SPEED_BONUS_KEY = "weapon_attack_speed_bonus";

    public LegacyItemReadResult read(LegacyPdcSource source) {
        if (source == null) return malformed(java.util.List.of("source"));
        ArrayList<String> issues = new ArrayList<>();
        if (!source.contains(ITEM_ID_KEY)) {
            return new LegacyItemReadResult(
                    Optional.empty(), java.util.List.of(),
                    LegacyItemReadResult.Status.MISSING);
        }
        String itemId = source.stringValue(ITEM_ID_KEY).orElse(null);
        if (itemId == null) issues.add("itemIdType");
        else if (itemId.isBlank() || itemId.length() > 128) issues.add("itemId");
        Optional<Integer> rawLevel = source.integerValue(ENHANCEMENT_LEVEL_KEY);
        if (source.contains(ENHANCEMENT_LEVEL_KEY) && rawLevel.isEmpty()) {
            issues.add("enhancementLevelType");
        }
        if (rawLevel.isPresent() && (rawLevel.get() < 0 || rawLevel.get() > 30)) issues.add("enhancementLevel");
        Optional<Byte> broken = source.byteValue(BROKEN_KEY);
        if (source.contains(BROKEN_KEY) && broken.isEmpty()) issues.add("brokenType");
        Optional<Double> power = source.doubleValue(ATTACK_POWER_BONUS_KEY);
        Optional<Double> speed = source.doubleValue(ATTACK_SPEED_BONUS_KEY);
        if (source.contains(ATTACK_POWER_BONUS_KEY) && power.isEmpty()) issues.add("attackPowerBonusType");
        if (source.contains(ATTACK_SPEED_BONUS_KEY) && speed.isEmpty()) issues.add("attackSpeedBonusType");
        if (power.isPresent() && !Double.isFinite(power.get())) issues.add("attackPowerBonus");
        if (speed.isPresent() && !Double.isFinite(speed.get())) issues.add("attackSpeedBonus");
        String material = source.materialIdentity();
        if (material == null || material.isBlank() || material.length() > 128) issues.add("materialIdentity");
        if (!issues.isEmpty()) return malformed(issues);
        LegacyItemView view = new LegacyItemView(
                itemId, material,
                rawLevel.isPresent() ? OptionalInt.of(rawLevel.get()) : OptionalInt.empty(),
                broken.isPresent(),
                power.isPresent() ? OptionalDouble.of(power.get()) : OptionalDouble.empty(),
                speed.isPresent() ? OptionalDouble.of(speed.get()) : OptionalDouble.empty());
        return new LegacyItemReadResult(
                Optional.of(view), java.util.List.of(),
                LegacyItemReadResult.Status.READABLE);
    }

    private static LegacyItemReadResult malformed(java.util.List<String> issues) {
        return new LegacyItemReadResult(
                Optional.empty(), issues, LegacyItemReadResult.Status.MALFORMED);
    }
}
