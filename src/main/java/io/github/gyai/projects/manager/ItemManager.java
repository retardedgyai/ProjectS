package io.github.gyai.projects.manager;

import io.github.gyai.projects.item.CustomItem;
import io.github.gyai.projects.item.Weapon;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class ItemManager {

    private static final Map<String, CustomItem> ITEMS = new HashMap<>();

    public static void initialize() {
        ITEMS.clear();

        register(new Weapon(
                "starter_sword",
                "§bProjectSの剣",
                Material.IRON_SWORD,
                10
        ));
    }

    public static void register(CustomItem item) {
        ITEMS.put(item.getId(), item);
    }

    public static CustomItem getItem(String id) {
        return ITEMS.get(id);
    }

    public static ItemStack createItem(String id) {
        CustomItem item = getItem(id);

        if (item == null) {
            return null;
        }

        return item.createItem();
    }
}