package io.github.gyai.projects.combat.classsystem;

import io.github.gyai.projects.manager.ItemManager;
import io.github.gyai.projects.network.SkillInputType;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClassManager {
    private final ItemManager itemManager;
    private final ClassRegistry registry;
    private final Map<UUID, String> activeClasses = new HashMap<>();

    public ClassManager(ItemManager itemManager, ClassRegistry registry) {
        this.itemManager = itemManager;
        this.registry = registry;
    }

    public ClassRegistry.RegisteredClass update(Player player) {
        String weaponId = itemManager.getItemId(player.getInventory().getItemInMainHand());
        ClassRegistry.RegisteredClass current = registry.getByWeapon(weaponId);
        String previousId = activeClasses.get(player.getUniqueId());
        String currentId = current == null ? null : current.definition().id();
        if (!java.util.Objects.equals(previousId, currentId)) {
            if (previousId != null) {
                registry.getAll().stream().filter(entry -> entry.definition().id().equals(previousId))
                        .findFirst().ifPresent(entry -> entry.controller().reset(player));
            }
            if (currentId == null) activeClasses.remove(player.getUniqueId());
            else activeClasses.put(player.getUniqueId(), currentId);
        }
        return current;
    }

    public boolean handle(Player player, SkillInputType input) {
        ClassRegistry.RegisteredClass active = update(player);
        if (active == null) return false;
        SkillSlot slot = SkillSlot.fromInput(input);
        if (slot != null) active.controller().handle(player, slot);
        return true;
    }

    public ClassRegistry.RegisteredClass getActive(Player player) { return update(player); }

    public void removePlayer(Player player) {
        ClassRegistry.RegisteredClass active = getActive(player);
        if (active != null) active.controller().reset(player);
        activeClasses.remove(player.getUniqueId());
    }
}
