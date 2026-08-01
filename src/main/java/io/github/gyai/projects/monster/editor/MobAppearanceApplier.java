package io.github.gyai.projects.monster.editor;

import io.github.gyai.projects.manager.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class MobAppearanceApplier {
    private static final Pattern TEXTURE_URL = Pattern.compile(
            "\\\"url\\\"\\s*:\\s*\\\"(https://textures\\.minecraft\\.net/texture/[A-Za-z0-9_-]{1,256})\\\"");
    private final ItemManager itemManager;
    private final HeadDefinitionRepository headRepository;

    public MobAppearanceApplier(
            ItemManager itemManager,
            HeadDefinitionRepository headRepository
    ) {
        this.itemManager = itemManager;
        this.headRepository = headRepository;
    }

    public void apply(LivingEntity entity, MobDefinition definition) {
        MobAppearanceDefinition appearance = definition.appearance();
        entity.setGlowing(appearance.glowing());
        entity.customName(net.kyori.adventure.text.Component.text(
                definition.displayName()));
        entity.setCustomNameVisible(
                definition.nameplateMode() == MobDefinition.NameplateMode.ALWAYS);
        setAttribute(entity, Attribute.SCALE, appearance.scale());
        if (entity instanceof Ageable ageable) {
            if (appearance.age() == MobAppearanceDefinition.Age.BABY) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }
        }
        applyVariants(entity, appearance);
        applyEquipment(entity, appearance);
    }

    private void applyVariants(
            LivingEntity entity,
            MobAppearanceDefinition appearance
    ) {
        var variants = appearance.variants();
        if (entity instanceof Slime slime && variants.containsKey("size")) {
            slime.setSize(parseInt(variants.get("size"), 1, 127, 1));
        }
        if (entity instanceof Sheep sheep) {
            color(variants.get("color"), sheep::setColor);
            if (variants.containsKey("sheared")) {
                sheep.setSheared(Boolean.parseBoolean(variants.get("sheared")));
            }
        }
        if (entity instanceof Wolf wolf) {
            color(variants.get("collar-color"), wolf::setCollarColor);
            if (variants.containsKey("angry")) {
                wolf.setAngry(Boolean.parseBoolean(variants.get("angry")));
            }
            registryValue(RegistryAccess.registryAccess().getRegistry(
                            RegistryKey.WOLF_VARIANT),
                    variants.get("variant"), wolf::setVariant);
        }
        if (entity instanceof Cat cat) {
            color(variants.get("collar-color"), cat::setCollarColor);
            registryValue(RegistryAccess.registryAccess().getRegistry(
                            RegistryKey.CAT_VARIANT),
                    variants.get("variant"), cat::setCatType);
        }
        if (entity instanceof Horse horse) {
            enumValue(Horse.Color.class, variants.get("color"), horse::setColor);
        }
        if (entity instanceof Villager villager) {
            registryValue(RegistryAccess.registryAccess().getRegistry(
                            RegistryKey.VILLAGER_PROFESSION),
                    variants.get("profession"), villager::setProfession);
            registryValue(RegistryAccess.registryAccess().getRegistry(
                            RegistryKey.VILLAGER_TYPE),
                    variants.get("villager-type"), villager::setVillagerType);
        }
    }

    private void applyEquipment(
            LivingEntity entity,
            MobAppearanceDefinition appearance
    ) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return;
        for (MobAppearanceDefinition.Slot slot : MobAppearanceDefinition.Slot.values()) {
            ItemStack item = createItem(appearance.equipment().get(slot));
            switch (slot) {
                case HEAD -> {
                    equipment.setHelmet(item, true);
                    equipment.setHelmetDropChance(0);
                }
                case CHEST -> {
                    equipment.setChestplate(item, true);
                    equipment.setChestplateDropChance(0);
                }
                case LEGS -> {
                    equipment.setLeggings(item, true);
                    equipment.setLeggingsDropChance(0);
                }
                case FEET -> {
                    equipment.setBoots(item, true);
                    equipment.setBootsDropChance(0);
                }
                case MAIN_HAND -> {
                    equipment.setItemInMainHand(item, true);
                    equipment.setItemInMainHandDropChance(0);
                }
                case OFF_HAND -> {
                    equipment.setItemInOffHand(item, true);
                    equipment.setItemInOffHandDropChance(0);
                }
            }
        }
    }

    private ItemStack createItem(MobEquipmentEntry entry) {
        if (entry == null || !entry.visible()
                || entry.sourceType() == MobEquipmentEntry.SourceType.NONE) {
            return new ItemStack(Material.AIR);
        }
        ItemStack result = switch (entry.sourceType()) {
            case NONE -> new ItemStack(Material.AIR);
            case VANILLA_ITEM -> new ItemStack(Material.valueOf(
                    entry.material().toUpperCase(Locale.ROOT)));
            case PROJECTS_ITEM -> {
                ItemStack item = itemManager.createItem(entry.referenceId());
                yield item == null ? new ItemStack(Material.AIR) : item;
            }
            case CUSTOM_HEAD -> createHead(entry.referenceId());
        };
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            if (meta instanceof LeatherArmorMeta leather
                    && !entry.color().isBlank()) {
                leather.setColor(Color.fromRGB(
                        Integer.parseInt(entry.color().substring(1), 16)));
            }
            meta.setEnchantmentGlintOverride(entry.glint());
            meta.setAttributeModifiers(com.google.common.collect.ImmutableMultimap.of());
            result.setItemMeta(meta);
        }
        return result;
    }

    private ItemStack createHead(String id) {
        HeadDefinition definition = headRepository.get(id);
        if (definition == null) return new ItemStack(Material.AIR);
        if (definition.sourceType() == HeadDefinition.SourceType.PROJECTS_ITEM) {
            ItemStack item = itemManager.createItem(definition.projectsItemId());
            return item == null ? new ItemStack(Material.AIR) : item;
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (definition.sourceType() == HeadDefinition.SourceType.TEXTURE_VALUE
                && head.getItemMeta() instanceof SkullMeta skull) {
            try {
                var profile = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(
                        definition.id().getBytes(StandardCharsets.UTF_8)));
                String json = new String(Base64.getDecoder().decode(
                        definition.textureValue()), StandardCharsets.UTF_8);
                var matcher = TEXTURE_URL.matcher(json);
                if (matcher.find()) {
                    var textures = profile.getTextures();
                    textures.setSkin(URI.create(matcher.group(1)).toURL());
                    profile.setTextures(textures);
                    skull.setOwnerProfile(profile);
                    head.setItemMeta(skull);
                } else return new ItemStack(Material.AIR);
            } catch (java.net.MalformedURLException | IllegalArgumentException ignored) {
                return new ItemStack(Material.AIR);
            }
        }
        return head;
    }

    private static void setAttribute(
            LivingEntity entity,
            Attribute attribute,
            double value
    ) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private static void color(String value, java.util.function.Consumer<DyeColor> setter) {
        enumValue(DyeColor.class, value, setter);
    }

    private static <T extends Enum<T>> void enumValue(
            Class<T> type,
            String value,
            java.util.function.Consumer<T> setter
    ) {
        if (value == null || value.isBlank()) return;
        try {
            setter.accept(Enum.valueOf(type, value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            // Validator rejects unsupported values before this method is called.
        }
    }

    private static <T extends org.bukkit.Keyed> void registryValue(
            Registry<T> registry,
            String value,
            java.util.function.Consumer<T> setter
    ) {
        if (value == null || value.isBlank()) return;
        T selected = registry.get(NamespacedKey.minecraft(
                value.toLowerCase(Locale.ROOT)));
        if (selected != null) setter.accept(selected);
    }

    private static int parseInt(String value, int minimum, int maximum, int fallback) {
        try {
            return Math.clamp(Integer.parseInt(value), minimum, maximum);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
