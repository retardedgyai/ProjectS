package io.github.gyai.projects.dev;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class DevMenuHolder implements InventoryHolder {
    private final DevMenuManager.Page page;
    private final int pageNumber;
    private final Map<Integer, DevMenuManager.MenuAction> actions = new HashMap<>();
    private Inventory inventory;

    DevMenuHolder(DevMenuManager.Page page, int pageNumber) {
        this.page = page;
        this.pageNumber = pageNumber;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    void action(int slot, DevMenuManager.MenuAction action) {
        actions.put(slot, action);
    }

    DevMenuManager.MenuAction action(int slot) {
        return actions.get(slot);
    }

    DevMenuManager.Page page() {
        return page;
    }

    int pageNumber() {
        return pageNumber;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
