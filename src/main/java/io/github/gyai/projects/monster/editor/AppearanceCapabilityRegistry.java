package io.github.gyai.projects.monster.editor;

import java.util.Map;
import java.util.Set;

public final class AppearanceCapabilityRegistry {
    private static final Set<String> BABY_TYPES = Set.of(
            "ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER",
            "PIGLIN", "PIGLIN_BRUTE", "ZOGLIN",
            "SHEEP", "WOLF", "CAT", "HORSE", "VILLAGER");
    private static final Map<String, Set<String>> VARIANTS = Map.of(
            "SLIME", Set.of("size"),
            "SHEEP", Set.of("color", "sheared"),
            "WOLF", Set.of("variant", "collar-color", "angry"),
            "CAT", Set.of("variant", "collar-color"),
            "HORSE", Set.of("color"),
            "VILLAGER", Set.of("profession", "villager-type"));

    private AppearanceCapabilityRegistry() {
    }

    public static boolean supportsBaby(String entityType) {
        return BABY_TYPES.contains(normalize(entityType));
    }

    public static Set<String> supportedVariants(String entityType) {
        return VARIANTS.getOrDefault(normalize(entityType), Set.of());
    }

    public static boolean supportsEquipment(String entityType) {
        String type = normalize(entityType);
        return type.contains("ZOMBIE") || type.contains("SKELETON")
                || Set.of("HUSK", "DROWNED", "PIGLIN", "PIGLIN_BRUTE",
                "ZOMBIFIED_PIGLIN", "STRAY", "BOGGED", "VILLAGER",
                "VINDICATOR", "PILLAGER", "WITCH")
                .contains(type);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
